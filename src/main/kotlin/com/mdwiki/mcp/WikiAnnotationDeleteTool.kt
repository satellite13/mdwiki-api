package com.mdwiki.mcp

import com.mdwiki.mcp.McpSupport.parseUuid
import com.mdwiki.service.AnnotationService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiAnnotationDeleteTool(private val annotationService: AnnotationService) {

    @McpTool(
        name = "wiki_annotation_delete",
        description = "Delete an annotation by UUID. Requires EDITOR or ADMIN role."
    )
    fun delete(@McpToolParam(description = "Annotation UUID") id: String): Map<String, String> {
        val parsedId = parseUuid(id)
        annotationService.delete(parsedId)
        return mapOf("status" to "deleted", "id" to parsedId.toString())
    }
}
