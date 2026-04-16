package com.mdwiki.mapper

import com.mdwiki.dto.PageListItem
import com.mdwiki.dto.PageResponse
import com.mdwiki.dto.SearchResult
import com.mdwiki.model.Page
import com.mdwiki.util.MarkdownFrontmatter

private const val SEARCH_SNIPPET_LIMIT = 200

fun Page.toResponse(): PageResponse = PageResponse(
    id = id!!,
    slug = slug,
    title = title,
    contentMd = contentMd,
    contentHtml = contentHtml,
    frontmatterMeta = frontmatterMeta,
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

/** Normalizes PostgreSQL `ts_headline` output for API (plain text + length cap). */
fun headlineToSearchSnippet(headline: String): String {
    if (headline.isBlank()) return ""
    val cleaned = stripMarkdownForSearchSnippet(headline)
    return if (cleaned.length > SEARCH_SNIPPET_LIMIT) {
        "${cleaned.take(SEARCH_SNIPPET_LIMIT).trimEnd()}…"
    } else {
        cleaned
    }
}

private fun String?.toSearchSnippet(): String {
    if (this.isNullOrBlank()) {
        return ""
    }
    val plain = stripMarkdownForSearchSnippet(this)
    return if (plain.length > SEARCH_SNIPPET_LIMIT) {
        "${plain.take(SEARCH_SNIPPET_LIMIT).trimEnd()}…"
    } else {
        plain
    }
}

/** Turns markdown-ish body into a single-line plain snippet for search cards (not full MD parsing). */
private fun stripMarkdownForSearchSnippet(md: String): String {
    var s = MarkdownFrontmatter.strip(md)
    s = s.replace(Regex("```[\\s\\S]*?```"), " ")
    s = s.replace(Regex("`([^`]+)`"), "$1")
    s = s.replace(Regex("!\\[([^\\]]*)]\\([^)]*\\)"), "$1")
    s = s.replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
    s = s.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
    s = s.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
    s = s.replace(Regex("__([^_]+)__"), "$1")
    s = s.replace(Regex("(?<!\\*)\\*(?!\\*)([^*]+)(?<!\\*)\\*(?!\\*)"), "$1")
    s = s.replace(Regex("(?<!_)_(?!_)([^_]+)(?<!_)_(?!_)"), "$1")
    s = s.replace(Regex("~~([^~]+)~~"), "$1")
    s = s.replace(Regex("^[-*+]\\s+", RegexOption.MULTILINE), "")
    s = s.replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
    s = s.replace(Regex("^>\\s?", RegexOption.MULTILINE), "")
    s = s.replace(Regex("^---+\\s*$", RegexOption.MULTILINE), " ")
    s = s.replace(Regex("^\\*{3,}\\s*$", RegexOption.MULTILINE), " ")
    s = s.replace(Regex("\\s+"), " ").trim()
    return s
}
