package com.mdwiki.mcp

import com.mdwiki.dto.UpdatePageRequest
import com.mdwiki.mcp.McpSupport.currentUsername
import com.mdwiki.mcp.McpSupport.parseUuid
import com.mdwiki.service.PageService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiMoveDocumentTool(private val pageService: PageService) {

    @McpTool(
        name = "wiki_move_document",
        description = "Move a document to another folder or to root. Requires EDITOR or ADMIN role."
    )
    fun move(
        @McpToolParam(description = "Slug of the page to move") slug: String,
        @McpToolParam(description = "Destination folder UUID. Omit and set moveToRoot=true to move to root.", required = false) destinationFolderId: String?,
        @McpToolParam(description = "Set true to move page to root folder.", required = false) moveToRoot: Boolean?
    ): Map<String, Any?> {
        val username = currentUsername()

        val destinationProvided = !destinationFolderId.isNullOrBlank()
        val toRoot = moveToRoot == true
        require(destinationProvided || toRoot) {
            "Provide destinationFolderId or set moveToRoot=true"
        }
        require(!(destinationProvided && toRoot)) {
            "Use either destinationFolderId or moveToRoot=true, not both"
        }

        val request = if (toRoot) {
            UpdatePageRequest(clearFolder = true)
        } else {
            UpdatePageRequest(folderId = parseUuid(destinationFolderId!!))
        }
        val page = pageService.update(slug, request, username)

        return mapOf(
            "slug" to page.slug,
            "title" to page.title,
            "folderId" to page.folderId?.toString(),
            "updatedAt" to page.updatedAt.toString()
        )
    }
}
