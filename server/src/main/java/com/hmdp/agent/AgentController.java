package com.hmdp.agent;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.agent.dto.AgentEvent;
import com.hmdp.agent.dto.ChatRequest;
import com.hmdp.agent.memory.RedisChatMemory;
import com.hmdp.agent.tool.SemanticTools;
import com.hmdp.agent.trace.TokenUsageRecorder;
import com.hmdp.agent.trace.ToolTraceRecorder;
import com.hmdp.dto.Result;
import com.hmdp.embedding.SemanticSearchService;
import com.hmdp.embedding.ShopVectorIndexer;
import com.hmdp.index.ShopGeoIndexer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentController {

    private static final ScheduledExecutorService PROGRESS_POLLER = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "agent-progress-poller");
        thread.setDaemon(true);
        return thread;
    });

    private final AgentService agentService;
    private final RedisChatMemory memory;
    private final ToolTraceRecorder traceRecorder;
    private final TokenUsageRecorder usageRecorder;
    private final ShopVectorIndexer indexer;
    private final SemanticSearchService searchService;
    private final SemanticTools semanticTools;
    private final ShopGeoIndexer geoIndexer;

    /**
     * SSE 流式对话：type=session/delta/error/done
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        String sessionId = StrUtil.isBlank(request.getSessionId())
                ? IdUtil.fastSimpleUUID()
                : request.getSessionId();
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onError(e -> log.warn("SSE 连接异常: {}", e.getMessage()));

        send(emitter, AgentEvent.of("session", sessionId));

        if (StrUtil.isBlank(request.getMessage())) {
            send(emitter, AgentEvent.of("error", "问题不能为空"));
            emitter.complete();
            return emitter;
        }

        // 工具跑在 boundedElastic 线程，ThreadLocal 传不过去，这里轮询埋点表推送进度
        long turnStart = System.currentTimeMillis();
        AtomicLong lastSeenTs = new AtomicLong(turnStart);
        ScheduledFuture<?> poller = PROGRESS_POLLER.scheduleAtFixedRate(() -> {
            for (Map<String, Object> trace : traceRecorder.recentSince(lastSeenTs.get(), 10)) {
                if (trace.get("ts") instanceof Number ts) {
                    lastSeenTs.set(Math.max(lastSeenTs.get(), ts.longValue()));
                }
                send(emitter, AgentEvent.of("tool", String.valueOf(trace.get("tool"))));
            }
        }, 250, 250, TimeUnit.MILLISECONDS);

        emitter.onCompletion(() -> poller.cancel(false));
        emitter.onTimeout(() -> {
            poller.cancel(false);
            emitter.complete();
        });

        agentService.stream(sessionId, request.getMessage(), new AgentStreamListener() {
            @Override
            public void onUsage(Integer promptTokens, Integer completionTokens) {
                int prompt = promptTokens == null ? 0 : promptTokens;
                int completion = completionTokens == null ? 0 : completionTokens;
                send(emitter, AgentEvent.of("usage", prompt + "," + completion));
            }
        }).subscribe(
                chunk -> send(emitter, AgentEvent.of("delta", chunk)),
                error -> {
                    send(emitter, AgentEvent.of("error", "模型调用失败：" + rootMessage(error)));
                    emitter.complete();
                },
                () -> {
                    send(emitter, AgentEvent.of("done", sessionId));
                    emitter.complete();
                }
        );
        return emitter;
    }

    /**
     * 同步对话，方便 Postman 调试
     */
    @PostMapping("/chat/sync")
    public Result chatSync(@RequestBody ChatRequest request) {
        if (StrUtil.isBlank(request.getMessage())) {
            return Result.fail("问题不能为空");
        }
        String sessionId = StrUtil.isBlank(request.getSessionId())
                ? IdUtil.fastSimpleUUID()
                : request.getSessionId();
        String answer = agentService.chat(sessionId, request.getMessage());
        return Result.ok(Map.of("sessionId", sessionId, "answer", answer));
    }

    /**
     * 最近的工具调用记录，用于排查“模型为什么这么答”
     */
    @GetMapping("/traces")
    public Result traces(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(traceRecorder.recent(Math.min(Math.max(limit, 1), 100)));
    }

    @DeleteMapping("/memory/{sessionId}")
    public Result clearMemory(@PathVariable String sessionId) {
        memory.clear(sessionId);
        return Result.ok();
    }

    /**
     * token 用量：最近明细 + 今日汇总（成本按 V4.1-Flash 高峰价估算）
     */
    @GetMapping("/usage")
    public Result usage(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(usageRecorder.summary(Math.min(Math.max(limit, 1), 100)));
    }

    /**
     * 重建店铺向量索引（语料变更后调用）
     */
    @PostMapping("/admin/reindex")
    public Result reindex() {
        Map<String, Object> result = indexer.rebuild();
        searchService.invalidate();
        return Result.ok(result);
    }

    /**
     * 重建店铺 GEO 索引（坐标变更后调用）
     */
    @PostMapping("/admin/reindex-geo")
    public Result reindexGeo() {
        return Result.ok(geoIndexer.rebuild());
    }

    /**
     * 语义检索调试接口，直接看召回结果和相似度
     */
    @GetMapping("/admin/semantic")
    public Result semantic(@RequestParam("q") String query,
                           @RequestParam(defaultValue = "5") int topK) {
        if (StrUtil.isBlank(query)) {
            return Result.fail("查询词不能为空");
        }
        int limit = Math.min(Math.max(topK, 1), 20);
        return Result.ok(JSONUtil.parseArray(semanticTools.semanticSearch(query, limit)));
    }

    private void send(SseEmitter emitter, AgentEvent event) {
        try {
            emitter.send(SseEmitter.event().data(event, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            log.debug("SSE 发送失败: {}", e.getMessage());
        }
    }

    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return StrUtil.maxLength(StrUtil.blankToDefault(cause.getMessage(), cause.getClass().getSimpleName()), 200);
    }
}
