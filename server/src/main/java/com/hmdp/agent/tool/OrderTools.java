package com.hmdp.agent.tool;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.agent.order.AgentOrderService;
import com.hmdp.agent.trace.ToolTraceRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 写操作工具：只做「预留订单」，真正的下单要用户在前端确认后调用 /agent/order/confirm。
 * 这样模型无法绕过二次确认直接扣库存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTools {

    private final AgentOrderService orderService;
    private final ToolTraceRecorder trace;

    @Tool(name = "prepare_seckill_order",
            description = "为用户预留一张秒杀优惠券的订单（不扣库存、不扣款），返回 orderId 和券名。"
                    + "只有在用户明确说要抢券/下单时才调用。调用后必须告诉用户「订单已预留，请在下方卡片点击确认」，"
                    + "不要声称已经下单成功。")
    public String prepareSeckillOrder(
            @ToolParam(description = "秒杀优惠券 id，可从 list_vouchers_of_shop 获取") Long voucherId,
            ToolContext toolContext) {
        Long userId = resolveUserId(toolContext);
        return trace.record("prepare_seckill_order", voucherId, () -> {
            try {
                Map<String, Object> result = orderService.prepare(voucherId, userId);
                return JSONUtil.toJsonStr(result);
            } catch (RuntimeException e) {
                return JSONUtil.toJsonStr(Map.of("error", StrUtil.blankToDefault(e.getMessage(), "预留订单失败")));
            }
        });
    }

    private Long resolveUserId(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object raw = toolContext.getContext().get("userId");
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(raw.toString());
    }
}
