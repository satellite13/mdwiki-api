package com.mdwiki.dto

import java.util.UUID

data class SearchResult(
    val pageId: UUID,
    val slug: String,
    val title: String,
    val snippet: String
)
