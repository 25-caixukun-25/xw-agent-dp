package com.hmdp.index;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 把店铺坐标写入 Redis GEO，供“附近 5 公里”查询使用。
 * 原项目靠单元测试手动灌数据，这里改成启动时自动重建，保证种子数据也能用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopGeoIndexer {

    private final IShopService shopService;
    private final StringRedisTemplate stringRedisTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            rebuild();
        } catch (Exception e) {
            log.warn("店铺 GEO 索引初始化失败：{}", e.getMessage());
        }
    }

    public Map<String, Object> rebuild() {
        List<Shop> shops = shopService.list();
        Map<Long, List<Shop>> byType = shops.stream()
                .filter(shop -> shop.getTypeId() != null && shop.getX() != null && shop.getY() != null)
                .collect(Collectors.groupingBy(Shop::getTypeId));

        Set<String> keys = byType.keySet().stream()
                .map(typeId -> RedisConstants.SHOP_GEO_KEY + typeId)
                .collect(Collectors.toSet());
        if (!keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }

        int total = 0;
        for (Map.Entry<Long, List<Shop>> entry : byType.entrySet()) {
            String key = RedisConstants.SHOP_GEO_KEY + entry.getKey();
            for (Shop shop : entry.getValue()) {
                stringRedisTemplate.opsForGeo()
                        .add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                total++;
            }
        }
        log.info("店铺 GEO 索引重建完成：{} 家，{} 个分类", total, byType.size());
        return Map.of("shops", total, "types", byType.size());
    }
}
