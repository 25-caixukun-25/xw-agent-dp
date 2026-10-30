package com.hmdp.embedding;

/**
 * 向量相似度计算，抽成静态工具便于单测。
 */
public final class VectorMath {

    private VectorMath() {
    }

    /**
     * 余弦相似度，长度不一致时按较短的维度计算，零向量返回 0。
     */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            return 0;
        }
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
