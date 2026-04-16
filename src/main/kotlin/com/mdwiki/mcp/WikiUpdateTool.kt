package com.mdwiki.mcp

import com.mdwiki.dto.UpdatePageRequest
import com.mdwiki.service.PageService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class WikiUpdateTool(private val pageService: PageService) {

    @McpTool(
        name = "wiki_update",
        description = "Update an existing wiki page's content. Requires EDITOR or ADMIN role."
    )
    fun update(
        @McpToolParam(description = "Slug of the page to update") slug: String,
        @McpToolParam(description = "New page title", required = false) title: String?,
        @McpToolParam(description = "New page content in markdown", required = false) contentMd: String?
    ): Map<String, Any?> {
        val username = SecurityContextHolder.getContext().authentication?.name
            ?: throw IllegalStateException("Not authenticated")
        val page = pageService.update(slug, UpdatePageRequest(title = title, contentMd = contentMd), username)
        return mapOf("slug" to page.slug, "title" to page.title, "updatedAt" to page.updatedAt.toString())
    }
}
