package com.mdwiki.mcp

import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class McpToolConfig {

    @Bean
    fun mcpToolCallbackProvider(
        wikiSearchTool: WikiSearchTool,
        wikiReadTool: WikiReadTool,
        wikiListTool: WikiListTool,
        wikiBacklinksTool: WikiBacklinksTool,
        wikiCreateTool: WikiCreateTool,
        wikiUpdateTool: WikiUpdateTool,
        wikiDeleteTool: WikiDeleteTool
    ): ToolCallbackProvider {
        return MethodToolCallbackProvider.builder()
            .toolObjects(
                wikiSearchTool, wikiReadTool, wikiListTool, wikiBacklinksTool,
                wikiCreateTool, wikiUpdateTool, wikiDeleteTool
            )
            .build()
    }
}
