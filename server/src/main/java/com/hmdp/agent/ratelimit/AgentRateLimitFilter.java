package com.hmdp.agent.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * /agent/chat 单 IP 限流：Redis 计数器，固定时间窗（每分钟一个 key）。
 * 超限直接返回 429，不进入 SSE 流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRateLimitFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "rate:agent:chat:";
    private static final long WINDOW_SECONDS = 70;

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${hmdp.agent.rate-limit-per-minute:20}")
    private int limitPerMinute;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && "/agent/chat".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        String key = KEY_PREFIX + clientIp + ":" + (System.currentTimeMillis() / 60000);
        Long count;
        try {
            count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            // Redis 不可用时放行，避免限流组件把主链路拖垮
            log.warn("限流计数失败，放行本次请求: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        if (count != null && count > limitPerMinute) {
            log.warn("触发限流 ip={} count={}", clientIp, count);
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"success\":false,\"errorMsg\":\"请求过于频繁，请稍后再试\",\"data\":null}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
