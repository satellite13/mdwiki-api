package com.mdwiki.service

import com.mdwiki.dto.AnswerCitation
import com.mdwiki.dto.AnswerResponse
import com.mdwiki.error.BadRequestException
import com.mdwiki.rag.RagService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ExtractiveAnswerService(private val rag: RagService) {
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
        val citations = hits.mapIndexed { index, hit ->
            AnswerCitation(
                id = index + 1,
                pageSlug = hit.pageSlug,
                pageTitle = hit.pageTitle,
                sectionKey = null,
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
