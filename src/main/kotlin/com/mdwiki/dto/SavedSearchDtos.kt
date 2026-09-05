package com.mdwiki.dto

import com.mdwiki.model.SavedSearchMode
import com.mdwiki.model.SavedSearchSort
import java.time.Instant
import java.util.UUID

data class SavedSearchWriteRequest(
    val name: String,
    val queryText: String,
    val mode: SavedSearchMode,
    val tags: List<String> = emptyList(),
    val minScore: Double? = null,
    val sort: SavedSearchSort = SavedSearchSort.RELEVANCE,
    val expectedVersion: Long? = null
)

data class SavedSearchResponse(
    val id: UUID,
    val name: String,
    val queryText: String,
    val mode: SavedSearchMode,
    val tags: List<String>,
    val minScore: Double?,
    val sort: SavedSearchSort,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
)
