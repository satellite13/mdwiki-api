package com.mdwiki.mcp

import com.mdwiki.dto.UpdatePageRequest
import com.mdwiki.service.PageService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class WikiUpdateTool(private val pageService: PageService) {

    @Tool(name = "wiki_update", description = "Update an existing wiki page's content. Requires EDITOR or ADMIN role.")
    fun update(
        @ToolParam(description = "Slug of the page to update") slug: String,
        @ToolParam(description = "New page title", required = false) title: String?,
        @ToolParam(description = "New page content in markdown", required = false) contentMd: String?
    ): Map<String, Any?> {
        val username = SecurityContextHolder.getContext().authentication.name
        val page = pageService.update(slug, UpdatePageRequest(title = title, contentMd = contentMd), username)
        return mapOf("slug" to page.slug, "title" to page.title, "updatedAt" to page.updatedAt.toString())
    }
}
