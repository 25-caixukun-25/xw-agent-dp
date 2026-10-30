package com.hmdp.embedding;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 百炼（DashScope）文本向量化客户端，走 OpenAI 兼容协议。
 * 单次最多 10 条（text-embedding-v4 限制），这里自动分批。
 */
@Slf4j
@Component
public class DashScopeEmbeddingClient {

    private final RestClient restClient = RestClient.builder().build();

    @Value("${hmdp.embedding.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${hmdp.embedding.api-key:}")
    private String apiKey;

    @Value("${hmdp.embedding.model:text-embedding-v4}")
    private String model;

    @Value("${hmdp.embedding.dimensions:1024}")
    private int dimensions;

    @Value("${hmdp.embedding.batch-size:10}")
    private int batchSize;

    public boolean enabled() {
        return StrUtil.isNotBlank(apiKey);
    }

    public String modelName() {
        return model;
    }

    public int dimensions() {
        return dimensions;
    }

    /**
     * 批量向量化，返回顺序与入参一致
     */
    public List<float[]> embed(List<String> texts) {
        if (!enabled()) {
            throw new IllegalStateException("未配置 DASHSCOPE_API_KEY，无法调用向量化服务");
        }
        List<float[]> vectors = new ArrayList<>(texts.size());
        int size = Math.max(1, Math.min(batchSize, 10));
        for (int from = 0; from < texts.size(); from += size) {
            int to = Math.min(from + size, texts.size());
            vectors.addAll(embedBatch(texts.subList(from, to)));
        }
        return vectors;
    }

    public float[] embedOne(String text) {
        return embed(List.of(text)).get(0);
    }

    private List<float[]> embedBatch(List<String> batch) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", batch);
        body.put("dimensions", dimensions);
        body.put("encoding_format", "float");

        String response;
        try {
            response = restClient.post()
                    .uri(baseUrl + "/embeddings")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("向量化调用失败", e);
            throw new RuntimeException("向量化服务调用失败: " + e.getMessage(), e);
        }

        JSONObject json = JSONUtil.parseObj(response);
        JSONArray data = json.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new RuntimeException("向量化返回为空: " + StrUtil.maxLength(response, 200));
        }
        List<float[]> result = new ArrayList<>(data.size());
        for (Object item : data) {
            JSONArray embedding = ((JSONObject) item).getJSONArray("embedding");
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = embedding.getFloat(i);
            }
            result.add(vector);
        }
        log.debug("向量化 {} 条，维度 {}", result.size(), dimensions);
        return result;
    }
}
