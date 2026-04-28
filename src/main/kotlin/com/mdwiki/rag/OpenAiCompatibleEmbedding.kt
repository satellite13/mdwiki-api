package com.mdwiki.rag

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

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
            .map { datum -> decodeEmbedding(datum.embedding, datum.index) }
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

    private fun decodeEmbedding(embeddingNode: Any?, index: Int): FloatArray {
        if (embeddingNode == null) {
            throw RuntimeException("Malformed embedding response from $baseUrl: `data[$index].embedding` is null")
        }

        if (embeddingNode is List<*>) {
            if (embeddingNode.isEmpty()) {
                throw RuntimeException("Malformed embedding response from $baseUrl: `data[$index].embedding` is empty array")
            }
            return embeddingNode.map { item ->
                when (item) {
                    is Number -> item.toFloat()
                    is String -> item.toFloatOrNull()
                    else -> null
                } ?: throw RuntimeException(
                    "Malformed embedding response from $baseUrl: `data[$index].embedding` contains non-numeric item"
                )
            }.toFloatArray()
        }

        if (embeddingNode is String) {
            return decodeBase64Embedding(embeddingNode, index)
        }

        throw RuntimeException(
            "Malformed embedding response from $baseUrl: unsupported `data[$index].embedding` type (${embeddingNode::class.java.simpleName})"
        )
    }

    private fun decodeBase64Embedding(encoded: String, index: Int): FloatArray {
        val bytes = try {
            Base64.getDecoder().decode(encoded)
        } catch (e: IllegalArgumentException) {
            throw RuntimeException("Malformed embedding response from $baseUrl: invalid base64 in `data[$index].embedding`", e)
        }
        if (bytes.isEmpty()) {
            throw RuntimeException("Malformed embedding response from $baseUrl: decoded `data[$index].embedding` is empty")
        }

        return when {
            // Perplexity-style quantized payload: one int8 value per dimension.
            bytes.size == dimension -> bytes.map { it.toFloat() }.toFloatArray()
            // OpenAI-compatible binary float payload.
            bytes.size == dimension * 4 -> decodeFloat32LittleEndian(bytes)
            // Fallback for providers that return float32 with inferred dimension.
            bytes.size % 4 == 0 -> decodeFloat32LittleEndian(bytes)
            // Last resort: treat as int8 vector with inferred dimension.
            else -> bytes.map { it.toFloat() }.toFloatArray()
        }
    }

    private fun decodeFloat32LittleEndian(bytes: ByteArray): FloatArray {
        val count = bytes.size / 4
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(count)
        for (i in 0 until count) {
            out[i] = buffer.float
        }
        return out
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class EmbeddingResponse(val data: List<EmbeddingDatum>? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class EmbeddingDatum(val embedding: Any? = null, val index: Int)
}
