package com.mdwiki.mcp

import com.mdwiki.dto.PatchSectionMode
import com.mdwiki.dto.PatchSectionRequest
import com.mdwiki.mcp.McpSupport.currentUsername
import com.mdwiki.mcp.McpSupport.parseInstant
import com.mdwiki.service.PageService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiPatchSectionTool(private val pageService: PageService) {

    @McpTool(
        name = "wiki_patch_section",
        description = "Replace one section by stable key from wiki_map. Parent keys (includesChildren=true) replace the heading and all nested subsections — use a leaf key to change only one block. mode=body keeps the heading line (default); mode=section replaces the heading too. expectedUpdatedAt is required. Optional expectedHash from wiki_map rejects stale section content."
    )
    fun patch(
        @McpToolParam(description = "Page slug") slug: String,
        @McpToolParam(description = "Stable section key from wiki_map") sectionKey: String,
        @McpToolParam(description = "New section body (mode=body) or full section including heading (mode=section)") content: String,
        @McpToolParam(description = "ISO-8601 updatedAt from wiki_map or wiki_read") expectedUpdatedAt: String,
        @McpToolParam(description = "body (default) or section", required = false) mode: String?,
        @McpToolParam(description = "Optional SHA-256 of the current section slice", required = false) expectedHash: String?
    ): Map<String, Any?> {
        val parsedMode = when (mode?.trim()?.lowercase()) {
            null, "", "body" -> PatchSectionMode.BODY
            "section" -> PatchSectionMode.SECTION
            else -> throw IllegalArgumentException("mode must be body or section")
        }
        val result = pageService.patchSection(
            slug,
            PatchSectionRequest(
                sectionKey = sectionKey,
                content = content,
                expectedUpdatedAt = parseInstant(expectedUpdatedAt),
                mode = parsedMode,
                expectedHash = expectedHash?.takeIf { it.isNotBlank() }
            ),
            currentUsername()
        )
        return mapOf(
            "slug" to result.slug,
            "title" to result.title,
            "sectionKey" to result.sectionKey,
            "replacements" to result.replacements,
            "contentHash" to result.contentHash,
            "previousUpdatedAt" to result.previousUpdatedAt.toString(),
            "updatedAt" to result.updatedAt.toString()
        )
    }
}
