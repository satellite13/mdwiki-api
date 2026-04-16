package com.mdwiki.service

import com.mdwiki.dto.SearchResult
import com.mdwiki.mapper.toSearchResult
import com.mdwiki.repository.PageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SearchService(private val pageRepository: PageRepository) {

    @Transactional(readOnly = true)
    fun search(query: String, limit: Int = 20): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        return pageRepository.fullTextSearch(query, limit).map { it.toSearchResult() }
    }
}
