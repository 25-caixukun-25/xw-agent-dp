package com.hmdp.agent.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRateLimitFilterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private AgentRateLimitFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        filter = new AgentRateLimitFilter(redis);
        ReflectionTestUtils.setField(filter, "limitPerMinute", 20);
    }

    private MockHttpServletRequest chatRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/agent/chat");
        request.setRemoteAddr("10.0.0.8");
        return request;
    }

    @Test
    @DisplayName("第 20 次请求放行，并设置窗口过期时间")
    void allowsRequestsUnderLimit() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(20L);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(chatRequest(), response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest(), "未超限应放行到后续链路");
    }

    @Test
    @DisplayName("第 21 次请求返回 429 且不再进入处理链")
    void blocksRequestsOverLimit() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(21L);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(chatRequest(), response, chain);

        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString(StandardCharsets.UTF_8).contains("请求过于频繁"));
        assertNull(chain.getRequest(), "超限请求不应继续执行");
    }

    @Test
    @DisplayName("窗口内首次请求会设置 Redis 过期时间，避免计数永不过期")
    void setsExpireOnFirstHit() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(1L);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(chatRequest(), new MockHttpServletResponse(), chain);

        verify(redis).expire(anyString(), eq(70L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("只对 POST /agent/chat 生效，其他接口不计数")
    void skipsOtherEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/agent/chat/sync");
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(valueOps, never()).increment(anyString());
        assertNotNull(chain.getRequest());
    }

    @Test
    @DisplayName("Redis 异常时放行，限流组件不能拖垮主链路")
    void failsOpenWhenRedisDown() throws Exception {
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("redis down"));
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(chatRequest(), response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
        verify(redis, never()).expire(anyString(), anyLong(), eq(TimeUnit.SECONDS));
    }
}
