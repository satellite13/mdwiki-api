package com.mdwiki.mcp

import com.mdwiki.service.PageService
import com.mdwiki.service.usecase.DeletePageUseCase
import com.mdwiki.mcp.McpSupport.currentUsername
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiDeleteTool(private val pageService: PageService) {

    @McpTool(name = "wiki_delete", description = "Delete a wiki page by slug. Requires EDITOR or ADMIN role.")
    fun delete(
        @McpToolParam(description = "Slug of the page to delete") slug: String,
        @McpToolParam(description = "Delete mode: SOFT or HARD") mode: String? = null
    ): Map<String, String> {
        val deleteMode = runCatching {
            DeletePageUseCase.DeleteMode.valueOf((mode ?: "SOFT").uppercase())
        }.getOrDefault(DeletePageUseCase.DeleteMode.SOFT)
        pageService.delete(slug, deleteMode, currentUsername())
        return mapOf("status" to "deleted", "slug" to slug)
    }
}
