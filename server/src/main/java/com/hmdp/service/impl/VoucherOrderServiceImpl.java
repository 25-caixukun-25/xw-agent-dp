package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 优惠券秒杀：Lua 原子扣减 + Redis Stream 异步落库
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final ISeckillVoucherService seckillVoucherService;
    private final RedisWorker.RedisIdWorker redisIdWorker;
    private final StringRedisTemplate stringRedisTemplate;
    private final TransactionTemplate transactionTemplate;

    private static final String QUEUE_NAME = "stream.orders";
    private static final String GROUP_NAME = "g1";
    private static final String CONSUMER_NAME = "c1";
    private static final int MAX_HANDLE_COUNT = 50;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "seckill-order-consumer");
        thread.setDaemon(true);
        return thread;
    });

    @PostConstruct
    private void init() {
        // 启动时确保消费组存在，否则 XREADGROUP 会抛 NOGROUP
        try {
            stringRedisTemplate.opsForStream().createGroup(QUEUE_NAME, ReadOffset.from("0"), GROUP_NAME);
            log.info("已创建消费组 {}/{}", QUEUE_NAME, GROUP_NAME);
        } catch (Exception e) {
            log.info("消费组已存在或无需创建: {}", e.getMessage());
        }
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    @PreDestroy
    public void destroy() {
        SECKILL_ORDER_EXECUTOR.shutdownNow();
    }

    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 1.读取队列中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from(GROUP_NAME, CONSUMER_NAME),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(QUEUE_NAME, ReadOffset.lastConsumed())
                    );
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    // 2.解析并落库
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    handleVoucherOrder(voucherOrder);
                    // 3.确认消息
                    stringRedisTemplate.opsForStream().acknowledge(QUEUE_NAME, GROUP_NAME, record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常，转入 pending-list 补偿", e);
                    handlePendingList();
                }
            }
        }
    }

    /**
     * 补偿未确认的消息，最多处理 MAX_HANDLE_COUNT 条，避免死循环打满 CPU
     */
    private void handlePendingList() {
        for (int i = 0; i < MAX_HANDLE_COUNT; i++) {
            try {
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from(GROUP_NAME, CONSUMER_NAME),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(QUEUE_NAME, ReadOffset.from("0"))
                );
                if (list == null || list.isEmpty()) {
                    return;
                }
                MapRecord<String, Object, Object> record = list.get(0);
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(record.getValue(), new VoucherOrder(), true);
                handleVoucherOrder(voucherOrder);
                stringRedisTemplate.opsForStream().acknowledge(QUEUE_NAME, GROUP_NAME, record.getId());
            } catch (Exception e) {
                log.error("处理 pending 订单异常", e);
                try {
                    Thread.sleep(200);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        // 1.执行 Lua：库存校验 + 一人一单 + 扣减 + 投递消息，全部原子完成
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result == null ? -1 : result.intValue();
        // 2.非 0 代表没有购买资格
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        return Result.ok(orderId);
    }

    /**
     * 消费线程中执行，这里用编程式事务保证「扣库存 + 写订单」原子性
     */
    public void handleVoucherOrder(VoucherOrder voucherOrder) {
        transactionTemplate.executeWithoutResult(status -> createVoucherOrder(voucherOrder));
    }

    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 1.兜底校验一人一单
        long count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            log.warn("用户 {} 已购买过优惠券 {}，跳过", userId, voucherOrder.getVoucherId());
            return;
        }
        // 2.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                .update();
        if (!success) {
            log.warn("优惠券 {} 库存不足，跳过", voucherOrder.getVoucherId());
            return;
        }
        // 3.写订单
        save(voucherOrder);
    }
}
