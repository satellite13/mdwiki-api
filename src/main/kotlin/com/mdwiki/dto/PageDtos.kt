package com.mdwiki.dto

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.Instant
import java.util.UUID

data class CreatePageRequest(
    @field:NotBlank
    @field:Pattern(
        regexp = "^[a-z0-9а-яё]+(?:-[a-z0-9а-яё]+)*$",
        message = "Slug must be lowercase letters (latin or Cyrillic), digits, and hyphens"
    )
    val slug: String,
    @field:NotBlank
    val title: String,
    val contentMd: String = "",
    val folderId: UUID? = null
)

data class UpdatePageRequest(
    val title: String? = null,
    val contentMd: String? = null,
    val slug: String? = null,
    val folderId: UUID? = null,
    val clearFolder: Boolean? = null
)

data class PageResponse(
    val id: UUID,
    val slug: String,
    val title: String,
    val contentMd: String?,
    /** Распарсенный YAML frontmatter документа (только чтение с API). */
    val frontmatterMeta: JsonNode? = null,
    /** Является ли страница read-only (locked в frontmatter). */
    val locked: Boolean = false,
    val tags: List<String>,
    val createdBy: String?,
    val updatedBy: String?,
    val folderId: UUID? = null,
    val folderPath: List<FolderPathItem> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant
)

data class PageListItem(
    val id: UUID,
    val slug: String,
    val title: String,
    val tags: List<String>,
    val folderId: UUID? = null,
    val updatedAt: Instant,
    val deletedAt: Instant? = null
)

data class BacklinkResponse(
    val slug: String,
    val title: String
)

data class FolderPathItem(
    val id: UUID,
    val name: String
)

data class ImportMdFileInput(
    val filename: String,
    val contentMd: String
)

enum class ImportMdItemStatus(@get:JsonValue val wire: String) {
    CREATED("created"),
    UPDATED("updated"),
    SKIPPED("skipped"),
    ERROR("error")
}

data class ImportMdItemResult(
    val filename: String,
    val slug: String? = null,
    val title: String? = null,
    val status: ImportMdItemStatus,
    val message: String? = null
)

data class ImportMdPagesResponse(
    val results: List<ImportMdItemResult>,
    val created: Int,
    val updated: Int,
    val skipped: Int,
    val errors: Int
)
