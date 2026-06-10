package com.mdwiki.rag

import com.mdwiki.config.EmbeddingProperties
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class EmbeddingProviderBuilder(
    private val embeddingProperties: EmbeddingProperties,
    private val webClientBuilder: WebClient.Builder
) {
    fun normalizeProvider(provider: String): String {
        val normalized = provider.trim().lowercase()
        if (normalized !in SUPPORTED_PROVIDERS) {
            throw IllegalArgumentException("Unsupported embedding provider: $provider")
        }
        return normalized
    }

    fun defaultModelFor(provider: String): String {
        return when (normalizeProvider(provider)) {
            "openai" -> embeddingProperties.openai.model
            "ollama" -> embeddingProperties.ollama.model
            "lmstudio" -> embeddingProperties.lmstudio.model
            else -> throw IllegalArgumentException("Unsupported embedding provider: $provider")
        }
    }

    fun resolveBaseUrl(provider: String, baseUrlOverride: String?): String {
        val normalizedProvider = normalizeProvider(provider)
        val override = baseUrlOverride?.trim()?.takeIf { it.isNotEmpty() }
        if (override != null) return override
        return when (normalizedProvider) {
            "openai" -> embeddingProperties.openai.baseUrl
            "ollama" -> embeddingProperties.ollama.baseUrl
            "lmstudio" -> embeddingProperties.lmstudio.baseUrl
            else -> throw IllegalArgumentException("Unsupported embedding provider: $provider")
        }
    }

    fun resolveApiKey(provider: String, apiKeyOverride: String?): String? {
        val normalizedProvider = normalizeProvider(provider)
        val override = apiKeyOverride?.trim()?.takeIf { it.isNotEmpty() }
        if (override != null) return override
        return when (normalizedProvider) {
            "openai" -> embeddingProperties.openai.apiKey.trim().takeIf { it.isNotEmpty() }
            "lmstudio" -> embeddingProperties.lmstudio.apiKey.trim().takeIf { it.isNotEmpty() }
            "ollama" -> null
            else -> throw IllegalArgumentException("Unsupported embedding provider: $provider")
        }
    }

    fun isApiKeyConfigured(provider: String, apiKeyOverride: String?): Boolean {
        return resolveApiKey(provider, apiKeyOverride) != null
    }

    fun create(provider: String, model: String, baseUrlOverride: String? = null, apiKeyOverride: String? = null): EmbeddingProvider {
        val normalizedProvider = normalizeProvider(provider)
        val normalizedModel = model.trim()
        if (normalizedModel.isEmpty()) {
            throw IllegalArgumentException("Embedding model must not be blank")
        }
        val baseUrl = resolveBaseUrl(normalizedProvider, baseUrlOverride)
        val apiKey = resolveApiKey(normalizedProvider, apiKeyOverride)

        return when (normalizedProvider) {
            "openai" -> OpenAiCompatibleEmbedding(
                baseUrl = baseUrl,
                model = normalizedModel,
                apiKey = apiKey ?: "",
                dimension = embeddingProperties.dimension,
                webClientBuilder = webClientBuilder
            )
            "ollama" -> OllamaEmbedding(
                baseUrl = baseUrl,
                model = normalizedModel,
                dimension = embeddingProperties.dimension,
                webClientBuilder = webClientBuilder
            )
            "lmstudio" -> OpenAiCompatibleEmbedding(
                baseUrl = baseUrl,
                model = normalizedModel,
                apiKey = apiKey ?: "",
                dimension = embeddingProperties.dimension,
                queryPrefix = embeddingProperties.lmstudio.queryPrefix,
                documentPrefix = embeddingProperties.lmstudio.documentPrefix,
                webClientBuilder = webClientBuilder
            )
            else -> throw IllegalArgumentException("Unsupported embedding provider: $provider")
        }
    }

    companion object {
        val SUPPORTED_PROVIDERS: Set<String> = setOf("openai", "ollama", "lmstudio")
    }
}
