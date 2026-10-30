package com.hmdp.agent.memory;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的多轮会话记忆。
 * 只保存 user / assistant 文本，工具调用过程的中间消息不落库，避免 tool_call_id 对不上。
 */
@Component
@RequiredArgsConstructor
public class RedisChatMemory {

    private static final String KEY_PREFIX = "agent:memory:";

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${hmdp.agent.memory-ttl-minutes:120}")
    private long ttlMinutes;

    @Value("${hmdp.agent.max-history-messages:20}")
    private int maxHistoryMessages;

    public List<Message> load(String sessionId) {
        String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
        List<Message> messages = new ArrayList<>();
        if (StrUtil.isBlank(json)) {
            return messages;
        }
        for (Object item : JSONUtil.parseArray(json)) {
            JSONObject object = (JSONObject) item;
            String role = object.getStr("role");
            String content = object.getStr("content");
            if (StrUtil.isBlank(content)) {
                continue;
            }
            if ("user".equals(role)) {
                messages.add(new UserMessage(content));
            } else if ("assistant".equals(role)) {
                messages.add(new AssistantMessage(content));
            }
        }
        return messages;
    }

    public void append(String sessionId, String userMessage, String assistantMessage) {
        List<Message> messages = load(sessionId);
        messages.add(new UserMessage(userMessage));
        if (StrUtil.isNotBlank(assistantMessage)) {
            messages.add(new AssistantMessage(assistantMessage));
        }
        messages = trim(messages, maxHistoryMessages);
        JSONArray array = new JSONArray();
        for (Message message : messages) {
            JSONObject object = new JSONObject();
            object.set("role", message.getMessageType().getValue());
            object.set("content", message.getText());
            array.add(object);
        }
        stringRedisTemplate.opsForValue()
                .set(KEY_PREFIX + sessionId, array.toString(), ttlMinutes, TimeUnit.MINUTES);
    }

    public void clear(String sessionId) {
        stringRedisTemplate.delete(KEY_PREFIX + sessionId);
    }

    /**
     * 只保留最近 N 条，N 至少为 2（保证一问一答成对保留）。
     * 抽成静态方法便于单测，不依赖 Redis。
     */
    public static List<Message> trim(List<Message> messages, int maxMessages) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        int limit = Math.max(2, maxMessages);
        if (messages.size() <= limit) {
            return new ArrayList<>(messages);
        }
        return new ArrayList<>(messages.subList(messages.size() - limit, messages.size()));
    }
}
