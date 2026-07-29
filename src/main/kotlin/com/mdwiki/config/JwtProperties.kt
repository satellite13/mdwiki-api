package com.mdwiki.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mdwiki.jwt")
data class JwtProperties(
    val secret: String,
    val expirationMs: Long = 86400000
) {
    init {
        // Без секрета токены подписывались бы известным дефолтом — не стартуем.
        require(secret.isNotBlank()) { "mdwiki.jwt.secret is not set (env JWT_SECRET)" }
    }
}
