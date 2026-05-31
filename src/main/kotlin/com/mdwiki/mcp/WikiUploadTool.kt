package com.mdwiki.mcp

import com.mdwiki.service.AttachmentService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class WikiUploadTool(private val attachmentService: AttachmentService) {

    @McpTool(
        name = "wiki_upload",
        description = "Upload attachment via base64 payload and filename. Returns uploaded file URL. Requires EDITOR or ADMIN role."
    )
    fun upload(
        @McpToolParam(description = "Base64 file content. You may pass plain base64 or data URL (data:...;base64,...)")
        fileBase64: String,
        @McpToolParam(description = "Original filename, e.g. image.png")
        filename: String,
        @McpToolParam(description = "Optional MIME type override, e.g. image/png", required = false)
        contentType: String?,
        @McpToolParam(description = "Optional page UUID to link attachment", required = false)
        pageId: String?
    ): Map<String, Any?> {
        val username = SecurityContextHolder.getContext().authentication?.name
            ?: throw IllegalStateException("Not authenticated")
        val parsedPageId = pageId?.takeIf { it.isNotBlank() }?.let(::parseUuid)
        val attachment = attachmentService.uploadFromBase64(
            base64Data = fileBase64,
            filename = filename,
            username = username,
            pageId = parsedPageId,
            contentType = contentType
        )
        return mapOf(
            "id" to attachment.id.toString(),
            "filename" to attachment.originalName,
            "storedName" to attachment.storedName,
            "contentType" to attachment.contentType,
            "sizeBytes" to attachment.sizeBytes,
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
