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
        return transformOutsideCode(body) { segment ->
            wikilinkPattern.replace(segment) { m ->
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
    }

    fun extractWikilinks(markdown: String): List<Wikilink> {
        val result = mutableListOf<Wikilink>()
        forEachOutsideCode(markdown) { segment ->
            wikilinkPattern.findAll(segment).forEach { match ->
                val rawSlug = match.groupValues[1].trim()
                val normalized = normalizePageSlug(rawSlug)
                if (normalized.isEmpty()) return@forEach
                result += Wikilink(
                    slug = normalized,
                    displayText = match.groupValues[2].trim().ifEmpty { null }
                )
            }
        }
        return result
    }

    fun extractTags(markdown: String): Set<String> {
        val result = mutableSetOf<String>()
        forEachOutsideCode(markdown) { segment ->
            tagPattern.findAll(segment).forEach { result += it.groupValues[1] }
        }
        return result
    }

    private fun forEachOutsideCode(markdown: String, action: (String) -> Unit) {
        var lastEnd = 0
        for (match in codeBlockPattern.findAll(markdown)) {
            if (match.range.first > lastEnd) {
                action(markdown.substring(lastEnd, match.range.first))
            }
            lastEnd = match.range.last + 1
        }
        if (lastEnd < markdown.length) {
            action(markdown.substring(lastEnd))
        }
    }

    private fun transformOutsideCode(markdown: String, transform: (String) -> String): String {
        val out = StringBuilder()
        var lastEnd = 0
        for (match in codeBlockPattern.findAll(markdown)) {
            if (match.range.first > lastEnd) {
                out.append(transform(markdown.substring(lastEnd, match.range.first)))
            }
            out.append(match.value)
            lastEnd = match.range.last + 1
        }
        if (lastEnd < markdown.length) {
            out.append(transform(markdown.substring(lastEnd)))
        }
        return out.toString()
    }

    data class InternalPageLink(val label: String, val slugRaw: String)

    private val mdInternalPageLinkPattern = Regex("""\[([^\]]*)\]\(/page/([^)]+)\)""")

    fun extractInternalPageLinks(markdown: String): List<InternalPageLink> {
        val result = mutableListOf<InternalPageLink>()
        forEachOutsideCode(markdown) { segment ->
            mdInternalPageLinkPattern.findAll(segment).forEach { match ->
                val raw = match.groupValues[2].trim()
                val decoded = runCatching { java.net.URLDecoder.decode(raw, Charsets.UTF_8) }.getOrDefault(raw)
                result += InternalPageLink(label = match.groupValues[1], slugRaw = decoded)
            }
        }
        return result
    }

    /**
     * Заменяет markdown-ссылки `[text](/page/old)` на `[text](/page/new)` при совпадении
     * нормализованного slug/title цели с [oldNormalizedSlug] или [oldNormalizedTitle].
     */
    fun rewriteInternalPageLinks(
        body: String,
        oldNormalizedSlug: String,
        newSlug: String,
        oldNormalizedTitle: String? = null,
    ): String {
        if (oldNormalizedSlug == newSlug) return body
        return transformOutsideCode(body) { segment ->
            mdInternalPageLinkPattern.replace(segment) { match ->
                val label = match.groupValues[1]
                val raw = match.groupValues[2].trim()
                val decoded = runCatching { java.net.URLDecoder.decode(raw, Charsets.UTF_8) }.getOrDefault(raw)
                val normalized = normalizePageSlug(decoded)
                val matches = normalized == oldNormalizedSlug ||
                    (oldNormalizedTitle != null && normalized == oldNormalizedTitle)
                if (!matches) {
                    return@replace match.value
                }
                "[$label](/page/$newSlug)"
            }
        }
    }

    /** true, если [rawReference] резолвится в одну из [pages] (по slug или нормализованному title). */
    fun resolvesToPage(rawReference: String, pages: Collection<com.mdwiki.model.Page>): Boolean {
        val trimmed = rawReference.trim()
        if (trimmed.isEmpty()) return false
        val normalized = normalizePageSlug(trimmed)
        if (normalized.isEmpty()) return false
        return pages.any { page ->
            page.slug == trimmed ||
                page.slug == normalized ||
                normalizePageSlug(page.title) == normalized
        }
    }
}
