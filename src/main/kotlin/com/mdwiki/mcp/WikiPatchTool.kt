package com.mdwiki.mcp

import com.mdwiki.dto.PatchPageRequest
import com.mdwiki.mcp.McpSupport.currentUsername
import com.mdwiki.mcp.McpSupport.parseInstant
import com.mdwiki.service.PageService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiPatchTool(private val pageService: PageService) {

    @McpTool(
        name = "wiki_patch",
        description = "Replace an exact markdown fragment. Prefer wiki_patch_section when you have a section key. Optional sectionKey limits the search to that section. oldText must match exactly once unless replaceAll=true."
    )
    fun patch(
        @McpToolParam(description = "Slug of the page to patch") slug: String,
        @McpToolParam(description = "Exact existing fragment to replace (must not be blank)") oldText: String,
        @McpToolParam(description = "Replacement text") newText: String,
        @McpToolParam(description = "ISO-8601 updatedAt from wiki_read; request fails with conflict if the page changed")
        expectedUpdatedAt: String,
        @McpToolParam(description = "If true, replace every exact match. Default false (fails when oldText matches more than once).", required = false)
        replaceAll: Boolean?,
        @McpToolParam(description = "Optional stable section key from wiki_map; oldText is searched only inside that section", required = false)
        sectionKey: String?
    ): Map<String, Any?> {
        val result = pageService.patch(
            slug,
            PatchPageRequest(
                oldText = oldText,
                newText = newText,
                expectedUpdatedAt = parseInstant(expectedUpdatedAt),
                replaceAll = replaceAll ?: false,
                sectionKey = sectionKey?.takeIf { it.isNotBlank() }
            ),
            currentUsername()
        )
        return mapOf(
            "slug" to result.slug,
            "title" to result.title,
            "replacements" to result.replacements,
            "contentLength" to (result.contentMd?.length ?: 0),
            "previousUpdatedAt" to result.previousUpdatedAt.toString(),
            "updatedAt" to result.updatedAt.toString()
        )
    }
}
