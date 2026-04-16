package com.mdwiki.dto

import java.time.Instant
import java.util.UUID

data class AttachmentResponse(
    val id: UUID,
    val originalName: String,
    val storedName: String,
    val contentType: String,
    val sizeBytes: Long,
    val uploadedBy: String?,
    val pageId: UUID?,
    val url: String,
    val createdAt: Instant
)
