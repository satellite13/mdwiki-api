package com.mdwiki.mcp

import com.mdwiki.dto.CreateFolderRequest
import com.mdwiki.mcp.McpSupport.currentUsername
import com.mdwiki.mcp.McpSupport.parseUuid
import com.mdwiki.service.FolderService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiFolderCreateTool(private val folderService: FolderService) {

    @McpTool(
        name = "wiki_folder_create",
        description = "Create a wiki directory (folder). Optionally provide parent folder ID for nested directories. Requires EDITOR or ADMIN role."
    )
    fun create(
        @McpToolParam(description = "Directory name") name: String,
        @McpToolParam(description = "Parent folder UUID. If omitted, folder is created in root.", required = false) parentFolderId: String?
    ): Map<String, Any?> {
        val username = currentUsername()
        val parsedParentId = parentFolderId?.takeIf { it.isNotBlank() }?.let(::parseUuid)
        val folder = folderService.create(CreateFolderRequest(name = name, parentId = parsedParentId), username)
        return mapOf(
            "id" to folder.id.toString(),
            "name" to folder.name,
            "parentId" to folder.parentId?.toString(),
            "createdAt" to folder.createdAt.toString()
        )
    }
}
