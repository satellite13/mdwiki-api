package com.mdwiki.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mdwiki.embedding")
data class EmbeddingProperties(
    val provider: String = "openai",
    val dimension: Int = 1536,
    val openai: OpenAiEmbeddingConfig = OpenAiEmbeddingConfig(),
    val ollama: OllamaEmbeddingConfig = OllamaEmbeddingConfig(),
    val lmstudio: LmStudioEmbeddingConfig = LmStudioEmbeddingConfig(),
    /** Max JSON body size when reading embedding API responses (WebClient codec buffer). */
    val maxResponseBufferBytes: Int = 16 * 1024 * 1024
) {
    data class OpenAiEmbeddingConfig(
        val baseUrl: String = "https://api.openai.com/v1",
        val model: String = "text-embedding-3-small",
        val apiKey: String = ""
    )

    data class OllamaEmbeddingConfig(
        val baseUrl: String = "http://localhost:11434",
        val model: String = "nomic-embed-text"
    )

    data class LmStudioEmbeddingConfig(
        val baseUrl: String = "http://localhost:1234/v1",
        val model: String = "text-embedding-nomic-embed-text-v1.5@q8_0",
        val apiKey: String = "",
        val queryPrefix: String = "",
        val documentPrefix: String = ""
    )
}
