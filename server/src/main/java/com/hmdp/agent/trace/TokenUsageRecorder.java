package com.hmdp.agent.trace;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Token 用量记录：每次模型调用的输入/输出 token、耗时，写入 Redis。
 * 成本按 DeepSeek-V4.1-Flash 高峰价估算（输入 3 元/百万，输出 9 元/百万）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenUsageRecorder {

    private static final String RECENT_KEY = "agent:usage:recent";
    private static final String DAY_KEY_PREFIX = "agent:usage:day:";
    private static final int MAX_SIZE = 200;
    private static final double INPUT_PRICE_PER_MILLION = 3.0;
    private static final double OUTPUT_PRICE_PER_MILLION = 9.0;

    private final StringRedisTemplate stringRedisTemplate;

    public void record(String sessionId, Integer promptTokens, Integer completionTokens,
                       int answerChars, long costMs) {
        if (promptTokens == null && completionTokens == null) {
            return;
        }
        int prompt = promptTokens == null ? 0 : promptTokens;
        int completion = completionTokens == null ? 0 : completionTokens;
        double costYuan = prompt / 1_000_000.0 * INPUT_PRICE_PER_MILLION
                + completion / 1_000_000.0 * OUTPUT_PRICE_PER_MILLION;

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        item.put("sessionId", sessionId);
        item.put("promptTokens", prompt);
        item.put("completionTokens", completion);
        item.put("totalTokens", prompt + completion);
        item.put("costMs", costMs);
        item.put("answerChars", answerChars);
        item.put("estimatedYuan", String.format("%.4f", costYuan));

        String dayKey = DAY_KEY_PREFIX + LocalDate.now();
        try {
            stringRedisTemplate.opsForList().leftPush(RECENT_KEY, JSONUtil.toJsonStr(item));
            stringRedisTemplate.opsForList().trim(RECENT_KEY, 0, MAX_SIZE - 1);
            stringRedisTemplate.opsForHash().increment(dayKey, "requests", 1);
            stringRedisTemplate.opsForHash().increment(dayKey, "promptTokens", prompt);
            stringRedisTemplate.opsForHash().increment(dayKey, "completionTokens", completion);
            stringRedisTemplate.expire(dayKey, 7, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("写入 token 用量失败: {}", e.getMessage());
        }
        log.info("[usage] session={} prompt={} completion={} cost={}ms", sessionId, prompt, completion, costMs);
    }

    public Map<String, Object> summary(int limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> recentJson = stringRedisTemplate.opsForList().range(RECENT_KEY, 0, Math.max(0, limit - 1));
        List<Map<String, Object>> recent = new ArrayList<>();
        if (recentJson != null) {
            for (String json : recentJson) {
                Map<String, Object> map = new LinkedHashMap<>();
                JSONUtil.parseObj(json).forEach(map::put);
                recent.add(map);
            }
        }
        result.put("recent", recent);

        String dayKey = DAY_KEY_PREFIX + LocalDate.now();
        Map<Object, Object> day = stringRedisTemplate.opsForHash().entries(dayKey);
        long prompt = parseLong(day.get("promptTokens"));
        long completion = parseLong(day.get("completionTokens"));
        long requests = parseLong(day.get("requests"));
        Map<String, Object> today = new LinkedHashMap<>();
        today.put("date", LocalDate.now().toString());
        today.put("requests", requests);
        today.put("promptTokens", prompt);
        today.put("completionTokens", completion);
        today.put("totalTokens", prompt + completion);
        today.put("estimatedYuan", String.format("%.4f",
                prompt / 1_000_000.0 * INPUT_PRICE_PER_MILLION + completion / 1_000_000.0 * OUTPUT_PRICE_PER_MILLION));
        result.put("today", today);
        return result;
    }

    private long parseLong(Object value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
