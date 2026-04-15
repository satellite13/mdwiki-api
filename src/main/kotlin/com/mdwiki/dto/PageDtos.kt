package com.mdwiki.dto

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class CreatePageRequest(
    @field:NotBlank
    val slug: String,
    @field:NotBlank
    val title: String,
    val contentMd: String = ""
)

data class UpdatePageRequest(
    val title: String? = null,
    val contentMd: String? = null
)

data class PageResponse(
    val id: UUID,
    val slug: String,
    val title: String,
    val contentMd: String?,
    val contentHtml: String?,
    val tags: List<String>,
    val createdBy: String?,
    val updatedBy: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class PageListItem(
    val id: UUID,
    val slug: String,
    val title: String,
    val tags: List<String>,
    val updatedAt: Instant
)

data class BacklinkResponse(
    val slug: String,
    val title: String
)
