package com.mdwiki.mcp

import com.mdwiki.dto.CreatePageRequest
import com.mdwiki.service.PageService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class WikiCreateTool(private val pageService: PageService) {

    @McpTool(name = "wiki_create", description = "Create a new wiki page. Requires EDITOR or ADMIN role.")
    fun create(
        @McpToolParam(description = "URL-friendly slug for the page") slug: String,
        @McpToolParam(description = "Page title") title: String,
        @McpToolParam(description = "Page content in markdown format") contentMd: String
    ): Map<String, Any?> {
        val username = SecurityContextHolder.getContext().authentication?.name
            ?: throw IllegalStateException("Not authenticated")
        val page = pageService.create(CreatePageRequest(slug = slug, title = title, contentMd = contentMd), username)
        return mapOf("slug" to page.slug, "title" to page.title, "createdAt" to page.createdAt.toString())
    }
}
