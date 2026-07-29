package com.mdwiki.mapper

import com.mdwiki.dto.AnnotationResponse
import com.mdwiki.model.Annotation

fun Annotation.toResponse(): AnnotationResponse = AnnotationResponse(
    id = id!!,
    pageId = pageId,
    highlightedText = highlightedText,
    anchorContext = anchorContext,
    comment = comment,
    rangeStart = rangeStart,
    rangeEnd = rangeEnd,
    color = color,
    createdBy = createdBy,
    createdAt = createdAt,
    updatedAt = updatedAt
)
