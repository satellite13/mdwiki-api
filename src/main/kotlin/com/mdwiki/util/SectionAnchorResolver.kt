package com.mdwiki.util

object SectionAnchorResolver {
    private val EXPLICIT_ID = Regex("""^(.*?)\s*\{#([A-Za-z0-9_.:-]+)\}\s*$""")

    fun resolveKey(markdown: String, sectionHeading: String?, chunkText: String): String? {
        val sections = MarkdownSectionParser.parse(markdown)
        if (sectionHeading.isNullOrBlank()) {
            return sections.firstOrNull { it.stableKey == "_preamble" }?.stableKey
        }
        val heading = stripExplicitId(sectionHeading)
        val candidates = sections.filter { it.heading == heading }
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates[0].stableKey
        val needle = chunkText.trim()
        val containing = candidates.filter { section ->
            val end = section.endOffset.coerceAtMost(markdown.length)
            val start = section.startOffset.coerceAtLeast(0).coerceAtMost(end)
            markdown.substring(start, end).contains(needle)
        }
        val chosen = containing.minByOrNull { it.endOffset - it.startOffset }
            ?: candidates.minByOrNull { it.headingLevel }
        return chosen?.stableKey
    }

    private fun stripExplicitId(raw: String): String {
        val match = EXPLICIT_ID.matchEntire(raw.trim()) ?: return raw.trim()
        return match.groupValues[1].trim()
    }
}
