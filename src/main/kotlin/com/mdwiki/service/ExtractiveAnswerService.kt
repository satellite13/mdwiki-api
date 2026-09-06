package com.mdwiki.service

import com.mdwiki.dto.AnswerCitation
import com.mdwiki.dto.AnswerResponse
import com.mdwiki.error.BadRequestException
import com.mdwiki.rag.RagService
import com.mdwiki.repository.PageRepository
import com.mdwiki.util.CitationQuoteNormalizer
import com.mdwiki.util.SectionAnchorResolver
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ExtractiveAnswerService(
    private val rag: RagService,
    private val pages: PageRepository? = null
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun answer(question: String, topK: Int): AnswerResponse {
        val normalized = question.trim()
        if (normalized.length !in 1..1000) throw BadRequestException("question must be 1..1000 characters")
        if (topK !in 1..20) throw BadRequestException("topK must be 1..20")
        val retrievalLimit = (topK * 3).coerceAtMost(20)
        val hits = try {
            rag.search(normalized, retrievalLimit)
        } catch (error: Exception) {
            log.warn("Extractive retrieval failed: {}", error.javaClass.simpleName)
            emptyList()
        }.distinctBy { Triple(it.pageSlug, it.sectionHeading, it.chunkText) }

        val pagesBySlug = pages?.findAllBySlugIn(hits.map { it.pageSlug }.distinct())
            ?.filter { it.deletedAt == null }
            ?.associateBy { it.slug }
            .orEmpty()

        val eligible = hits
            .filter { hit -> pages == null || pagesBySlug.containsKey(hit.pageSlug) }
            .let { filterByRelevance(it) }
            .let { diversify(it, topK) }

        val citations = eligible.mapIndexed { index, hit ->
            val page = pagesBySlug[hit.pageSlug]
            AnswerCitation(
                id = index + 1,
                pageSlug = hit.pageSlug,
                pageTitle = hit.pageTitle,
                sectionKey = page?.contentMd?.let {
                    SectionAnchorResolver.resolveKey(it, hit.sectionHeading, hit.chunkText)
                },
                sectionHeading = hit.sectionHeading,
                quote = CitationQuoteNormalizer.normalize(hit.chunkText),
                score = hit.score
            )
        }

        return AnswerResponse(
            answerMd = citations.joinToString("\n\n") { "${it.quote} [${it.id}]" },
            citations = citations,
            grounded = citations.isNotEmpty()
        )
    }

    /** Drop weak noise relative to the best hit (and an absolute floor). */
    internal fun filterByRelevance(hits: List<RagService.SearchResult>): List<RagService.SearchResult> {
        if (hits.isEmpty()) return hits
        val best = hits.maxOf { it.score }
        if (best <= 0.0) return emptyList()
        val floor = maxOf(ABSOLUTE_MIN_SCORE, best * RELATIVE_SCORE_RATIO)
        return hits.filter { it.score >= floor }.sortedByDescending { it.score }
    }

    /** Prefer different pages, then fill remaining slots with next-best chunks. */
    internal fun diversify(
        hits: List<RagService.SearchResult>,
        topK: Int
    ): List<RagService.SearchResult> {
        if (hits.size <= topK) return hits
        val selected = mutableListOf<RagService.SearchResult>()
        val usedPages = mutableSetOf<String>()
        for (hit in hits) {
            if (selected.size >= topK) break
            if (usedPages.add(hit.pageSlug)) selected += hit
        }
        for (hit in hits) {
            if (selected.size >= topK) break
            if (selected.none { it.pageSlug == hit.pageSlug && it.chunkText == hit.chunkText }) {
                selected += hit
            }
        }
        return selected.take(topK)
    }

    companion object {
        private const val ABSOLUTE_MIN_SCORE = 0.12
        private const val RELATIVE_SCORE_RATIO = 0.35
    }
}
