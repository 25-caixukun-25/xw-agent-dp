package com.hmdp.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * SSE 事件：session（回传会话 id）/ delta（增量文本）/ error / done
 */
@Data
@AllArgsConstructor
public class AgentEvent {
    private String type;
    private String content;

    public static AgentEvent of(String type, String content) {
        return new AgentEvent(type, content);
    }
}
