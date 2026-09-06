package com.mdwiki.mcp

import com.mdwiki.dto.UpdateFolderRequest
import com.mdwiki.mcp.McpSupport.parseUuid
import com.mdwiki.mcp.McpSupport.currentUsername
import com.mdwiki.service.FolderService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiFolderRenameTool(private val folderService: FolderService) {

    @McpTool(
        name = "wiki_folder_rename",
        description = "Rename a wiki directory. The new name must be unique among sibling folders. Moves the folder on disk and updates nested page file paths. Requires EDITOR or ADMIN role."
    )
    fun rename(
        @McpToolParam(description = "Folder UUID to rename") folderId: String,
        @McpToolParam(description = "New directory name") name: String
    ): Map<String, Any?> {
        val parsedFolderId = parseUuid(folderId)
        val renamed = folderService.rename(parsedFolderId, UpdateFolderRequest(name = name), currentUsername())
        return mapOf(
            "id" to renamed.id.toString(),
            "name" to renamed.name,
            "parentId" to renamed.parentId?.toString(),
            "status" to "renamed"
        )
    }
}
