package com.mdwiki.mcp

import com.mdwiki.dto.MoveFolderRequest
import com.mdwiki.mcp.McpSupport.parseUuid
import com.mdwiki.service.FolderService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiFolderMoveTool(private val folderService: FolderService) {

    @McpTool(
        name = "wiki_folder_move",
        description = "Move a directory with all nested folders and documents. Requires EDITOR or ADMIN role."
    )
    fun move(
        @McpToolParam(description = "Folder UUID to move") folderId: String,
        @McpToolParam(
            description = "Destination parent folder UUID. Omit and set moveToRoot=true to move folder to root.",
            required = false
        ) destinationParentFolderId: String?,
        @McpToolParam(description = "Set true to move folder to root.", required = false) moveToRoot: Boolean?
    ): Map<String, Any?> {
        val destinationProvided = !destinationParentFolderId.isNullOrBlank()
        val toRoot = moveToRoot == true
        require(destinationProvided || toRoot) {
            "Provide destinationParentFolderId or set moveToRoot=true"
        }
        require(!(destinationProvided && toRoot)) {
            "Use either destinationParentFolderId or moveToRoot=true, not both"
        }

        val parsedFolderId = parseUuid(folderId)
        val targetParentId = destinationParentFolderId?.takeIf { it.isNotBlank() }?.let(::parseUuid)
        val moved = folderService.move(parsedFolderId, MoveFolderRequest(parentId = if (toRoot) null else targetParentId))

        return mapOf(
            "id" to moved.id.toString(),
            "name" to moved.name,
            "parentId" to moved.parentId?.toString(),
            "status" to "moved"
        )
    }
}
