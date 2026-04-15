package com.mdwiki.rag

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class OllamaEmbeddingTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var embedding: OllamaEmbedding
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        embedding = OllamaEmbedding(
            baseUrl = mockServer.url("/").toString().trimEnd('/'),
            model = "nomic-embed-text",
            dimension = 3,
            webClientBuilder = WebClient.builder()
        )
    }

    @AfterEach
    fun tearDown() { mockServer.shutdown() }

    @Test
    fun `embed sends correct request and parses response`() {
        mockServer.enqueue(MockResponse()
            .setBody(objectMapper.writeValueAsString(mapOf("embeddings" to listOf(listOf(0.1, 0.2, 0.3)))))
            .setHeader("Content-Type", "application/json"))
        val result = embedding.embed("hello")
        assertEquals(3, result.size)
        assertEquals(0.1f, result[0], 0.001f)
        val request = mockServer.takeRequest()
        assertTrue(request.path!!.contains("/api/embed"))
    }

    @Test
    fun `embed handles batch by calling per-text`() {
        for (i in 0..1) {
            mockServer.enqueue(MockResponse()
                .setBody(objectMapper.writeValueAsString(mapOf("embeddings" to listOf(listOf(0.1 * (i+1), 0.2, 0.3)))))
                .setHeader("Content-Type", "application/json"))
        }
        val result = embedding.embed(listOf("text1", "text2"))
        assertEquals(2, result.size)
        assertEquals(2, mockServer.requestCount)
    }
}
