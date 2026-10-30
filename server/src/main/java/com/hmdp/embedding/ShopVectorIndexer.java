package com.hmdp.embedding;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把 tb_shop_doc 里的店铺描述向量化后写入 tb_shop_vector。
 * 数据量千级，全量重建即可，不做增量。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopVectorIndexer {

    private final JdbcTemplate jdbcTemplate;
    private final DashScopeEmbeddingClient embeddingClient;

    @Transactional
    public Map<String, Object> rebuild() {
        long start = System.currentTimeMillis();
        List<Map<String, Object>> docs = jdbcTemplate.queryForList(
                "SELECT shop_id, content FROM tb_shop_doc ORDER BY shop_id");
        if (docs.isEmpty()) {
            return Map.of("indexed", 0, "message", "tb_shop_doc 为空，先导入语料");
        }

        List<Long> shopIds = new ArrayList<>(docs.size());
        List<String> texts = new ArrayList<>(docs.size());
        for (Map<String, Object> row : docs) {
            shopIds.add(((Number) row.get("shop_id")).longValue());
            texts.add(String.valueOf(row.get("content")));
        }

        List<float[]> vectors = embeddingClient.embed(texts);

        List<Object[]> args = new ArrayList<>(docs.size());
        for (int i = 0; i < shopIds.size(); i++) {
            args.add(new Object[]{
                    shopIds.get(i),
                    embeddingClient.modelName(),
                    vectors.get(i).length,
                    JSONUtil.toJsonStr(vectors.get(i))
            });
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO tb_shop_vector (shop_id, model, dims, embedding) VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE model = VALUES(model), dims = VALUES(dims), embedding = VALUES(embedding)",
                args);

        long cost = System.currentTimeMillis() - start;
        log.info("向量索引重建完成：{} 条，耗时 {} ms", args.size(), cost);
        return Map.of("indexed", args.size(), "costMs", cost,
                "model", embeddingClient.modelName(), "dims", embeddingClient.dimensions());
    }

    public int count() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_shop_vector", Integer.class);
        return count == null ? 0 : count;
    }
}
