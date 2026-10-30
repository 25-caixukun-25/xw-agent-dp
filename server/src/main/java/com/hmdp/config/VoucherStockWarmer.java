package com.hmdp.config;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 秒杀库存预热。
 * <p>
 * 下单走 Lua 脚本，脚本第一步就是读 seckill:stock:{voucherId}。
 * 如果这个 key 不存在，Lua 里 tonumber(nil) <= 0 会直接抛错，
 * 接口只会返回「服务器异常」，看起来像坏掉了。
 * 这里在启动时按数据库库存补齐缺失的 key（已存在的绝不覆盖，避免把抢购进度重置）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoucherStockWarmer implements ApplicationRunner {

    private final ISeckillVoucherService seckillVoucherService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        List<SeckillVoucher> vouchers = seckillVoucherService.list();
        int warmed = 0;
        for (SeckillVoucher voucher : vouchers) {
            String key = RedisConstants.SECKILL_STOCK_KEY + voucher.getVoucherId();
            Boolean created = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, String.valueOf(voucher.getStock()));
            if (Boolean.TRUE.equals(created)) {
                warmed++;
            }
        }
        log.info("秒杀库存预热完成：新增 {} 个 key，共扫描 {} 张秒杀券", warmed, vouchers.size());
    }
}
