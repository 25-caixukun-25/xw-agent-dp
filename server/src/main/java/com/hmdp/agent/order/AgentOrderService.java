package com.hmdp.agent.order;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Agent 发起的秒杀下单：两阶段提交。
 * <p>
 * 1. prepare：只预留订单号，不扣库存、不落库，等用户在页面上确认；
 * 2. confirm：以 orderId 为幂等键，第一次真正扣库存并写订单，重复提交直接返回首次结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrderService {

    private static final String PENDING_KEY = "order:pending:";
    private static final String PENDING_USER_KEY = "order:pending:user:";
    private static final String DONE_KEY = "order:done:";
    private static final long PENDING_TTL_MINUTES = 10L;
    private static final long DONE_TTL_HOURS = 24L;

    private final StringRedisTemplate stringRedisTemplate;
    private final ISeckillVoucherService seckillVoucherService;
    private final IVoucherOrderService voucherOrderService;
    private final IVoucherService voucherService;
    private final RedisWorker.RedisIdWorker redisIdWorker;

    /**
     * 预留订单：只生成 orderId，不产生任何写库和扣库存操作
     */
    public Map<String, Object> prepare(Long voucherId, Long userId) {
        if (voucherId == null) {
            throw new IllegalArgumentException("优惠券 id 不能为空");
        }
        if (userId == null) {
            throw new IllegalStateException("请先登录后再下单");
        }
        SeckillVoucher seckill = seckillVoucherService.getById(voucherId);
        if (seckill == null) {
            throw new IllegalArgumentException("这不是一张秒杀券，或券不存在");
        }
        if (seckill.getStock() == null || seckill.getStock() <= 0) {
            throw new IllegalStateException("库存不足");
        }

        long orderId = redisIdWorker.nextId("order");
        Map<String, String> pending = new HashMap<>();
        pending.put("userId", String.valueOf(userId));
        pending.put("voucherId", String.valueOf(voucherId));
        String pendingKey = PENDING_KEY + orderId;
        stringRedisTemplate.opsForHash().putAll(pendingKey, pending);
        stringRedisTemplate.expire(pendingKey, PENDING_TTL_MINUTES, TimeUnit.MINUTES);
        stringRedisTemplate.opsForValue()
                .set(PENDING_USER_KEY + userId, String.valueOf(orderId), PENDING_TTL_MINUTES, TimeUnit.MINUTES);

        Voucher voucher = voucherService.getById(voucherId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", String.valueOf(orderId));
        result.put("voucherId", String.valueOf(voucherId));
        result.put("title", voucher == null ? "秒杀券" : voucher.getTitle());
        result.put("stock", seckill.getStock());
        result.put("needConfirm", true);
        log.info("预留秒杀订单 orderId={} userId={} voucherId={}", orderId, userId, voucherId);
        return result;
    }

    /**
     * 确认下单：orderId 为幂等键，重复调用返回首次结果
     */
    public Map<String, Object> confirm(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId 不能为空");
        }
        String doneKey = DONE_KEY + orderId;
        String cached = stringRedisTemplate.opsForValue().get(doneKey);
        if (cached != null) {
            Map<String, Object> result = new LinkedHashMap<>(JSONUtil.parseObj(cached));
            result.put("duplicated", true);
            return result;
        }

        Map<Object, Object> pending = stringRedisTemplate.opsForHash().entries(PENDING_KEY + orderId);
        if (pending == null || pending.isEmpty()) {
            throw new IllegalStateException("订单不存在或已超时，请重新发起");
        }
        Long userId = Long.valueOf(String.valueOf(pending.get("userId")));
        Long voucherId = Long.valueOf(String.valueOf(pending.get("voucherId")));

        VoucherOrder order = new VoucherOrder()
                .setId(orderId)
                .setUserId(userId)
                .setVoucherId(voucherId)
                .setStatus(1)
                .setCreateTime(LocalDateTime.now());
        voucherOrderService.createVoucherOrder(order);

        VoucherOrder saved = voucherOrderService.getById(orderId);
        if (saved == null) {
            throw new IllegalStateException("下单失败：库存不足或你已购买过这张券");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", String.valueOf(orderId));
        result.put("voucherId", String.valueOf(voucherId));
        result.put("userId", String.valueOf(userId));
        result.put("status", "CREATED");
        result.put("duplicated", false);
        stringRedisTemplate.opsForValue().set(doneKey, JSONUtil.toJsonStr(result), DONE_TTL_HOURS, TimeUnit.HOURS);
        stringRedisTemplate.delete(PENDING_KEY + orderId);
        stringRedisTemplate.delete(PENDING_USER_KEY + userId);
        log.info("秒杀订单已创建 orderId={} userId={}", orderId, userId);
        return result;
    }

    /**
     * 查询当前用户最新一笔待确认订单
     */
    public Map<String, Object> pending(Long userId) {
        if (userId == null) {
            return null;
        }
        String orderId = stringRedisTemplate.opsForValue().get(PENDING_USER_KEY + userId);
        if (orderId == null) {
            return null;
        }
        Map<Object, Object> pending = stringRedisTemplate.opsForHash().entries(PENDING_KEY + orderId);
        if (pending == null || pending.isEmpty()) {
            return null;
        }
        Long voucherId = Long.valueOf(String.valueOf(pending.get("voucherId")));
        Voucher voucher = voucherService.getById(voucherId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("voucherId", String.valueOf(voucherId));
        result.put("title", voucher == null ? "秒杀券" : voucher.getTitle());
        result.put("payYuan", voucher == null || voucher.getPayValue() == null ? null : voucher.getPayValue() / 100.0);
        return result;
    }

    public boolean cancel(Long orderId) {
        if (orderId == null) {
            return false;
        }
        Map<Object, Object> pending = stringRedisTemplate.opsForHash().entries(PENDING_KEY + orderId);
        if (pending != null && !pending.isEmpty()) {
            stringRedisTemplate.delete(PENDING_USER_KEY + pending.get("userId"));
        }
        stringRedisTemplate.delete(PENDING_KEY + orderId);
        return true;
    }
}
