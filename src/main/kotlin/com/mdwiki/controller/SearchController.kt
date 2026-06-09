package com.mdwiki.controller

import com.mdwiki.dto.RagSearchResult
import com.mdwiki.dto.SearchResult
import com.mdwiki.rag.RagService
import com.mdwiki.repository.PageRepository
import com.mdwiki.service.SearchService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/search")
class SearchController(
    private val searchService: SearchService,
    private val ragService: RagService,
    private val pageRepository: PageRepository
) {

    @GetMapping
    fun search(@RequestParam q: String): List<SearchResult> = searchService.search(q)

    @GetMapping("/rag")
    fun searchRag(
        @RequestParam q: String,
        @RequestParam(defaultValue = "10") topK: Int
    ): List<RagSearchResult> {
        val results = ragService.search(q, topK)
        if (results.isEmpty()) return emptyList()
        val slugs = results.map { it.pageSlug }.distinct()
        val tagsBySlug = pageRepository.findAllBySlugIn(slugs)
            .filter { it.deletedAt == null }
            .associate { page -> page.slug to page.tags.map { tag -> tag.name }.sorted() }
        return results.map { r ->
            RagSearchResult(
                chunkText = r.chunkText,
                sectionHeading = r.sectionHeading,
                pageTitle = r.pageTitle,
                pageSlug = r.pageSlug,
                score = r.score,
                snippet = r.chunkText.take(300).replace("\n", " "),
                tags = tagsBySlug[r.pageSlug] ?: emptyList()
            )
        }
    }
}
