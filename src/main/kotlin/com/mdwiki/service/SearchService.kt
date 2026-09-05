package com.mdwiki.service

import com.mdwiki.dto.RagSearchResult
import com.mdwiki.dto.SearchResult
import com.mdwiki.mapper.headlineToSearchSnippet
import com.mdwiki.rag.RagService
import com.mdwiki.repository.PageRepository
import com.mdwiki.util.SectionAnchorResolver
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SearchService(
    private val pageRepository: PageRepository,
    private val ragService: RagService
) {

    @Transactional(readOnly = true)
    fun search(query: String, limit: Int = 20): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        return pageRepository.searchWithHeadline(query, limit).map { hit ->
            SearchResult(
                pageId = hit.getId(),
                slug = hit.getSlug(),
                title = hit.getTitle(),
                snippet = headlineToSearchSnippet(hit.getHeadline()),
                updatedAt = hit.getUpdatedAt()
            )
        }
    }

    @Transactional(readOnly = true)
    fun ragSearch(query: String, topK: Int = 10): List<RagSearchResult> {
        if (query.isBlank()) return emptyList()
        val limit = topK.coerceIn(1, 50)
        val hits = ragService.search(query, limit)
        if (hits.isEmpty()) return emptyList()

        val pagesBySlug = pageRepository.findAllBySlugIn(hits.map { it.pageSlug }.distinct())
            .filter { it.deletedAt == null }
            .associateBy { it.slug }

        return hits.map { hit ->
            val page = pagesBySlug[hit.pageSlug]
            RagSearchResult(
                chunkText = hit.chunkText,
                pageSlug = hit.pageSlug,
                pageTitle = hit.pageTitle,
                sectionHeading = hit.sectionHeading,
                snippet = headlineToSearchSnippet(hit.chunkText),
                score = hit.score,
                tags = page?.tags?.map { it.name }?.sorted().orEmpty(),
                sectionKey = page?.contentMd?.let { content ->
                    SectionAnchorResolver.resolveKey(content, hit.sectionHeading, hit.chunkText)
                },
                updatedAt = page?.updatedAt
            )
        }
    }
}
