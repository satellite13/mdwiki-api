package com.mdwiki.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mdwiki")
data class WikiProperties(
    val contentDir: String = "./wiki-content",
    val rag: RagProperties = RagProperties()
) {
    data class RagProperties(
        val maxChunkChars: Int = 2000,
        val vectorSearchLimit: Int = 20,
        val embeddingIndexAttempts: Int = 3
    )
}
