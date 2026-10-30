package com.hmdp.embedding;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 语义检索：查询向量化后与库中店铺向量做余弦相似度，取 Top-K。
 * 数据量只有千级，索引常驻内存，不引入向量数据库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final DashScopeEmbeddingClient embeddingClient;
    private final ShopVectorIndexer indexer;

    private volatile List<VectorEntry> cache = List.of();

    public record VectorEntry(long shopId, float[] vector, String content) {
    }

    public record Match(long shopId, double similarity, String content) {
    }

    public List<Match> search(String query, int topK) {
        ensureIndex();
        if (cache.isEmpty()) {
            return List.of();
        }
        float[] queryVector = embeddingClient.embedOne(query);
        List<Match> matches = new ArrayList<>(cache.size());
        for (VectorEntry entry : cache) {
            matches.add(new Match(entry.shopId(), cosine(queryVector, entry.vector()), entry.content()));
        }
        matches.sort(Comparator.comparingDouble(Match::similarity).reversed());
        return matches.subList(0, Math.min(topK, matches.size()));
    }

    public int indexedCount() {
        return cache.size();
    }

    public void invalidate() {
        cache = List.of();
    }

    /**
     * 首次检索时如果向量表为空，自动建一次索引
     */
    private void ensureIndex() {
        if (!cache.isEmpty()) {
            return;
        }
        synchronized (this) {
            if (!cache.isEmpty()) {
                return;
            }
            if (indexer.count() == 0) {
                if (!embeddingClient.enabled()) {
                    throw new IllegalStateException("未配置 DASHSCOPE_API_KEY，无法建立向量索引");
                }
                indexer.rebuild();
            }
            loadCache();
        }
    }

    private void loadCache() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT v.shop_id, v.embedding, d.content FROM tb_shop_vector v "
                        + "LEFT JOIN tb_shop_doc d ON d.shop_id = v.shop_id");
        List<VectorEntry> entries = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            long shopId = ((Number) row.get("shop_id")).longValue();
            String content = row.get("content") == null ? "" : String.valueOf(row.get("content"));
            List<Float> list = JSONUtil.toList(String.valueOf(row.get("embedding")), Float.class);
            float[] vector = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                vector[i] = list.get(i);
            }
            entries.add(new VectorEntry(shopId, vector, content));
        }
        cache = entries;
        log.info("语义索引加载完成：{} 条", entries.size());
    }

    private double cosine(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
