package com.mdwiki.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateAnnotationRequest(
    @field:NotBlank val highlightedText: String,
    @field:NotBlank val anchorContext: String,
    val comment: String? = null,
    val rangeStart: Int? = null,
    val rangeEnd: Int? = null,
    val color: String? = null
)

data class UpdateAnnotationRequest(
    val comment: String? = null,
    @field:Size(max = 20) val color: String? = null,
    val clearComment: Boolean? = null,
    val clearColor: Boolean? = null
)

data class AnnotationResponse(
    val id: UUID,
    val pageId: UUID,
    val highlightedText: String,
    val anchorContext: String,
    val comment: String?,
    val rangeStart: Int?,
    val rangeEnd: Int?,
    val color: String?,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant
)
