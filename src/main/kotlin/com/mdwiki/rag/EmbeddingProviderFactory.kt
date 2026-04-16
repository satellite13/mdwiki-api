package com.mdwiki.rag

import com.mdwiki.config.EmbeddingProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class EmbeddingProviderFactory {

    @Bean
    @ConditionalOnMissingBean
    fun webClientBuilder(): WebClient.Builder = WebClient.builder()

    @Bean
    fun embeddingProvider(
        properties: EmbeddingProperties,
        webClientBuilder: WebClient.Builder
    ): EmbeddingProvider {
        return when (properties.provider.lowercase()) {
            "openai" -> OpenAiCompatibleEmbedding(
                baseUrl = properties.openai.baseUrl,
                model = properties.openai.model,
                apiKey = properties.openai.apiKey,
                dimension = properties.dimension,
                webClientBuilder = webClientBuilder
            )
            "ollama" -> OllamaEmbedding(
                baseUrl = properties.ollama.baseUrl,
                model = properties.ollama.model,
                dimension = properties.dimension,
                webClientBuilder = webClientBuilder
            )
            "lmstudio" -> OpenAiCompatibleEmbedding(
                baseUrl = properties.lmstudio.baseUrl,
                model = properties.lmstudio.model,
                apiKey = properties.lmstudio.apiKey,
                dimension = properties.dimension,
                webClientBuilder = webClientBuilder
            )
            else -> throw IllegalArgumentException("Unknown embedding provider: ${properties.provider}")
        }
    }
}
