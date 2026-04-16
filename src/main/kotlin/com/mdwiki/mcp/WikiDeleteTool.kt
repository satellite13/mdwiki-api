package com.mdwiki.mcp

import com.mdwiki.service.PageService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

@Component
class WikiDeleteTool(private val pageService: PageService) {

    @Tool(name = "wiki_delete", description = "Delete a wiki page by slug. Requires EDITOR or ADMIN role.")
    fun delete(@ToolParam(description = "Slug of the page to delete") slug: String): Map<String, String> {
        pageService.delete(slug)
        return mapOf("status" to "deleted", "slug" to slug)
    }
}
