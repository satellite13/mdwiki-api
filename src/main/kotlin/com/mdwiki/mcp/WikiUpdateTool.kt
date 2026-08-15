package com.mdwiki.mcp

import com.mdwiki.dto.UpdatePageRequest
import com.mdwiki.mcp.McpSupport.currentUsername
import com.mdwiki.mcp.McpSupport.parseInstant
import com.mdwiki.service.PageService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiUpdateTool(private val pageService: PageService) {

    @McpTool(
        name = "wiki_update",
        description = "Update page title, slug, or replace the entire markdown. Requires EDITOR or ADMIN. For a fragment use wiki_patch. When changing contentMd, pass expectedUpdatedAt from wiki_read to avoid overwriting concurrent edits."
    )
    fun update(
        @McpToolParam(description = "Slug of the page to update (preserved unless newSlug is provided)") slug: String,
        @McpToolParam(description = "New page title", required = false) title: String?,
        @McpToolParam(description = "Full page content in markdown. Prefer wiki_patch for partial edits.", required = false) contentMd: String?,
        @McpToolParam(description = "New slug for the page (optional, only if you want to rename the URL)", required = false) newSlug: String?,
        @McpToolParam(description = "ISO-8601 updatedAt from wiki_read; conflict if the page changed", required = false)
        expectedUpdatedAt: String?
    ): Map<String, Any?> {
        val username = currentUsername()
        val page = pageService.update(
            slug,
            UpdatePageRequest(
                title = title,
                contentMd = contentMd,
                slug = newSlug,
                expectedUpdatedAt = expectedUpdatedAt?.let(::parseInstant)
            ),
            username
        )
        return mapOf("slug" to page.slug, "title" to page.title, "updatedAt" to page.updatedAt.toString())
    }
}
