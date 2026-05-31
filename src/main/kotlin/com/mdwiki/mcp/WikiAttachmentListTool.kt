package com.mdwiki.mcp

import com.mdwiki.service.AttachmentService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class WikiAttachmentListTool(private val attachmentService: AttachmentService) {

    @McpTool(
        name = "wiki_attachment_list",
        description = "List wiki attachments. Optionally filter by page UUID."
    )
    fun list(
        @McpToolParam(description = "Page number (0-based)", required = false) page: Int?,
        @McpToolParam(description = "Page size", required = false) size: Int?,
        @McpToolParam(description = "Optional page UUID to filter attachments", required = false) pageId: String?
    ): List<Map<String, Any?>> {
        val parsedPageId = pageId?.takeIf { it.isNotBlank() }?.let(::parseUuid)
        return attachmentService.list(page ?: 0, size ?: 50, parsedPageId).map { attachment ->
            mapOf(
                "id" to attachment.id.toString(),
                "originalName" to attachment.originalName,
                "storedName" to attachment.storedName,
                "contentType" to attachment.contentType,
                "sizeBytes" to attachment.sizeBytes,
                "uploadedBy" to attachment.uploadedBy,
                "pageId" to attachment.pageId?.toString(),
                "url" to attachment.url,
                "createdAt" to attachment.createdAt.toString()
            )
        }
    }

    private fun parseUuid(raw: String): UUID {
        return try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid UUID: $raw")
        }
    }
}
