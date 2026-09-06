package com.mdwiki.mcp

import com.mdwiki.service.FolderService
import com.mdwiki.dto.FolderDeletePageAction
import com.mdwiki.mcp.McpSupport.parseUuid
import com.mdwiki.mcp.McpSupport.currentUsername
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiFolderDeleteTool(private val folderService: FolderService) {

    @McpTool(
        name = "wiki_folder_delete",
        description = "Delete a wiki directory by folder UUID. Use pageAction=DELETE to remove pages in the subtree, or pageAction=MOVE_TO_ROOT to keep pages at the wiki root. Requires EDITOR or ADMIN role."
    )
    fun delete(
        @McpToolParam(description = "Folder UUID to delete") folderId: String,
        @McpToolParam(
            description = "What to do with pages in the deleted subtree: DELETE (default) or MOVE_TO_ROOT",
            required = false
        ) pageAction: String?
    ): Map<String, String> {
        val parsedFolderId = parseUuid(folderId)
        val parsedPageAction = parsePageAction(pageAction)
        folderService.delete(parsedFolderId, currentUsername(), parsedPageAction)
        return mapOf(
            "status" to "deleted",
            "folderId" to parsedFolderId.toString(),
            "pageAction" to parsedPageAction.name
        )
    }

    private fun parsePageAction(raw: String?): FolderDeletePageAction {
        if (raw.isNullOrBlank()) return FolderDeletePageAction.DELETE
        return try {
            FolderDeletePageAction.valueOf(raw.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid pageAction: $raw (expected DELETE or MOVE_TO_ROOT)")
        }
    }
}
