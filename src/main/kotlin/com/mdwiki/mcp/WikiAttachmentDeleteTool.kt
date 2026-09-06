package com.mdwiki.mcp

import com.mdwiki.mcp.McpSupport.parseUuid
import com.mdwiki.mcp.McpSupport.currentUsername
import com.mdwiki.service.AttachmentService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiAttachmentDeleteTool(private val attachmentService: AttachmentService) {

    @McpTool(
        name = "wiki_attachment_delete",
        description = "Delete attachment by UUID. Requires EDITOR or ADMIN role."
    )
    fun delete(@McpToolParam(description = "Attachment UUID") id: String): Map<String, String> {
        val parsedId = parseUuid(id)
        attachmentService.delete(parsedId, currentUsername())
        return mapOf("status" to "deleted", "id" to parsedId.toString())
    }
}
