package com.mdwiki.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mdwiki.jwt")
data class JwtProperties(
    val secret: String,
    val expirationMs: Long = 86400000
)
