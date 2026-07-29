package com.mdwiki.rag

import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

class OllamaEmbedding(
    private val baseUrl: String,
    private val model: String,
    private val dimension: Int,
    webClientBuilder: WebClient.Builder
) : EmbeddingProvider {

    private val webClient: WebClient = webClientBuilder.baseUrl(baseUrl.trimEnd('/')).build()

    // /api/embed принимает список текстов — один HTTP-вызов на батч вместо вызова на каждый чанк.
    override fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        val requestBody = mapOf("model" to model, "input" to texts)
        val response = webClient.post()
            .uri("/api/embed")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(OllamaEmbedResponse::class.java)
            .block() ?: throw RuntimeException("Empty response from Ollama")
        return response.embeddings.map { embedding -> embedding.map { it.toFloat() }.toFloatArray() }
    }

    override fun dimension(): Int = dimension
    data class OllamaEmbedResponse(val embeddings: List<List<Double>>)
}
