package com.mdwiki.service

import org.springframework.stereotype.Service

@Service
class WikilinkService {

    data class Wikilink(val slug: String, val displayText: String?)

    private val wikilinkPattern = Regex("""\[\[([^|\]]+?)(?:\|([^\]]+?))?\]\]""")
    private val tagPattern = Regex("""(?<=\s|^)#([\w\p{L}-]+)""")
    private val codeBlockPattern = Regex("""(`[^`]+`|```[\s\S]*?```)""")
    private val slugNonAlnum = Regex("[^a-z0-9а-яё]+", RegexOption.IGNORE_CASE)
    private val slugTrimDashes = Regex("^-+|-+$")

    /** Канонический slug страницы (как при создании из UI). */
    fun normalizePageSlug(raw: String): String =
        raw.lowercase().trim()
            .replace(slugNonAlnum, "-")
            .replace(slugTrimDashes, "")

    /**
     * Заменяет вики-ссылки, у которых нормализованный slug/title цели равен [oldNormalizedSlug] или [oldNormalizedTitle], на [newSlug].
     * Подпись после `|` сохраняется.
     */
    fun rewriteWikilinksReferencingNormalizedSlug(
        body: String,
        oldNormalizedSlug: String,
        newSlug: String,
        oldNormalizedTitle: String? = null
    ): String {
        if (oldNormalizedSlug == newSlug) return body
        return wikilinkPattern.replace(body) { m ->
            val rawInner = m.groupValues[1].trim()
            val label = m.groupValues[2].trim()
            val normalizedInner = normalizePageSlug(rawInner)
            val matches = normalizedInner == oldNormalizedSlug ||
                (oldNormalizedTitle != null && normalizedInner == oldNormalizedTitle)
            if (!matches) {
                return@replace m.value
            }
            if (label.isEmpty()) {
                "[[$newSlug]]"
            } else {
                "[[$newSlug|$label]]"
            }
        }
    }

    fun extractWikilinks(markdown: String): List<Wikilink> {
        return wikilinkPattern.findAll(markdown).mapNotNull { match ->
            val rawSlug = match.groupValues[1].trim()
            val normalized = normalizePageSlug(rawSlug)
            if (normalized.isEmpty()) {
                return@mapNotNull null
            }
            Wikilink(
                slug = normalized,
                displayText = match.groupValues[2].trim().ifEmpty { null }
            )
        }.toList()
    }

    fun extractTags(markdown: String): Set<String> {
        val cleaned = codeBlockPattern.replace(markdown, "")
        return tagPattern.findAll(cleaned).map { it.groupValues[1] }.toSet()
    }
}
