package com.hmdp.agent.trace;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 工具调用埋点：记录工具名、参数、耗时、结果大小，写入 Redis 环形列表，便于排查与效果评估。
 * 不使用 AOP 包装：@Tool 方法所在的 Bean 一旦被 CGLIB 代理，Spring AI 的方法扫描可能失效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolTraceRecorder {

    private static final String KEY = "agent:trace:recent";
    private static final int MAX_SIZE = 200;
    private static final int MAX_CALLS_PER_TURN = 8;
    private static final long TURN_WINDOW_MS = 90_000L;

    private static final ThreadLocal<Integer> TURN_COUNT = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Long> TURN_START = ThreadLocal.withInitial(System::currentTimeMillis);

    private final StringRedisTemplate stringRedisTemplate;

    public String record(String toolName, Object args, Supplier<String> action) {
        if (overLimit()) {
            log.warn("[tool-trace] 单轮工具调用超过 {} 次，拦截 {}", MAX_CALLS_PER_TURN, toolName);
            return "{\"error\":\"本轮查询次数已达上限，请直接基于已有信息回答用户\"}";
        }
        long start = System.currentTimeMillis();
        boolean success = true;
        String result = null;
        try {
            result = action.get();
            return result;
        } catch (RuntimeException e) {
            success = false;
            throw e;
        } finally {
            long cost = System.currentTimeMillis() - start;
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("ts", System.currentTimeMillis());
            trace.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            trace.put("tool", toolName);
            trace.put("args", args == null ? "" : StrUtil.maxLength(JSONUtil.toJsonStr(args), 100));
            trace.put("costMs", cost);
            trace.put("success", success);
            trace.put("resultChars", result == null ? 0 : result.length());
            log.info("[tool-trace] {} {}ms success={} chars={}", toolName, cost, success,
                    result == null ? 0 : result.length());
            try {
                stringRedisTemplate.opsForList().leftPush(KEY, JSONUtil.toJsonStr(trace));
                stringRedisTemplate.opsForList().trim(KEY, 0, MAX_SIZE - 1);
                stringRedisTemplate.expire(KEY, 1, TimeUnit.DAYS);
            } catch (Exception e) {
                log.warn("写入工具埋点失败: {}", e.getMessage());
            }
        }
    }

    public void beginTurn() {
        TURN_COUNT.set(0);
        TURN_START.set(System.currentTimeMillis());
    }

    public void endTurn() {
        TURN_COUNT.remove();
        TURN_START.remove();
    }

    /**
     * 单轮提问的工具调用次数限制，超过就返回提示让模型收敛，避免无限试探刷 token。
     * 用 90 秒滑动窗口兜底：工具若在别的线程执行，计数不会跨轮无限累加。
     */
    private boolean overLimit() {
        long now = System.currentTimeMillis();
        if (now - TURN_START.get() > TURN_WINDOW_MS) {
            TURN_START.set(now);
            TURN_COUNT.set(0);
        }
        int next = TURN_COUNT.get() + 1;
        TURN_COUNT.set(next);
        return next > MAX_CALLS_PER_TURN;
    }

    /**
     * 取指定时间点之后的埋点，按时间升序，用于流式过程中推送“正在调用工具”。
     * 工具在 boundedElastic 线程执行，ThreadLocal 传不过去，所以这里用埋点表做进度来源。
     */
    public List<Map<String, Object>> recentSince(long sinceTs, int limit) {
        List<Map<String, Object>> all = recent(50);
        return all.stream()
                .filter(item -> item.get("ts") instanceof Number number && number.longValue() > sinceTs)
                .sorted(Comparator.comparingLong(item -> ((Number) item.get("ts")).longValue()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> recent(int limit) {        List<String> list = stringRedisTemplate.opsForList().range(KEY, 0, Math.max(0, limit - 1));
        if (list == null) {
            return List.of();
        }
        return list.stream()
                .map(json -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    JSONUtil.parseObj(json).forEach(map::put);
                    return map;
                })
                .collect(Collectors.toList());
    }
}
