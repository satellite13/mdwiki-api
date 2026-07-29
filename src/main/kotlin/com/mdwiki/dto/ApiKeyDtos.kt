package com.mdwiki.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateApiKeyRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 255)
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
