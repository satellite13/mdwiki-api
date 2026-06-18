package com.mdwiki.controller

import com.mdwiki.dto.RagSearchResult
import com.mdwiki.dto.SearchResult
import com.mdwiki.service.SearchService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/search")
class SearchController(private val searchService: SearchService) {

    @GetMapping
    fun search(@RequestParam q: String): List<SearchResult> = searchService.search(q)

    @GetMapping("/rag")
    fun searchRag(
        @RequestParam q: String,
        @RequestParam(defaultValue = "10") topK: Int
    ): List<RagSearchResult> = searchService.ragSearch(q, topK)
}
