package com.mdwiki.dto

import java.time.Instant

data class StableLinkRequest(val sectionKey: String, val expectedUpdatedAt: Instant)

data class StableLinkResponse(
    val stableId: String,
    val sectionKey: String,
    val pageSlug: String,
    val updatedAt: Instant,
    val url: String,
    val page: PageResponse? = null
)

data class StableLinkResolution(
    val stableId: String,
    val pageSlug: String,
    val sectionKey: String,
    val url: String
)
