package com.mdwiki.mapper

import com.mdwiki.dto.AttachmentResponse
import com.mdwiki.model.Attachment

fun Attachment.toResponse(): AttachmentResponse = AttachmentResponse(
    id = id!!,
    originalName = originalName,
    storedName = storedName,
    contentType = contentType,
    sizeBytes = sizeBytes,
    uploadedBy = uploadedBy?.username,
    pageId = page?.id,
    url = "/api/uploads/$storedName",
    createdAt = createdAt
)
