package com.mdwiki.mcp

import com.mdwiki.dto.ImportMdFileInput
import com.mdwiki.mcp.McpSupport.currentUsername
import com.mdwiki.mcp.McpSupport.parseUuid
import com.mdwiki.service.PageService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiImportTool(private val pageService: PageService) {

    @McpTool(
        name = "wiki_import",
        description = "Import a markdown file as a wiki page. Slug is derived from filename; title from frontmatter title, then H1, then filename. If a page with the same slug exists, skips unless overwrite=true. Requires EDITOR or ADMIN role."
    )
    fun importMd(
        @McpToolParam(description = "Markdown filename, e.g. my-note.md")
        filename: String,
        @McpToolParam(description = "Full markdown content (including optional YAML frontmatter)")
        contentMd: String,
        @McpToolParam(description = "Optional parent folder UUID. If omitted, page is created/moved to root.", required = false)
        folderId: String? = null,
        @McpToolParam(description = "If true, overwrite existing page with the same slug. Default false (skip).", required = false)
        overwrite: Boolean = false
    ): Map<String, Any?> {
        val username = currentUsername()
        val parsedFolderId = folderId?.takeIf { it.isNotBlank() }?.let(::parseUuid)
        val response = pageService.importMd(
            files = listOf(ImportMdFileInput(filename = filename, contentMd = contentMd)),
            folderId = parsedFolderId,
            overwrite = overwrite,
            username = username
        )
        val item = response.results.firstOrNull()
        return mapOf(
            "filename" to (item?.filename ?: filename),
            "slug" to item?.slug,
            "title" to item?.title,
            "status" to (item?.status?.wire ?: "error"),
            "message" to item?.message,
            "created" to response.created,
            "updated" to response.updated,
            "skipped" to response.skipped,
            "errors" to response.errors
        )
    }
}
