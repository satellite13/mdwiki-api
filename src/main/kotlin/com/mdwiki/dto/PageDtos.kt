package com.mdwiki.dto

import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class CreatePageRequest(
    @field:NotBlank
    val slug: String,
    @field:NotBlank
    val title: String,
    val contentMd: String = "",
    val folderId: UUID? = null
)

data class UpdatePageRequest(
    val title: String? = null,
    val contentMd: String? = null,
    val folderId: UUID? = null,
    val clearFolder: Boolean? = null
)

data class PageResponse(
    val id: UUID,
    val slug: String,
    val title: String,
    val contentMd: String?,
    val contentHtml: String?,
    /** Распарсенный YAML frontmatter документа (только чтение с API). */
    val frontmatterMeta: JsonNode? = null,
    val tags: List<String>,
    val createdBy: String?,
    val updatedBy: String?,
    val folderId: UUID? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class PageListItem(
    val id: UUID,
    val slug: String,
    val title: String,
    val tags: List<String>,
    val folderId: UUID? = null,
    val updatedAt: Instant
)

data class BacklinkResponse(
    val slug: String,
    val title: String
)
