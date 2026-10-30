package com.hmdp.agent;

/**
 * 流式对话过程中的回调，用于把「正在调用哪个工具」和「本次 token 消耗」推给前端。
 */
public interface AgentStreamListener {

    void onUsage(Integer promptTokens, Integer completionTokens);

    default void onDone() {
    }
}
