package com.mdwiki.dto

import jakarta.validation.constraints.NotBlank
import java.util.UUID

enum class BrokenLinkKind {
    WIKILINK,
    MARKDOWN
}

data class BrokenLinkResponse(
    val id: UUID?,
    val brokenTarget: String,
    val kind: BrokenLinkKind,
    val sourceSlug: String,
    val sourceTitle: String,
    val displayText: String? = null,
)

data class RewriteBrokenLinksRequest(
    @field:NotBlank
    val fromTarget: String,
    @field:NotBlank
    val toSlug: String,
    /** Если задан — переписать только на этой странице-источнике. */
    val sourceSlug: String? = null,
)

data class RewriteBrokenLinksResponse(
    val pagesUpdated: Int,
    val skippedLocked: List<String> = emptyList(),
)
