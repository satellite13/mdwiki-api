package com.mdwiki.dto

import java.time.Instant
import java.util.UUID

data class CreateApiKeyRequest(
    val name: String,
    val expiresAt: Instant? = null
)

data class ApiKeyResponse(
    val id: UUID,
    val name: String,
    val lastUsedAt: Instant?,
    val createdAt: Instant,
    val expiresAt: Instant?
)

data class ApiKeyCreatedResponse(
    val id: UUID,
    val name: String,
    val key: String,
    val createdAt: Instant,
    val expiresAt: Instant?
)
