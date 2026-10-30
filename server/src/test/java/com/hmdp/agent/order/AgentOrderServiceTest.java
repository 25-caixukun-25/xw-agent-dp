package com.hmdp.agent.order;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentOrderServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private HashOperations<String, Object, Object> hashOps;
    @Mock
    private ISeckillVoucherService seckillVoucherService;
    @Mock
    private IVoucherOrderService voucherOrderService;
    @Mock
    private IVoucherService voucherService;
    @Mock
    private RedisWorker.RedisIdWorker redisIdWorker;

    private AgentOrderService orderService;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForHash()).thenReturn(hashOps);
        orderService = new AgentOrderService(redis, seckillVoucherService, voucherOrderService,
                voucherService, redisIdWorker);
    }

    @Test
    @DisplayName("预留订单只生成 orderId，不落库、不扣库存")
    void prepareOnlyReserves() {
        when(seckillVoucherService.getById(2L)).thenReturn(new SeckillVoucher().setVoucherId(2L).setStock(100));
        when(redisIdWorker.nextId("order")).thenReturn(999L);
        when(voucherService.getById(2L)).thenReturn(new Voucher().setId(2L).setTitle("100元代金券（秒杀）"));

        Map<String, Object> result = orderService.prepare(2L, 7L);

        assertEquals("999", result.get("orderId"));
        assertEquals(true, result.get("needConfirm"));
        verify(hashOps).putAll(eq("order:pending:999"), anyMap());
        verify(voucherOrderService, times(0)).createVoucherOrder(any());
    }

    @Test
    @DisplayName("未登录不能预留订单")
    void prepareRequiresLogin() {
        assertThrows(IllegalStateException.class, () -> orderService.prepare(2L, null));
    }

    @Test
    @DisplayName("库存为 0 时拒绝预留")
    void prepareRejectsSoldOut() {
        when(seckillVoucherService.getById(3L)).thenReturn(new SeckillVoucher().setVoucherId(3L).setStock(0));
        assertThrows(IllegalStateException.class, () -> orderService.prepare(3L, 7L));
    }

    @Test
    @DisplayName("同一个 orderId 重复确认只落库一次，第二次直接返回原结果")
    void confirmIsIdempotent() {
        when(hashOps.entries("order:pending:999"))
                .thenReturn(Map.of("userId", "7", "voucherId", "2"));
        when(voucherOrderService.getById(999L)).thenReturn(new VoucherOrder().setId(999L).setUserId(7L));
        when(valueOps.get("order:done:999"))
                .thenReturn(null, "{\"orderId\":\"999\",\"status\":\"CREATED\",\"duplicated\":false}");

        Map<String, Object> first = orderService.confirm(999L);
        Map<String, Object> second = orderService.confirm(999L);

        assertEquals(false, first.get("duplicated"));
        assertEquals(true, second.get("duplicated"));
        assertEquals("999", second.get("orderId"));
        verify(voucherOrderService, times(1)).createVoucherOrder(any());
    }

    @Test
    @DisplayName("订单不存在时确认会抛异常，而不是静默成功")
    void confirmMissingOrder() {
        when(hashOps.entries("order:pending:888")).thenReturn(Map.of());
        when(valueOps.get("order:done:888")).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> orderService.confirm(888L));
    }

    @Test
    @DisplayName("写入订单失败（库存被抢光）时抛出可读异常")
    void confirmFailsWhenOrderNotPersisted() {
        when(hashOps.entries("order:pending:777")).thenReturn(Map.of("userId", "7", "voucherId", "2"));
        when(voucherOrderService.getById(777L)).thenReturn(null);
        when(valueOps.get("order:done:777")).thenReturn(null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> orderService.confirm(777L));
        assertNotNull(error.getMessage());
    }
}
