package com.mdwiki.mcp

import com.mdwiki.service.AttachmentService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.util.UUID

@Component
class WikiAttachmentUploadTool(private val attachmentService: AttachmentService) {

    @McpTool(
        name = "wiki_attachment_upload",
        description = "Upload attachment from a file path on the mdwiki-api host. Requires EDITOR or ADMIN role."
    )
    fun upload(
        @McpToolParam(description = "Absolute or relative file path on server host") filePath: String,
        @McpToolParam(description = "Optional override for original attachment name", required = false) originalName: String?,
        @McpToolParam(description = "Optional MIME type override", required = false) contentType: String?,
        @McpToolParam(description = "Optional page UUID to link attachment", required = false) pageId: String?
    ): Map<String, Any?> {
        val username = SecurityContextHolder.getContext().authentication?.name
            ?: throw IllegalStateException("Not authenticated")
        val parsedPageId = pageId?.takeIf { it.isNotBlank() }?.let(::parseUuid)
        val attachment = attachmentService.uploadFromFile(
            filePath = Path.of(filePath),
            username = username,
            pageId = parsedPageId,
            originalName = originalName,
            contentType = contentType
        )
        return mapOf(
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

    private fun parseUuid(raw: String): UUID {
        return try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid UUID: $raw")
        }
    }
}
