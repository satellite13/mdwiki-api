package com.mdwiki.dto

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.Instant
import java.util.UUID

object PageSlugConstraints {
    const val PATTERN = "^[a-z0-9а-яё]+(?:-[a-z0-9а-яё]+)*$"
    const val MESSAGE = "Slug must be lowercase letters (latin or Cyrillic), digits, and hyphens"
}

data class CreatePageRequest(
    @field:NotBlank
    @field:Pattern(
        regexp = PageSlugConstraints.PATTERN,
        message = PageSlugConstraints.MESSAGE
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
    @field:Pattern(
        regexp = PageSlugConstraints.PATTERN,
        message = PageSlugConstraints.MESSAGE
    )
    val slug: String? = null,
    val folderId: UUID? = null,
    val clearFolder: Boolean? = null,
    val expectedUpdatedAt: Instant? = null
)

data class PatchPageRequest(
    val oldText: String,
    val newText: String,
    val expectedUpdatedAt: Instant,
    val replaceAll: Boolean = false,
    val sectionKey: String? = null
)

enum class PatchSectionMode {
    BODY,
    SECTION
}

data class PatchSectionRequest(
    val sectionKey: String,
    val content: String,
    val expectedUpdatedAt: Instant,
    val mode: PatchSectionMode = PatchSectionMode.BODY,
    val expectedHash: String? = null
)

data class PatchSectionResponse(
    val slug: String,
    val title: String,
    val sectionKey: String,
    val contentMd: String?,
    val replacements: Int,
    val previousUpdatedAt: Instant,
    val updatedAt: Instant,
    val contentHash: String
)

data class PageSectionMapItem(
    val key: String,
    val heading: String?,
    val headingPath: String,
    val level: Int,
    val length: Int,
    val hash: String,
    val includesChildren: Boolean,
    val stableId: String? = null
)

data class PageSectionMapResponse(
    val slug: String,
    val updatedAt: Instant,
    val sections: List<PageSectionMapItem>
)

data class RestoreRevisionRequest(
    val revisionNo: Long,
    val expectedUpdatedAt: Instant,
    val restoreTitle: Boolean = false
)

data class RevisionDiffResponse(
    val from: com.mdwiki.service.RevisionSnapshot,
    val to: com.mdwiki.service.RevisionSnapshot,
    val rows: List<com.mdwiki.service.RevisionDiffRow>,
    val truncated: Boolean
)

data class PatchPageResponse(
    val slug: String,
    val title: String,
    val contentMd: String?,
    val replacements: Int,
    val previousUpdatedAt: Instant,
    val updatedAt: Instant
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
