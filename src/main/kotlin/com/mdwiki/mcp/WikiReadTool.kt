package com.mdwiki.mcp

import com.mdwiki.error.NotFoundException
import com.mdwiki.service.PageService
import com.mdwiki.service.SectionIndexService
import com.mdwiki.util.MarkdownContentOps
import com.mdwiki.util.MarkdownSectionParser
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiReadTool(private val pageService: PageService) {

    @McpTool(
        name = "wiki_read",
        description = "Read a wiki page by its slug. Prefer sectionKey from wiki_map for a single section. heading/maxChars also work. Always returns updatedAt. For edits use wiki_patch_section or wiki_patch."
    )
    fun read(
        @McpToolParam(description = "Page slug (URL-friendly name)") slug: String,
        @McpToolParam(
            description = "Optional heading text (without #) to return only that section. Ignored when sectionKey is set.",
            required = false
        ) heading: String?,
        @McpToolParam(
            description = "Optional max characters of the returned markdown slice.",
            required = false
        ) maxChars: Int?,
        @McpToolParam(
            description = "Stable section key from wiki_map. Takes priority over heading.",
            required = false
        ) sectionKey: String?
    ): Map<String, Any?> {
        val page = pageService.findBySlug(slug)
        val backlinks = pageService.getBacklinks(slug)
        val full = page.contentMd ?: ""
        val parsed = MarkdownSectionParser.parse(full)
        val byKey = if (!sectionKey.isNullOrBlank()) {
            parsed.find { it.stableKey == sectionKey }
                ?: throw NotFoundException("Section '$sectionKey' not found on page '$slug'")
        } else {
            null
        }
        val byHeading = if (byKey == null && !heading.isNullOrBlank()) {
            MarkdownContentOps.extractSection(full, heading)
        } else {
            null
        }
        val slice = when {
            byKey != null -> full.substring(byKey.startOffset, byKey.endOffset)
            byHeading != null -> byHeading.content
            else -> full
        }
        val limited = if (maxChars != null) {
            MarkdownContentOps.truncate(slice, maxChars)
        } else {
            MarkdownContentOps.Truncated(content = slice, truncated = false, fullLength = slice.length)
        }
        val hash = if (byKey != null) {
            SectionIndexService.hashOf(full, byKey.startOffset, byKey.endOffset)
        } else {
            null
        }
        return mapOf(
            "slug" to page.slug,
            "title" to page.title,
            "contentMd" to limited.content,
            "sectionKey" to byKey?.stableKey,
            "sectionHeading" to (byKey?.heading ?: byHeading?.heading),
            "headingPath" to byKey?.headingPath,
            "contentHash" to hash,
            "contentTruncated" to limited.truncated,
            "contentLength" to limited.fullLength,
            "frontmatterMeta" to page.frontmatterMeta,
            "tags" to page.tags,
            "folderId" to page.folderId?.toString(),
            "folderPath" to page.folderPath.map { mapOf("id" to it.id, "name" to it.name) },
            "backlinks" to backlinks.map { mapOf("slug" to it.slug, "title" to it.title) },
            "createdBy" to page.createdBy,
            "updatedBy" to page.updatedBy,
            "createdAt" to page.createdAt.toString(),
            "updatedAt" to page.updatedAt.toString()
        )
    }
}
