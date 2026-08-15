package com.mdwiki.dto

data class RagSearchResult(
    val chunkText: String,
    val sectionHeading: String?,
    val pageTitle: String,
    val pageSlug: String,
    val score: Double,
    val snippet: String,
    val tags: List<String> = emptyList(),
    val sectionKey: String? = null
)
