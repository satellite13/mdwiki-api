package com.mdwiki.mcp

import com.mdwiki.service.PageService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

@Component
class WikiBacklinksTool(private val pageService: PageService) {

    @Tool(name = "wiki_backlinks", description = "Get all pages that link to a given page.")
    fun backlinks(@ToolParam(description = "Page slug to find backlinks for") slug: String): List<Map<String, String>> {
        return pageService.getBacklinks(slug).map { mapOf("slug" to it.slug, "title" to it.title) }
    }
}
