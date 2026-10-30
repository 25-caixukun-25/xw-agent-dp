package com.hmdp.agent.dto;

import lombok.Data;

/**
 * 确认下单请求。orderId 用字符串传，避免 JS number 精度丢失（雪花 id 超过 2^53）。
 */
@Data
public class ConfirmOrderRequest {
    private String orderId;
}
