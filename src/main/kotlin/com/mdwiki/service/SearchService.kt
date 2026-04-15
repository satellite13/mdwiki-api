package com.mdwiki.service

import com.mdwiki.dto.SearchResult
import com.mdwiki.repository.PageRepository
import org.springframework.stereotype.Service

@Service
class SearchService(private val pageRepository: PageRepository) {

    fun search(query: String, limit: Int = 20): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        return pageRepository.fullTextSearch(query, limit).map { page ->
            val snippet = page.contentMd
                ?.take(200)
                ?.let { if (page.contentMd!!.length > 200) "$it..." else it }
                ?: ""
            SearchResult(
                pageId = page.id!!,
                slug = page.slug,
                title = page.title,
                snippet = snippet
            )
        }
    }
}
