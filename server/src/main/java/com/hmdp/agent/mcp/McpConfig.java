package com.hmdp.agent.mcp;

import com.hmdp.agent.tool.SemanticTools;
import com.hmdp.agent.tool.ShopTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把 Agent 用的 @Tool 方法原样暴露成 MCP 工具，供 Claude Desktop / WorkBuddy 等外部 Agent 调用。
 * 复用同一批 Bean，不复制实现，保证两条链路行为一致。
 */
@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider zhimiToolCallbackProvider(ShopTools shopTools, SemanticTools semanticTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(shopTools, semanticTools)
                .build();
    }
}
