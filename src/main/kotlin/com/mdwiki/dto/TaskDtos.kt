package com.mdwiki.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class OpenTaskResponse(
    val documentId: UUID,
    val slug: String,
    val documentTitle: String,
    val text: String,
    val sourceOffset: Int,
    val sourceLine: String,
    val updatedAt: Instant,
    val locked: Boolean
)

data class CompleteOpenTaskRequest(
    val documentId: UUID,
    val updatedAt: Instant,
    @field:PositiveOrZero
    val sourceOffset: Int,
    @field:NotBlank
    val sourceLine: String,
    @field:Size(max = 255)
    val summary: String? = null
)
