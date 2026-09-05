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
    fun `every sourced paragraph has citation and exact quote`() {
        whenever(rag.search("question", 3)).thenReturn(listOf(
            RagService.SearchResult("Exact λ quote", "Heading", "Page", "page", 0.9)
        ))

        val answer = service.answer("question", 3)

        assertThat(answer.grounded).isTrue()
        assertThat(answer.answerMd).isEqualTo("Exact λ quote [1]")
        assertThat(answer.citations.single().quote).isEqualTo("Exact λ quote")
    }

    @Test
    fun `empty retrieval is explicitly ungrounded`() {
        whenever(rag.search("missing", 5)).thenReturn(emptyList())
        val answer = service.answer("missing", 5)
        assertThat(answer.grounded).isFalse()
        assertThat(answer.answerMd).isEmpty()
        assertThat(answer.citations).isEmpty()
    }
}
