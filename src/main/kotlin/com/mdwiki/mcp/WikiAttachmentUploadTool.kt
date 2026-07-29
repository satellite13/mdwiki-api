package com.mdwiki.mcp

import com.mdwiki.mcp.McpSupport.attachmentToMap
import com.mdwiki.mcp.McpSupport.currentUsername
import com.mdwiki.mcp.McpSupport.parseUuid
import com.mdwiki.service.AttachmentService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
class WikiAttachmentUploadTool(private val attachmentService: AttachmentService) {

    @McpTool(
        name = "wiki_attachment_upload",
        description = "Upload attachment from a file path on the mdwiki-api host. Requires EDITOR or ADMIN role. Path must be inside mdwiki.attachments.allowed-import-dirs."
    )
    fun upload(
        @McpToolParam(description = "Absolute or relative file path on server host (must be inside allowed import dirs)") filePath: String,
        @McpToolParam(description = "Optional override for original attachment name", required = false) originalName: String?,
        @McpToolParam(description = "Optional MIME type override", required = false) contentType: String?,
        @McpToolParam(description = "Optional page UUID to link attachment", required = false) pageId: String?
    ): Map<String, Any?> {
        val username = currentUsername()
        val parsedPageId = pageId?.takeIf { it.isNotBlank() }?.let(::parseUuid)
        val attachment = attachmentService.uploadFromFile(
            filePath = Path.of(filePath),
            username = username,
            pageId = parsedPageId,
            originalName = originalName,
            contentType = contentType
        )
        return attachmentToMap(attachment)
    }
}
