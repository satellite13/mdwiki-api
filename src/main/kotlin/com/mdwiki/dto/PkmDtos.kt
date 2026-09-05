package com.mdwiki.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class TextCaptureRequest(
    @field:NotBlank @field:Size(max = 200_000) val text: String,
    @field:Size(max = 500) val title: String? = null
)
data class UrlCaptureRequest(
    @field:NotBlank @field:Size(max = 4096) val url: String,
    @field:Size(max = 200_000) val note: String? = null,
    @field:Size(max = 500) val title: String? = null
)
data class CaptureResponse(val kind: String, val page: PageResponse, val attachment: AttachmentResponse? = null)
data class DailyNoteResponse(val date: String, val page: PageResponse, val created: Boolean)
data class RecentPageResponse(val page: PageListItem, val lastOpenedAt: Instant, val openCount: Long)
data class FavoritePageResponse(val page: PageListItem, val favoritedAt: Instant)

data class UnlinkedMentionResponse(
    val sourceSlug: String,
    val sourceTitle: String,
    val snippet: String,
    val sectionKey: String?,
    val startOffset: Int,
    val endOffset: Int,
    val expectedUpdatedAt: Instant
)
data class LinkUnlinkedMentionRequest(
    @field:NotBlank val sourceSlug: String,
    val startOffset: Int,
    val endOffset: Int,
    val expectedUpdatedAt: Instant
)
enum class OrphanDefinition { NO_INCOMING, NO_LINKS, NO_OUTGOING }
data class OrphanPageResponse(val page: PageListItem, val incomingCount: Long, val outgoingCount: Long)
