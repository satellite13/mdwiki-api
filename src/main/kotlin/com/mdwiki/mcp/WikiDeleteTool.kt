package com.mdwiki.mcp

import com.mdwiki.service.PageService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiDeleteTool(private val pageService: PageService) {

    @McpTool(name = "wiki_delete", description = "Delete a wiki page by slug. Requires EDITOR or ADMIN role.")
    fun delete(@McpToolParam(description = "Slug of the page to delete") slug: String): Map<String, String> {
        pageService.delete(slug)
        return mapOf("status" to "deleted", "slug" to slug)
    }
}
