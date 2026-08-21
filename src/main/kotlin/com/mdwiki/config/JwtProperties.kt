package com.mdwiki.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mdwiki.jwt")
data class JwtProperties(
    val secret: String,
    val expirationMs: Long = 86400000,
    /** TTL for MCP-minted scoped REST tokens (JwtScopes registry). Default 10 minutes. */
    val scopedExpirationMs: Long = 600_000
) {
    init {
        // Без секрета токены подписывались бы известным дефолтом — не стартуем.
        require(secret.isNotBlank()) { "mdwiki.jwt.secret is not set (env JWT_SECRET)" }
        require(scopedExpirationMs > 0) { "mdwiki.jwt.scoped-expiration-ms must be positive" }
    }
}
