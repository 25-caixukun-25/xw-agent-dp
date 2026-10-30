package com.hmdp.agent.dto;

import lombok.Data;

@Data
public class ChatRequest {
    /**
     * 会话 id，前端首次可不传，服务端生成后通过 SSE 回传
     */
    private String sessionId;
    /**
     * 用户问题
     */
    private String message;
}
