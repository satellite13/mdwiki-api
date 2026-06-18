package com.mdwiki.mcp

import com.mdwiki.dto.CreateAnnotationRequest
import com.mdwiki.service.AnnotationService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class WikiAnnotationCreateTool(private val annotationService: AnnotationService) {

    @McpTool(
        name = "wiki_annotation_create",
        description = "Create an annotation on a wiki page. Requires EDITOR or ADMIN role."
    )
    fun create(
        @McpToolParam(description = "Page slug") slug: String,
        @McpToolParam(description = "The text to highlight") highlightedText: String,
        @McpToolParam(description = "Surrounding context text for anchoring the annotation") anchorContext: String,
        @McpToolParam(description = "Optional user comment", required = false) comment: String?,
        @McpToolParam(description = "Optional start offset in source markdown", required = false) rangeStart: Int?,
        @McpToolParam(description = "Optional end offset in source markdown", required = false) rangeEnd: Int?,
        @McpToolParam(description = "Optional highlight color (hex)", required = false) color: String?
    ): Map<String, Any?> {
        val username = SecurityContextHolder.getContext().authentication?.name
            ?: throw IllegalStateException("Not authenticated")
        val annotation = annotationService.create(slug, CreateAnnotationRequest(
            highlightedText = highlightedText,
            anchorContext = anchorContext,
            comment = comment,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            color = color
        ), username)
        return mapOf(
            "id" to annotation.id.toString(),
            "pageId" to annotation.pageId.toString(),
            "highlightedText" to annotation.highlightedText,
            "comment" to annotation.comment,
            "createdBy" to annotation.createdBy,
            "createdAt" to annotation.createdAt.toString()
        )
    }
}
