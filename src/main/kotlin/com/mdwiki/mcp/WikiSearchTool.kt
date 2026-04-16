package com.mdwiki.mcp

import com.mdwiki.rag.RagService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiSearchTool(private val ragService: RagService) {

    @McpTool(
        name = "wiki_search",
        description = "Search wiki pages using RAG (semantic + full-text search). Returns relevant text chunks with page context."
    )
    fun search(
        @McpToolParam(description = "Search query text") query: String,
        @McpToolParam(description = "Maximum number of results to return", required = false) topK: Int?
    ): List<Map<String, Any?>> {
        return ragService.search(query, topK ?: 10).map { result ->
            mapOf(
                "pageSlug" to result.pageSlug,
                "pageTitle" to result.pageTitle,
                "sectionHeading" to result.sectionHeading,
                "chunkText" to result.chunkText,
                "score" to result.score
            )
        }
    }
}
