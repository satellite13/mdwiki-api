package com.mdwiki.rag

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

class OpenAiCompatibleEmbedding(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
    private val dimension: Int,
    webClientBuilder: WebClient.Builder
) : EmbeddingProvider {

    private val log = LoggerFactory.getLogger(OpenAiCompatibleEmbedding::class.java)

    private val webClient: WebClient = webClientBuilder.baseUrl(baseUrl.trimEnd('/')).build()

    override fun embed(texts: List<String>): List<FloatArray> {
        val requestBody = mapOf("model" to model, "input" to texts)
        val response = try {
            webClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .headers { if (apiKey.isNotBlank()) it.setBearerAuth(apiKey) }
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(EmbeddingResponse::class.java)
                .block() ?: throw RuntimeException("Empty response from embedding API ($baseUrl, model=$model)")
        } catch (e: WebClientResponseException) {
            // Upstream вернул не-2xx. Логируем краткое тело, чтобы было видно, что именно
            // отдал провайдер (например, «model not supported» или "error": { ... } от OpenRouter).
            log.error(
                "Embedding HTTP {} from {} (model={}): {}",
                e.statusCode.value(),
                baseUrl,
                model,
                truncate(e.responseBodyAsString)
            )
            throw RuntimeException("Embedding HTTP ${e.statusCode.value()} from $baseUrl", e)
        }

        val data = response.data
        if (data.isNullOrEmpty()) {
            // 2xx, но тело не похоже на OpenAI-embeddings (нет поля data или оно пустое). Тоже логируем.
            log.error(
                "Embedding response from {} (model={}) has no `data` field. Raw body snippet: {}",
                baseUrl,
                model,
                truncate(fetchRawBody(requestBody))
            )
            throw RuntimeException("Malformed embedding response from $baseUrl: missing/empty `data`")
        }

        return data.sortedBy { it.index }
            .map { datum -> datum.embedding.map { it.toFloat() }.toFloatArray() }
    }

    override fun dimension(): Int = dimension

    /**
     * Повторно читает сырое тело ответа — используется только для диагностики,
     * когда уже известно, что Jackson распарсил ответ, но в нём нет `data`.
     * В норме не вызывается, так что дополнительный RTT допустим.
     */
    private fun fetchRawBody(requestBody: Map<String, Any>): String {
        return try {
            webClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .headers { if (apiKey.isNotBlank()) it.setBearerAuth(apiKey) }
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String::class.java)
                .block() ?: "<null>"
        } catch (e: Exception) {
            "<failed to re-fetch body: ${e.message}>"
        }
    }

    private fun truncate(s: String?, limit: Int = 512): String {
        if (s == null) return "<null>"
        return if (s.length <= limit) s else s.substring(0, limit) + "…(+${s.length - limit})"
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class EmbeddingResponse(val data: List<EmbeddingDatum>? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class EmbeddingDatum(val embedding: List<Double>, val index: Int)
}
