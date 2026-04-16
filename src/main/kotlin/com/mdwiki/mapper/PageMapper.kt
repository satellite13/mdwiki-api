package com.mdwiki.mapper

import com.mdwiki.dto.PageListItem
import com.mdwiki.dto.PageResponse
import com.mdwiki.dto.SearchResult
import com.mdwiki.model.Page

private const val SEARCH_SNIPPET_LIMIT = 200

fun Page.toResponse(): PageResponse = PageResponse(
    id = id!!,
    slug = slug,
    title = title,
    contentMd = contentMd,
    contentHtml = contentHtml,
    tags = tags.map { it.name },
    createdBy = createdBy?.username,
    updatedBy = updatedBy?.username,
    folderId = folder?.id,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Page.toListItem(): PageListItem = PageListItem(
    id = id!!,
    slug = slug,
    title = title,
    tags = tags.map { it.name },
    folderId = folder?.id,
    updatedAt = updatedAt
)

fun Page.toSearchResult(): SearchResult = SearchResult(
    pageId = id!!,
    slug = slug,
    title = title,
    snippet = contentMd.toSearchSnippet()
)

private fun String?.toSearchSnippet(): String {
    if (this.isNullOrBlank()) {
        return ""
    }
    return if (length > SEARCH_SNIPPET_LIMIT) {
        "${take(SEARCH_SNIPPET_LIMIT)}..."
    } else {
        this
    }
}
