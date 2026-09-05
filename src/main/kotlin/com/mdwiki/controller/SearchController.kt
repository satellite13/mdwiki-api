package com.mdwiki.controller

import com.mdwiki.dto.RagSearchResult
import com.mdwiki.dto.SearchResult
import com.mdwiki.service.SearchService
import com.mdwiki.service.ExtractiveAnswerService
import com.mdwiki.dto.AnswerRequest
import com.mdwiki.dto.AnswerResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/search")
class SearchController(
    private val searchService: SearchService,
    private val answerService: ExtractiveAnswerService
) {

    @GetMapping
    fun search(@RequestParam q: String): List<SearchResult> = searchService.search(q)

    @GetMapping("/rag")
    fun searchRag(
        @RequestParam q: String,
        @RequestParam(defaultValue = "10") topK: Int
    ): List<RagSearchResult> = searchService.ragSearch(q, topK)

    @PostMapping("/answer")
    fun answer(@RequestBody request: AnswerRequest): AnswerResponse =
        answerService.answer(request.question, request.topK)
}
