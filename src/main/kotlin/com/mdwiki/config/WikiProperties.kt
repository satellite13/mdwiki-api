package com.mdwiki.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize

@ConfigurationProperties(prefix = "mdwiki")
data class WikiProperties(
    val contentDir: String = "./wiki-content",
    val rag: RagProperties = RagProperties(),
    val attachments: AttachmentProperties = AttachmentProperties(),
    val bundle: BundleProperties = BundleProperties()
) {
    data class RagProperties(
        val maxChunkChars: Int = 2000,
        val vectorSearchLimit: Int = 20,
        val embeddingIndexAttempts: Int = 3
    )

    data class AttachmentProperties(
        /**
         * Директории хоста, из которых разрешён импорт вложений по пути
         * (MCP-инструмент wiki_attachment_upload). Пустой список = импорт по пути запрещён.
         */
        val allowedImportDirs: List<String> = emptyList()
    )

    data class BundleProperties(
        val maxSize: DataSize = DataSize.ofMegabytes(200)
    )
}
