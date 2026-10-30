package com.hmdp.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryShopCategory() {
        String key = "SHOP_TYPE:list";

        // 1. 查询 redis 中是否存在
        List<String> shopCt = stringRedisTemplate.opsForList().range(key, 0, -1);

        // 2. 判断缓存是否存在 (先判空再判不为空，防止空指针)
        if (shopCt != null && CollUtil.isNotEmpty(shopCt)) {
            // 3. 存在，进行反序列化
            // 核心修正：遍历 List<String>，将每个 JSON 字符串转为 ShopType 对象
            List<ShopType> shopTypes = shopCt.stream()
                    .map(item -> JSONUtil.toBean(item, ShopType.class))
                    .collect(Collectors.toList());

            return Result.ok(shopTypes);
        }

        // 4. 不存在，查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();

        // 5. 数据库也不存在，返回错误
        if (CollUtil.isEmpty(typeList)) {
            return Result.fail("分类不存在");
        }

        // 6. 存在，写入 redis
        // 核心修正：将对象列表转为 JSON 字符串列表，然后批量存入
        List<String> jsonStringList = typeList.stream()
                .map(JSONUtil::toJsonStr)
                .collect(Collectors.toList());

        stringRedisTemplate.opsForList().rightPushAll(key, jsonStringList);

        // 核心修正：设置 1 小时过期时间
        stringRedisTemplate.expire(key, 1, TimeUnit.HOURS);

        return Result.ok(typeList);
    }
}
