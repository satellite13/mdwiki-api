package com.mdwiki.mcp

import com.mdwiki.dto.CreateFolderRequest
import com.mdwiki.service.FolderService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.UUID

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
        val username = SecurityContextHolder.getContext().authentication?.name
            ?: throw IllegalStateException("Not authenticated")
        val parsedParentId = parentFolderId?.takeIf { it.isNotBlank() }?.let(::parseUuid)
        val folder = folderService.create(CreateFolderRequest(name = name, parentId = parsedParentId), username)
        return mapOf(
            "id" to folder.id.toString(),
            "name" to folder.name,
            "parentId" to folder.parentId?.toString(),
            "createdAt" to folder.createdAt.toString()
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
