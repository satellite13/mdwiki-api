package com.mdwiki.mcp

import com.mdwiki.mcp.McpSupport.attachmentToMap
import com.mdwiki.mcp.McpSupport.parseUuid
import com.mdwiki.mcp.McpSupport.currentUsername
import com.mdwiki.service.AttachmentService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiAttachmentListTool(private val attachmentService: AttachmentService) {

    @McpTool(
        name = "wiki_attachment_list",
        description = "List wiki attachments. Optionally filter by page UUID."
    )
    fun list(
        @McpToolParam(description = "Page number (0-based)", required = false) page: Int?,
        @McpToolParam(description = "Page size", required = false) size: Int?,
        @McpToolParam(description = "Optional page UUID to filter attachments", required = false) pageId: String?,
        @McpToolParam(description = "Optional substring filter on original file name", required = false) q: String?
    ): List<Map<String, Any?>> {
        val parsedPageId = pageId?.takeIf { it.isNotBlank() }?.let(::parseUuid)
        return attachmentService
            .list(page ?: 0, size ?: 20, parsedPageId, q, currentUsername())
            .content
            .map(::attachmentToMap)
    }
}
