package com.mdwiki.service

import com.mdwiki.dto.AnswerCitation
import com.mdwiki.dto.AnswerResponse
import com.mdwiki.error.BadRequestException
import com.mdwiki.rag.RagService
import com.mdwiki.repository.PageRepository
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
        val hits = try {
            rag.search(normalized, topK)
        } catch (error: Exception) {
            log.warn("Extractive retrieval failed: {}", error.javaClass.simpleName)
            emptyList()
        }.distinctBy { Triple(it.pageSlug, it.sectionHeading, it.chunkText) }
        val pagesBySlug = pages?.findAllBySlugIn(hits.map { it.pageSlug }.distinct())
            ?.filter { it.deletedAt == null }
            ?.associateBy { it.slug }
            .orEmpty()
        val citations = hits.filter { hit -> pages == null || pagesBySlug.containsKey(hit.pageSlug) }
            .mapIndexed { index, hit ->
            val page = pagesBySlug[hit.pageSlug]
            AnswerCitation(
                id = index + 1,
                pageSlug = hit.pageSlug,
                pageTitle = hit.pageTitle,
                sectionKey = page?.contentMd?.let {
                    SectionAnchorResolver.resolveKey(it, hit.sectionHeading, hit.chunkText)
                },
                sectionHeading = hit.sectionHeading,
                quote = hit.chunkText,
                score = hit.score
            )
        }
        return AnswerResponse(
            answerMd = citations.joinToString("\n\n") { "${it.quote} [${it.id}]" },
            citations = citations,
            grounded = citations.isNotEmpty()
        )
    }
}
