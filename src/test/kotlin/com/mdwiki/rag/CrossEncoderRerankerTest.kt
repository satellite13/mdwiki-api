package com.mdwiki.rag

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CrossEncoderRerankerTest {

    @Test
    fun `rerank returns items sorted by score descending`() {
        val reranker = object : Reranker {
            override fun score(query: String, documents: List<String>): List<Float> {
                return documents.mapIndexed { index, _ -> index.toFloat() / documents.size }
            }
        }
        val docs = listOf("low", "medium", "high")
        val scores = reranker.score("test query", docs)
        val reranked = docs.zip(scores).sortedByDescending { it.second }
        assertEquals("high", reranked[0].first)
        assertEquals("medium", reranked[1].first)
        assertEquals("low", reranked[2].first)
    }

    @Test
    fun `crossEncoderReranker returns uniform scores when model not loaded`() {
        val reranker = CrossEncoderReranker()
        val scores = reranker.score("query", listOf("doc1", "doc2", "doc3"))
        assertEquals(3, scores.size)
        scores.forEach { assertEquals(0.5f, it) }
    }

    /**
     * Интеграционный тест: поднимает настоящую модель + tokenizer из classpath и проверяет,
     * что cross-encoder различает релевантные и нерелевантные документы. Скрытая задача —
     * убедиться, что HF-токенайзер действительно подключён (без него MS MARCO возвращает шум).
     */
    @Test
    fun `real model ranks relevant documents higher than irrelevant`() {
        val reranker = CrossEncoderReranker()
        injectField(reranker, "configuredModelPath", "")
        injectField(reranker, "configuredTokenizerPath", "")
        injectField(reranker, "maxLength", 512)
        reranker.init()
        assertTrue(reranker.isLoaded(), "Cross-encoder model/tokenizer must load from classpath for this test")

        val query = "how many people live in Berlin?"
        val relevant = "Berlin is the capital of Germany with about 3.7 million inhabitants."
        val irrelevant = "The cat sat on the mat and watched the sun rise over the hills."

        val scores = reranker.score(query, listOf(relevant, irrelevant))
        assertEquals(2, scores.size)
        scores.forEach { assertTrue(it in 0f..1f, "score must be in [0,1] after sigmoid, got $it") }

        val relScore = scores[0]
        val irrScore = scores[1]
        assertTrue(
            relScore > irrScore + 0.1f,
            "Relevant score ($relScore) must exceed irrelevant ($irrScore) by a clear margin"
        )
    }

    private fun injectField(target: Any, name: String, value: Any) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }
}
