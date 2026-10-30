package com.hmdp.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorMathTest {

    @Test
    @DisplayName("完全相同的向量相似度为 1")
    void identicalVectors() {
        float[] vector = {0.1f, 0.2f, 0.3f};
        assertEquals(1.0, VectorMath.cosine(vector, vector), 1e-6);
    }

    @Test
    @DisplayName("正交向量相似度为 0")
    void orthogonalVectors() {
        float[] a = {1f, 0f};
        float[] b = {0f, 1f};
        assertEquals(0.0, VectorMath.cosine(a, b), 1e-6);
    }

    @Test
    @DisplayName("零向量不参与计算，返回 0 而不是 NaN")
    void zeroVector() {
        assertEquals(0.0, VectorMath.cosine(new float[]{0f, 0f}, new float[]{1f, 2f}), 1e-6);
        assertEquals(0.0, VectorMath.cosine(null, new float[]{1f}), 1e-6);
    }

    @Test
    @DisplayName("长度不一致时按较短维度计算，不抛异常")
    void differentLength() {
        double value = VectorMath.cosine(new float[]{1f, 1f, 1f}, new float[]{1f, 1f});
        assertTrue(value > 0 && value <= 1.0);
    }

    @Test
    @DisplayName("同向但模长不同，相似度仍为 1")
    void scaledVectors() {
        double value = VectorMath.cosine(new float[]{1f, 2f}, new float[]{2f, 4f});
        assertEquals(1.0, value, 1e-6);
    }
}
