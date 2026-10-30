package com.hmdp.agent;

import cn.hutool.core.util.StrUtil;
import com.hmdp.agent.memory.RedisChatMemory;
import com.hmdp.agent.tool.SemanticTools;
import com.hmdp.agent.tool.ShopTools;
import com.hmdp.agent.trace.TokenUsageRecorder;
import com.hmdp.agent.trace.ToolTraceRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class AgentService {

    private static final String SYSTEM_PROMPT = """
            你是「智觅生活」App 里的智能助手，负责帮用户找店、看店、看优惠。

            工作规则：
            1. 只要问题涉及具体店铺、分类、位置、优惠券、探店笔记，必须先调用工具查询真实数据，禁止凭印象编造。
            2. 不确定分类 id 时，先调用 list_shop_types；用户说“附近”时，如果没给经纬度，就用分类查询并说明需要定位。
            3. 回答用中文，简洁分点，最多 5 条。每家店至少给出：店名、商圈或地址、人均（元）、评分（5 分制）。
            4. 工具没查到就如实说“没有找到”，并给出可执行的建议（换关键词、换分类），不要编造。
            5. 你不能下单、支付、点赞或修改任何数据。用户要下单时，提示他到店铺详情页操作。
            6. 金额一律用「元」，距离用「米」或「公里」。
            7. 一次提问最多查询 8 次工具，够用就作答，不要反复试探。
            """;

    private final ChatClient chatClient;
    private final ShopTools shopTools;
    private final SemanticTools semanticTools;
    private final RedisChatMemory memory;
    private final ToolTraceRecorder toolTraceRecorder;
    private final TokenUsageRecorder usageRecorder;

    public AgentService(ChatClient.Builder builder, ShopTools shopTools, SemanticTools semanticTools,
                        RedisChatMemory memory, ToolTraceRecorder toolTraceRecorder,
                        TokenUsageRecorder usageRecorder) {
        this.chatClient = builder.build();
        this.shopTools = shopTools;
        this.semanticTools = semanticTools;
        this.memory = memory;
        this.toolTraceRecorder = toolTraceRecorder;
        this.usageRecorder = usageRecorder;
    }

    /**
     * 流式对话：模型自己决定调用哪些工具，Spring AI 负责执行并回填结果
     */
    public Flux<String> stream(String sessionId, String userMessage) {
        return stream(sessionId, userMessage, null);
    }

    /**
     * 流式对话（带工具事件与 token 用量回调）
     */
    public Flux<String> stream(String sessionId, String userMessage, AgentStreamListener listener) {
        List<Message> history = memory.load(sessionId);
        StringBuilder answer = new StringBuilder();
        AtomicReference<Usage> usageRef = new AtomicReference<>();
        long start = System.currentTimeMillis();
        toolTraceRecorder.beginTurn();

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(history)
                .user(userMessage)
                .tools(shopTools, semanticTools)
                .stream()
                .chatResponse()
                .doOnNext(response -> {
                    Usage usage = usageOf(response);
                    if (usage != null && usage.getPromptTokens() != null) {
                        usageRef.set(usage);
                    }
                })
                .map(this::textOf)
                .filter(StrUtil::isNotEmpty)
                .doOnNext(answer::append)
                .doOnComplete(() -> {
                    memory.append(sessionId, userMessage, answer.toString());
                    Usage usage = usageRef.get();
                    Integer promptTokens = usage == null ? null : usage.getPromptTokens();
                    Integer completionTokens = usage == null ? null : usage.getCompletionTokens();
                    usageRecorder.record(sessionId, promptTokens, completionTokens,
                            answer.length(), System.currentTimeMillis() - start);
                    if (listener != null) {
                        try {
                            listener.onUsage(promptTokens, completionTokens);
                            listener.onDone();
                        } catch (Exception ignored) {
                            // 回调异常不影响主流程
                        }
                    }
                    toolTraceRecorder.endTurn();
                })
                .doOnError(error -> {
                    toolTraceRecorder.endTurn();
                    log.error("Agent 流式调用失败", error);
                });
    }

    /**
     * 同步对话，便于 curl / Postman 调试，token 用量更准确
     */
    public String chat(String sessionId, String userMessage) {
        List<Message> history = memory.load(sessionId);
        long start = System.currentTimeMillis();
        toolTraceRecorder.beginTurn();
        try {
            ChatResponse response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .messages(history)
                    .user(userMessage)
                    .tools(shopTools, semanticTools)
                    .call()
                    .chatResponse();
            String answer = textOf(response);
            memory.append(sessionId, userMessage, answer);
            Usage usage = usageOf(response);
            usageRecorder.record(sessionId,
                    usage == null ? null : usage.getPromptTokens(),
                    usage == null ? null : usage.getCompletionTokens(),
                    answer.length(), System.currentTimeMillis() - start);
            return answer;
        } finally {
            toolTraceRecorder.endTurn();
        }
    }

    private String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return StrUtil.blankToDefault(response.getResult().getOutput().getText(), "");
    }

    private Usage usageOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        return response.getMetadata().getUsage();
    }
}
