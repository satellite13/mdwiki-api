package com.mdwiki.service

import com.mdwiki.rag.RagService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ExtractiveAnswerServiceTest {
    private val rag: RagService = mock()
    private val service = ExtractiveAnswerService(rag)

    @Test
    fun `every sourced paragraph has citation and cleaned quote`() {
        whenever(rag.search("question", 9)).thenReturn(listOf(
            RagService.SearchResult("Exact λ quote with [[Page|alias]]", "Heading", "Page", "page", 0.9)
        ))

        val answer = service.answer("question", 3)

        assertThat(answer.grounded).isTrue()
        assertThat(answer.citations.single().quote).isEqualTo("Exact λ quote with alias")
        assertThat(answer.answerMd).contains("Exact λ quote with alias [1]")
    }

    @Test
    fun `empty retrieval is explicitly ungrounded`() {
        whenever(rag.search("missing", 15)).thenReturn(emptyList())
        val answer = service.answer("missing", 5)
        assertThat(answer.grounded).isFalse()
        assertThat(answer.answerMd).isEmpty()
        assertThat(answer.citations).isEmpty()
    }

    @Test
    fun `filters weak hits relative to the best score`() {
        val filtered = service.filterByRelevance(listOf(
            RagService.SearchResult("strong", null, "A", "a", 0.9),
            RagService.SearchResult("noise", null, "B", "b", 0.1)
        ))
        assertThat(filtered.map { it.pageSlug }).containsExactly("a")
    }

    @Test
    fun `diversifies across pages before filling extras`() {
        val hits = listOf(
            RagService.SearchResult("a1", null, "A", "a", 0.95),
            RagService.SearchResult("a2", null, "A", "a", 0.94),
            RagService.SearchResult("b1", null, "B", "b", 0.93),
            RagService.SearchResult("c1", null, "C", "c", 0.92)
        )
        assertThat(service.diversify(hits, 3).map { it.chunkText })
            .containsExactly("a1", "b1", "c1")
    }
}
