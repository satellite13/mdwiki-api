package com.mdwiki.rag

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

class OpenAiCompatibleEmbeddingTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var embedding: OpenAiCompatibleEmbedding
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        val baseUrl = mockServer.url("/v1").toString()
        embedding = OpenAiCompatibleEmbedding(
            baseUrl = baseUrl,
            model = "test-model",
            apiKey = "test-key",
            dimension = 3,
            webClientBuilder = WebClient.builder()
        )
    }

    @AfterEach
    fun tearDown() { mockServer.shutdown() }

    @Test
    fun `embed sends correct request and parses response`() {
        val responseBody = mapOf("data" to listOf(mapOf("embedding" to listOf(0.1, 0.2, 0.3), "index" to 0)))
        mockServer.enqueue(MockResponse().setBody(objectMapper.writeValueAsString(responseBody)).setHeader("Content-Type", "application/json"))

        val result = embedding.embed("hello world")
        assertEquals(3, result.size)
        assertEquals(0.1f, result[0], 0.001f)

        val request = mockServer.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.contains("/embeddings"))
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
        val body = objectMapper.readTree(request.body.readUtf8())
        assertEquals("test-model", body["model"].asText())
    }

    @Test
    fun `embed handles batch request`() {
        val responseBody = mapOf("data" to listOf(
            mapOf("embedding" to listOf(0.1, 0.2, 0.3), "index" to 0),
            mapOf("embedding" to listOf(0.4, 0.5, 0.6), "index" to 1)
        ))
        mockServer.enqueue(MockResponse().setBody(objectMapper.writeValueAsString(responseBody)).setHeader("Content-Type", "application/json"))
        val result = embedding.embed(listOf("text1", "text2"))
        assertEquals(2, result.size)
    }

    @Test
    fun `embed parses base64 int8 embeddings`() {
        val encoded = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))
        val responseBody = mapOf("data" to listOf(mapOf("embedding" to encoded, "index" to 0)))
        mockServer.enqueue(MockResponse().setBody(objectMapper.writeValueAsString(responseBody)).setHeader("Content-Type", "application/json"))

        val result = embedding.embed("hello world")
        assertEquals(3, result.size)
        assertEquals(1f, result[0], 0.001f)
        assertEquals(2f, result[1], 0.001f)
        assertEquals(3f, result[2], 0.001f)
    }

    @Test
    fun `embed parses base64 float32 embeddings`() {
        val bytes = ByteBuffer.allocate(12)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(0.25f)
            .putFloat(0.5f)
            .putFloat(0.75f)
            .array()
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val responseBody = mapOf("data" to listOf(mapOf("embedding" to encoded, "index" to 0)))
        mockServer.enqueue(MockResponse().setBody(objectMapper.writeValueAsString(responseBody)).setHeader("Content-Type", "application/json"))

        val result = embedding.embed("hello world")
        assertEquals(3, result.size)
        assertEquals(0.25f, result[0], 0.0001f)
        assertEquals(0.5f, result[1], 0.0001f)
        assertEquals(0.75f, result[2], 0.0001f)
    }

    @Test
    fun `dimension returns configured value`() { assertEquals(3, embedding.dimension()) }
}
