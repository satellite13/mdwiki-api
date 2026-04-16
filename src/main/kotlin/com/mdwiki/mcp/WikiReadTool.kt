package com.mdwiki.mcp

import com.mdwiki.service.PageService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiReadTool(private val pageService: PageService) {

    @McpTool(
        name = "wiki_read",
        description = "Read a wiki page by its slug. Returns full markdown content, tags, and metadata."
    )
    fun read(@McpToolParam(description = "Page slug (URL-friendly name)") slug: String): Map<String, Any?> {
        val page = pageService.findBySlug(slug)
        val backlinks = pageService.getBacklinks(slug)
        return mapOf(
            "slug" to page.slug, "title" to page.title, "contentMd" to page.contentMd,
            "frontmatterMeta" to page.frontmatterMeta,
            "tags" to page.tags,
            "backlinks" to backlinks.map { mapOf("slug" to it.slug, "title" to it.title) },
            "createdBy" to page.createdBy, "updatedBy" to page.updatedBy,
            "createdAt" to page.createdAt.toString(), "updatedAt" to page.updatedAt.toString()
        )
    }
}
