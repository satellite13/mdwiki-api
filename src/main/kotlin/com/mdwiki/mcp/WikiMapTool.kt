package com.mdwiki.mcp

import com.mdwiki.service.PageService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiMapTool(private val pageService: PageService) {

    @McpTool(
        name = "wiki_map",
        description = "List stable section keys for a page without returning full markdown. Use keys with wiki_read(sectionKey) and wiki_patch_section. A parent heading includes nested children until the next heading of the same or higher level; includesChildren=true means wiki_patch_section on that key rewrites the whole subtree. Prefer a leaf key to edit one subsection."
    )
    fun map(@McpToolParam(description = "Page slug") slug: String): Map<String, Any?> {
        val result = pageService.mapSections(slug)
        return mapOf(
            "slug" to result.slug,
            "updatedAt" to result.updatedAt.toString(),
            "sections" to result.sections.map {
                mapOf(
                    "key" to it.key,
                    "heading" to it.heading,
                    "headingPath" to it.headingPath,
                    "level" to it.level,
                    "length" to it.length,
                    "hash" to it.hash,
                    "includesChildren" to it.includesChildren
                )
            }
        )
    }
}
