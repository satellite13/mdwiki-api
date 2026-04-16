package com.mdwiki.mcp

import com.mdwiki.service.PageService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

@Component
class WikiListTool(private val pageService: PageService) {

    @Tool(name = "wiki_list", description = "List all wiki pages. Optionally filter by tag.")
    fun list(@ToolParam(description = "Filter by tag name", required = false) tag: String?): List<Map<String, Any?>> {
        val pages = pageService.findAll()
        val filtered = if (tag != null) pages.filter { tag in it.tags } else pages
        return filtered.map { mapOf("slug" to it.slug, "title" to it.title, "tags" to it.tags, "updatedAt" to it.updatedAt.toString()) }
    }
}
