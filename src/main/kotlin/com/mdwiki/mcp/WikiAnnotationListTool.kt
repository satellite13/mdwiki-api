package com.mdwiki.mcp

import com.mdwiki.service.AnnotationService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiAnnotationListTool(private val annotationService: AnnotationService) {

    @McpTool(
        name = "wiki_annotation_list",
        description = "List annotations for a wiki page by its slug."
    )
    fun list(
        @McpToolParam(description = "Page slug") slug: String
    ): List<Map<String, Any?>> {
        return annotationService.listBySlug(slug).map { annotation ->
            mapOf(
                "id" to annotation.id.toString(),
                "pageId" to annotation.pageId.toString(),
                "highlightedText" to annotation.highlightedText,
                "anchorContext" to annotation.anchorContext,
                "comment" to annotation.comment,
                "rangeStart" to annotation.rangeStart,
                "rangeEnd" to annotation.rangeEnd,
                "color" to annotation.color,
                "createdBy" to annotation.createdBy,
                "createdAt" to annotation.createdAt.toString(),
                "updatedAt" to annotation.updatedAt.toString()
            )
        }
    }
}
