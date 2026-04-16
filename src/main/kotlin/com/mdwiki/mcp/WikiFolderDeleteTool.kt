package com.mdwiki.mcp

import com.mdwiki.service.FolderService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class WikiFolderDeleteTool(private val folderService: FolderService) {

    @McpTool(
        name = "wiki_folder_delete",
        description = "Delete a wiki directory by folder UUID. Pages from the deleted subtree are moved to root. Requires EDITOR or ADMIN role."
    )
    fun delete(@McpToolParam(description = "Folder UUID to delete") folderId: String): Map<String, String> {
        val parsedFolderId = parseUuid(folderId)
        folderService.delete(parsedFolderId)
        return mapOf("status" to "deleted", "folderId" to parsedFolderId.toString())
    }

    private fun parseUuid(raw: String): UUID {
        return try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid UUID: $raw")
        }
    }
}
