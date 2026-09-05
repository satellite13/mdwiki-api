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
    fun search(query: String, limit: Int = 20, tags: List<String> = emptyList()): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val normalizedTags = tags.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        val hits = if (normalizedTags.isEmpty()) pageRepository.searchWithHeadline(query, limit)
        else pageRepository.searchWithHeadlineAndTags(query, normalizedTags, normalizedTags.size, limit)
        val pagesById = pageRepository.findAllById(hits.map { it.getId() }).associateBy { it.id }
        return hits.map { hit ->
            SearchResult(
                pageId = hit.getId(),
                slug = hit.getSlug(),
                title = hit.getTitle(),
                snippet = headlineToSearchSnippet(hit.getHeadline()),
                updatedAt = hit.getUpdatedAt(),
                tags = pagesById[hit.getId()]?.tags?.map { it.name }?.sorted().orEmpty()
            )
        }
    }

    @Transactional(readOnly = true)
    fun ragSearch(query: String, topK: Int = 10, tags: List<String> = emptyList()): List<RagSearchResult> {
        if (query.isBlank()) return emptyList()
        val limit = topK.coerceIn(1, 50)
        val normalizedTags = tags.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        val hits = if (normalizedTags.isEmpty()) ragService.search(query, limit)
        else ragService.search(query, limit, normalizedTags)
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
