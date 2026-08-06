package com.mdwiki.util

import com.mdwiki.mapper.extractFrontmatterTitleFromContent

/**
 * Resolves display title for imported markdown:
 * frontmatter `title` → first H1 → filename stem.
 */
object MdImportTitleResolver {

    private val h1Line = Regex("""^#\s+(.+?)\s*$""", RegexOption.MULTILINE)

    fun resolveTitle(contentMd: String, filenameStem: String): String {
        extractFrontmatterTitleFromContent(contentMd)?.let { return it }
        extractFirstH1(contentMd)?.let { return it }
        return filenameStem.trim().ifBlank { "Untitled" }
    }

    fun filenameStem(filename: String): String {
        val name = filename.substringAfterLast('/').substringAfterLast('\\').trim()
        return when {
            name.endsWith(".markdown", ignoreCase = true) -> name.dropLast(".markdown".length)
            name.endsWith(".md", ignoreCase = true) -> name.dropLast(".md".length)
            else -> name
        }.trim()
    }

    fun isMarkdownFilename(filename: String): Boolean {
        val name = filename.substringAfterLast('/').substringAfterLast('\\').trim()
        return name.endsWith(".md", ignoreCase = true) || name.endsWith(".markdown", ignoreCase = true)
    }

    private fun extractFirstH1(contentMd: String): String? {
        val body = MarkdownFrontmatter.strip(contentMd)
        return h1Line.find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}
