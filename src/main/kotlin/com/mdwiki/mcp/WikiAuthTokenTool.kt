package com.mdwiki.mcp

import com.mdwiki.mcp.McpSupport.currentUsername
import com.mdwiki.security.JwtScopes
import com.mdwiki.service.usecase.MintScopedRestTokenUseCase
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.stereotype.Component

@Component
class WikiAuthTokenTool(
    private val mintScopedRestTokenUseCase: MintScopedRestTokenUseCase
) {

    @McpTool(
        name = "wiki_auth_token",
        description = "Mint a short-lived scoped Bearer JWT for REST upload of large markdown files. " +
            "Use Authorization: Bearer <token> with POST /api/pages/import (multipart). " +
            "Scope is pages:import only; TTL ~10 minutes. Requires EDITOR or ADMIN. " +
            "Prefer this over wiki_import when contentMd is large."
    )
    fun mint(): Map<String, Any?> {
        val minted = mintScopedRestTokenUseCase.execute(currentUsername(), JwtScopes.PAGES_IMPORT)
        return mapOf(
            "token" to minted.token,
            "tokenType" to "Bearer",
            "scope" to minted.scope,
            "expiresAt" to minted.expiresAt.toString(),
            "expiresInSeconds" to minted.expiresInSeconds,
            "usage" to "Authorization: Bearer <token> → POST /api/pages/import (multipart field 'files')"
        )
    }
}
