package com.mdwiki.rag

import org.springframework.web.reactive.function.client.WebClient
import org.springframework.http.MediaType

class OpenAiCompatibleEmbedding(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
    private val dimension: Int,
    webClientBuilder: WebClient.Builder
) : EmbeddingProvider {

    private val webClient: WebClient = webClientBuilder.baseUrl(baseUrl.trimEnd('/')).build()

    override fun embed(texts: List<String>): List<FloatArray> {
        val requestBody = mapOf("model" to model, "input" to texts)
        val response = webClient.post()
            .uri("/embeddings")
            .contentType(MediaType.APPLICATION_JSON)
            .headers { if (apiKey.isNotBlank()) it.setBearerAuth(apiKey) }
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(EmbeddingResponse::class.java)
            .block() ?: throw RuntimeException("Empty response from embedding API")
        return response.data.sortedBy { it.index }.map { datum -> datum.embedding.map { it.toFloat() }.toFloatArray() }
    }

    override fun dimension(): Int = dimension

    data class EmbeddingResponse(val data: List<EmbeddingDatum>)
    data class EmbeddingDatum(val embedding: List<Double>, val index: Int)
}
