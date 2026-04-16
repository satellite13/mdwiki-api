package com.mdwiki.service

import com.mdwiki.dto.SearchResult
import com.mdwiki.mapper.headlineToSearchSnippet
import com.mdwiki.repository.PageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SearchService(private val pageRepository: PageRepository) {

    @Transactional(readOnly = true)
    fun search(query: String, limit: Int = 20): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        return pageRepository.searchWithHeadline(query, limit).map { hit ->
            SearchResult(
                pageId = hit.getId(),
                slug = hit.getSlug(),
                title = hit.getTitle(),
                snippet = headlineToSearchSnippet(hit.getHeadline())
            )
        }
    }
}
