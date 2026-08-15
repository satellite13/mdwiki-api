package com.mdwiki.mcp

import com.mdwiki.service.SearchService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiSearchTool(private val searchService: SearchService) {

    @McpTool(
        name = "wiki_search",
        description = "Search wiki pages using RAG (semantic + full-text search). Returns chunks with pageSlug, sectionKey (use with wiki_read / wiki_patch_section), and sectionHeading. Prefer sectionKey over heading when both are present."
    )
    fun search(
        @McpToolParam(description = "Search query text") query: String,
        @McpToolParam(description = "Maximum number of results to return", required = false) topK: Int?
    ): List<Map<String, Any?>> {
        return searchService.ragSearch(query, topK ?: 10).map { result ->
            mapOf(
                "pageSlug" to result.pageSlug,
                "pageTitle" to result.pageTitle,
                "sectionHeading" to result.sectionHeading,
                "sectionKey" to result.sectionKey,
                "chunkText" to result.chunkText,
                "score" to result.score
            )
        }
    }
}
