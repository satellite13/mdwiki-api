package com.mdwiki.dto

import java.util.UUID
import java.time.Instant

data class SearchResult(
    val pageId: UUID,
    val slug: String,
    val title: String,
    val snippet: String,
    val updatedAt: Instant? = null,
    val tags: List<String> = emptyList()
)
