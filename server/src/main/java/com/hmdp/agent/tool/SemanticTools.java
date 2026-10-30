package com.hmdp.agent.tool;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.agent.trace.ToolTraceRecorder;
import com.hmdp.embedding.SemanticSearchService;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 语义检索工具：解决关键词匹配搜不到的问题（“适合约会的安静地方”这类描述性需求）。
 */
@Component
@RequiredArgsConstructor
public class SemanticTools {

    private final SemanticSearchService semanticSearchService;
    private final IShopService shopService;
    private final ToolTraceRecorder trace;

    @Tool(name = "semantic_search_shops",
            description = "语义搜索店铺。当用户用自然语言描述需求（如“适合约会的安静餐厅”“带娃能玩一天的地方”“适合团建的场所”），"
                    + "而不是给出店名或分类时，优先使用本工具。返回最相关的店铺、相似度和匹配的描述片段。")
    public String semanticSearch(
            @ToolParam(description = "用户的自然语言需求描述，尽量保留原话") String query,
            @ToolParam(description = "返回条数，默认 5，最多 10", required = false) Integer topK) {
        int limit = topK == null ? 5 : Math.min(Math.max(topK, 1), 10);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", query);
        args.put("topK", limit);
        return trace.record("semantic_search_shops", args, () -> {
            List<SemanticSearchService.Match> matches = semanticSearchService.search(query, limit);
            if (matches.isEmpty()) {
                return "[]";
            }
            List<Long> ids = matches.stream().map(SemanticSearchService.Match::shopId).toList();
            Map<Long, Shop> shopMap = shopService.listByIds(ids).stream()
                    .collect(Collectors.toMap(Shop::getId, Function.identity()));

            List<Map<String, Object>> data = new ArrayList<>();
            for (SemanticSearchService.Match match : matches) {
                Shop shop = shopMap.get(match.shopId());
                if (shop == null) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("shopId", shop.getId());
                item.put("name", shop.getName());
                item.put("area", shop.getArea());
                item.put("address", shop.getAddress());
                item.put("avgPrice", shop.getAvgPrice());
                item.put("score", shop.getScore() == null ? null : shop.getScore() / 10.0);
                item.put("similarity", Math.round(match.similarity() * 1000) / 1000.0);
                item.put("desc", StrUtil.maxLength(match.content(), 120));
                data.add(item);
            }
            return JSONUtil.toJsonStr(data);
        });
    }
}
