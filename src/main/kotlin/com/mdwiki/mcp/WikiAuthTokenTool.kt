package com.mdwiki.mcp

import com.mdwiki.mcp.McpSupport.currentUsername
import com.mdwiki.security.JwtScopes
import com.mdwiki.service.usecase.MintScopedRestTokenUseCase
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiAuthTokenTool(
    private val mintScopedRestTokenUseCase: MintScopedRestTokenUseCase
) {

    @McpTool(
        name = "wiki_auth_token",
        description = "Mint a short-lived scoped Bearer JWT for REST uploads that do not fit MCP. " +
            "scope=pages:import (default) → POST /api/pages/import (multipart files). " +
            "scope=attachments:upload → POST /api/attachments (multipart file). " +
            "TTL ~10 minutes. Requires EDITOR or ADMIN."
    )
    fun mint(
        @McpToolParam(
            description = "REST scope: pages:import (default) or attachments:upload",
            required = false
        )
        scope: String?
    ): Map<String, Any?> {
        val resolved = scope?.takeIf { it.isNotBlank() } ?: JwtScopes.PAGES_IMPORT
        val minted = mintScopedRestTokenUseCase.execute(currentUsername(), resolved)
        return mapOf(
            "token" to minted.token,
            "tokenType" to "Bearer",
            "scope" to minted.scope,
            "expiresAt" to minted.expiresAt.toString(),
            "expiresInSeconds" to minted.expiresInSeconds,
            "usage" to JwtScopes.usageHint(minted.scope)
        )
    }
}
