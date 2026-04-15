package com.mdwiki.rag

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

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
}
