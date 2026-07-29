package com.mdwiki.mcp

import com.mdwiki.dto.UpdatePageRequest
import com.mdwiki.mcp.McpSupport.currentUsername
import com.mdwiki.service.PageService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiUpdateTool(private val pageService: PageService) {

    @McpTool(
        name = "wiki_update",
        description = "Update an existing wiki page's content. Requires EDITOR or ADMIN role. Slug is preserved unless explicitly provided."
    )
    fun update(
        @McpToolParam(description = "Slug of the page to update (preserved unless newSlug is provided)") slug: String,
        @McpToolParam(description = "New page title", required = false) title: String?,
        @McpToolParam(description = "New page content in markdown", required = false) contentMd: String?,
        @McpToolParam(description = "New slug for the page (optional, only if you want to rename the URL)", required = false) newSlug: String?
    ): Map<String, Any?> {
        val username = currentUsername()
        val page = pageService.update(slug, UpdatePageRequest(title = title, contentMd = contentMd, slug = newSlug), username)
        return mapOf("slug" to page.slug, "title" to page.title, "updatedAt" to page.updatedAt.toString())
    }
}
