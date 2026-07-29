package com.mdwiki.mcp

import com.mdwiki.dto.CreatePageRequest
import com.mdwiki.mcp.McpSupport.currentUsername
import com.mdwiki.mcp.McpSupport.parseUuid
import com.mdwiki.service.PageService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiCreateTool(private val pageService: PageService) {

    @McpTool(name = "wiki_create", description = "Create a new wiki page. Requires EDITOR or ADMIN role.")
    fun create(
        @McpToolParam(description = "URL-friendly slug for the page") slug: String,
        @McpToolParam(description = "Page title") title: String,
        @McpToolParam(description = "Page content in markdown format") contentMd: String,
        @McpToolParam(description = "Optional parent folder UUID. If omitted, page is created in root.", required = false)
        folderId: String? = null
    ): Map<String, Any?> {
        val username = currentUsername()
        val parsedFolderId = folderId?.takeIf { it.isNotBlank() }?.let(::parseUuid)
        val page = pageService.create(
            CreatePageRequest(slug = slug, title = title, contentMd = contentMd, folderId = parsedFolderId),
            username
        )
        return mapOf(
            "slug" to page.slug,
            "title" to page.title,
            "folderId" to page.folderId?.toString(),
            "createdAt" to page.createdAt.toString()
        )
    }
}
