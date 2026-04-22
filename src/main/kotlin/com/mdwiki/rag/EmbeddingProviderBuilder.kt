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

    fun create(provider: String, model: String): EmbeddingProvider {
        val normalizedProvider = normalizeProvider(provider)
        val normalizedModel = model.trim()
        if (normalizedModel.isEmpty()) {
            throw IllegalArgumentException("Embedding model must not be blank")
        }

        return when (normalizedProvider) {
            "openai" -> OpenAiCompatibleEmbedding(
                baseUrl = embeddingProperties.openai.baseUrl,
                model = normalizedModel,
                apiKey = embeddingProperties.openai.apiKey,
                dimension = embeddingProperties.dimension,
                webClientBuilder = webClientBuilder
            )
            "ollama" -> OllamaEmbedding(
                baseUrl = embeddingProperties.ollama.baseUrl,
                model = normalizedModel,
                dimension = embeddingProperties.dimension,
                webClientBuilder = webClientBuilder
            )
            "lmstudio" -> OpenAiCompatibleEmbedding(
                baseUrl = embeddingProperties.lmstudio.baseUrl,
                model = normalizedModel,
                apiKey = embeddingProperties.lmstudio.apiKey,
                dimension = embeddingProperties.dimension,
                webClientBuilder = webClientBuilder
            )
            else -> throw IllegalArgumentException("Unsupported embedding provider: $provider")
        }
    }

    companion object {
        val SUPPORTED_PROVIDERS: Set<String> = setOf("openai", "ollama", "lmstudio")
    }
}
