package com.hmdp.agent.tool;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.agent.trace.ToolTraceRecorder;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopTypeService;
import com.hmdp.service.IVoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 暴露给大模型的只读工具集。
 * 所有方法都返回精简后的 JSON 字符串，避免把图片 URL 等无关字段塞进上下文。
 */
@Component
@RequiredArgsConstructor
public class ShopTools {

    private final IShopService shopService;
    private final IShopTypeService shopTypeService;
    private final IVoucherService voucherService;
    private final IBlogService blogService;
    private final ToolTraceRecorder trace;

    @Tool(name = "list_shop_types",
            description = "查询平台所有店铺分类（美食、KTV、丽人美发、美睫美甲等），返回分类 id 和名称。用户想按分类找店但没给出分类 id 时，必须先调用本工具。")
    public String listShopTypes() {
        return trace.record("list_shop_types", null, () -> {
            List<ShopType> types = shopTypeService.query().orderByAsc("sort").list();
            List<Map<String, Object>> data = new ArrayList<>();
            for (ShopType type : types) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("typeId", type.getId());
                item.put("name", type.getName());
                data.add(item);
            }
            return JSONUtil.toJsonStr(data);
        });
    }

    @Tool(name = "search_shop_by_name",
            description = "按店铺名称关键词模糊搜索，最多返回 5 家。适用于用户说出了具体店名或品类词（如 火锅、茶餐厅、KTV）的场景。")
    public String searchShopByName(
            @ToolParam(description = "店铺名称关键词，不要带标点") String keyword) {
        return trace.record("search_shop_by_name", keyword, () -> {
            if (StrUtil.isBlank(keyword)) {
                return "[]";
            }
            List<Shop> shops = shopService.query()
                    .like("name", keyword.trim())
                    .last("limit 5")
                    .list();
            return JSONUtil.toJsonStr(briefList(shops));
        });
    }

    @Tool(name = "list_shops_by_type",
            description = "按分类分页查询店铺，每页 5 家，返回店铺名、商圈、地址、人均、评分、销量。用户说“看看美食/有什么 KTV”这类需求时使用。")
    public String listShopsByType(
            @ToolParam(description = "分类 id，可通过 list_shop_types 工具获取") Integer typeId,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer current) {
        int page = (current == null || current < 1) ? 1 : current;
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("typeId", typeId);
        args.put("current", page);
        return trace.record("list_shops_by_type", args, () -> {
            Result result = shopService.queryShopByType(typeId, page, null, null);
            return JSONUtil.toJsonStr(toBriefList(result));
        });
    }

    @Tool(name = "find_nearby_shops",
            description = "按经纬度查询 5 公里内的店铺，结果按距离由近到远排序并附带距离（单位：米）。用户提到“附近”“离我近”时使用。")
    public String findNearbyShops(
            @ToolParam(description = "分类 id，可通过 list_shop_types 获取") Integer typeId,
            @ToolParam(description = "经度，例如 120.149993") Double longitude,
            @ToolParam(description = "纬度，例如 30.334229") Double latitude) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("typeId", typeId);
        args.put("longitude", longitude);
        args.put("latitude", latitude);
        return trace.record("find_nearby_shops", args, () -> {
            Result result = shopService.queryShopByType(typeId, 1, longitude, latitude);
            return JSONUtil.toJsonStr(toBriefList(result));
        });
    }

    @Tool(name = "get_shop_detail",
            description = "查询单个店铺的详细信息：地址、商圈、人均、评分、销量、评论数、营业时间。回答“这家店怎么样”之前必须先调用。")
    public String getShopDetail(
            @ToolParam(description = "店铺 id") Long shopId) {
        return trace.record("get_shop_detail", shopId, () -> {
            Result result = shopService.queryById(shopId);
            if (result == null || !Boolean.TRUE.equals(result.getSuccess()) || !(result.getData() instanceof Shop shop)) {
                return "{\"error\":\"店铺不存在\"}";
            }
            Map<String, Object> detail = brief(shop);
            detail.put("comments", shop.getComments());
            detail.put("images", shop.getImages());
            return JSONUtil.toJsonStr(detail);
        });
    }

    @Tool(name = "list_vouchers_of_shop",
            description = "查询某家店可用的优惠券/代金券，返回面额、售价、使用规则。回答“有什么优惠”之前必须先调用。")
    public String listVouchersOfShop(
            @ToolParam(description = "店铺 id") Long shopId) {
        return trace.record("list_vouchers_of_shop", shopId, () -> {
            Result result = voucherService.queryVoucherOfShop(shopId);
            if (result == null || !Boolean.TRUE.equals(result.getSuccess()) || !(result.getData() instanceof List<?> list)) {
                return "[]";
            }
            List<Map<String, Object>> data = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Voucher voucher)) {
                    continue;
                }
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("voucherId", voucher.getId());
                map.put("title", voucher.getTitle());
                map.put("subTitle", voucher.getSubTitle());
                map.put("payYuan", voucher.getPayValue() == null ? null : voucher.getPayValue() / 100.0);
                map.put("actualYuan", voucher.getActualValue() == null ? null : voucher.getActualValue() / 100.0);
                map.put("rules", voucher.getRules());
                data.add(map);
            }
            return JSONUtil.toJsonStr(data);
        });
    }

    @Tool(name = "list_hot_blogs",
            description = "查询热门探店笔记，返回标题、点赞数、关联店铺 id。用户想看看大家推荐什么时使用。")
    public String listHotBlogs(
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer current) {
        int page = (current == null || current < 1) ? 1 : current;
        return trace.record("list_hot_blogs", page, () -> {
            Result result = blogService.queryHotBlog(page);
            if (result == null || !Boolean.TRUE.equals(result.getSuccess()) || !(result.getData() instanceof List<?> list)) {
                return "[]";
            }
            List<Map<String, Object>> data = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Blog blog)) {
                    continue;
                }
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("blogId", blog.getId());
                map.put("title", blog.getTitle());
                map.put("shopId", blog.getShopId());
                map.put("liked", blog.getLiked());
                data.add(map);
            }
            return JSONUtil.toJsonStr(data);
        });
    }

    @Tool(name = "get_blog_detail",
            description = "查询单篇探店笔记的正文（最多返回 300 字）与作者昵称。用户追问某篇笔记内容时使用。")
    public String getBlogDetail(
            @ToolParam(description = "笔记 id") Long blogId) {
        return trace.record("get_blog_detail", blogId, () -> {
            Result result = blogService.queryBlogById(blogId);
            if (result == null || !Boolean.TRUE.equals(result.getSuccess()) || !(result.getData() instanceof Blog blog)) {
                return "{\"error\":\"笔记不存在\"}";
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("blogId", blog.getId());
            map.put("title", blog.getTitle());
            map.put("author", blog.getName());
            map.put("shopId", blog.getShopId());
            map.put("liked", blog.getLiked());
            map.put("content", StrUtil.maxLength(StrUtil.blankToDefault(blog.getContent(), ""), 300));
            return JSONUtil.toJsonStr(map);
        });
    }

    private List<Map<String, Object>> toBriefList(Result result) {
        List<Map<String, Object>> data = new ArrayList<>();
        if (result == null || !Boolean.TRUE.equals(result.getSuccess()) || !(result.getData() instanceof List<?> list)) {
            return data;
        }
        for (Object item : list) {
            if (item instanceof Shop shop) {
                data.add(brief(shop));
            }
        }
        return data;
    }

    private List<Map<String, Object>> briefList(List<Shop> shops) {
        List<Map<String, Object>> data = new ArrayList<>(shops.size());
        for (Shop shop : shops) {
            data.add(brief(shop));
        }
        return data;
    }

    private Map<String, Object> brief(Shop shop) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("shopId", shop.getId());
        map.put("name", shop.getName());
        map.put("area", shop.getArea());
        map.put("address", shop.getAddress());
        map.put("avgPrice", shop.getAvgPrice());
        map.put("score", shop.getScore() == null ? null : shop.getScore() / 10.0);
        map.put("sold", shop.getSold());
        map.put("openHours", shop.getOpenHours());
        if (shop.getDistance() != null) {
            map.put("distanceMeters", Math.round(shop.getDistance() * 1000));
        }
        return map;
    }
}
