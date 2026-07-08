package com.mdwiki.service

import org.springframework.stereotype.Service

@Service
class WikilinkService {

    data class Wikilink(val slug: String, val displayText: String?)

    private val wikilinkPattern = Regex("""\[\[([^|\]]+?)(?:\|([^\]]+?))?\]\]""")
    private val tagPattern = Regex("""(?<=\s|^)#([\w\p{L}-]+)""")
    private val fencedCodePattern = Regex("""(?ms)^([`~]{3,}).*?^\1\s*$""")
    private val htmlCodeBlockPattern = Regex("""<(code|pre)\b[^>]*>[\s\S]*?</\1>""", RegexOption.IGNORE_CASE)
    private val indentedCodeLinePattern = Regex("""(?m)^(?:    |\t).*$""")
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
        return transformOutsideProtected(body) { segment ->
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
        val protected = protectedRanges(markdown)
        val result = mutableListOf<Wikilink>()
        wikilinkPattern.findAll(markdown).forEach { match ->
            if (isInRanges(protected, match.range.first)) return@forEach
            val rawSlug = match.groupValues[1].trim()
            val normalized = normalizePageSlug(rawSlug)
            if (normalized.isEmpty()) return@forEach
            result += Wikilink(
                slug = normalized,
                displayText = match.groupValues[2].trim().ifEmpty { null }
            )
        }
        return result
    }

    fun extractTags(markdown: String): Set<String> {
        val protected = protectedRanges(markdown)
        val result = mutableSetOf<String>()
        tagPattern.findAll(markdown).forEach { match ->
            if (isInRanges(protected, match.range.first)) return@forEach
            result += match.groupValues[1]
        }
        return result
    }

    private fun protectedRanges(markdown: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        fencedCodePattern.findAll(markdown).forEach { ranges += it.range }
        ranges += inlineCodeRanges(markdown, mergeRanges(ranges))
        htmlCodeBlockPattern.findAll(markdown).forEach { ranges += it.range }
        indentedCodeLinePattern.findAll(markdown).forEach { ranges += it.range }
        return mergeRanges(ranges)
    }

    private fun inlineCodeRanges(markdown: String, alreadyProtected: List<IntRange>): List<IntRange> {
        val protected = mergeRanges(alreadyProtected)
        val inline = mutableListOf<IntRange>()
        var i = 0
        while (i < markdown.length) {
            if (isInRanges(protected, i)) {
                i++
                continue
            }
            if (markdown[i] != '`') {
                i++
                continue
            }

            var tickCount = 0
            while (i + tickCount < markdown.length && markdown[i + tickCount] == '`') tickCount++
            val openStart = i
            i += tickCount
            var closed = false
            while (i < markdown.length) {
                if (markdown[i] == '`') {
                    var closeCount = 0
                    while (i + closeCount < markdown.length && markdown[i + closeCount] == '`') closeCount++
                    if (closeCount == tickCount) {
                        inline += openStart until (i + closeCount)
                        i += closeCount
                        closed = true
                        break
                    }
                }
                i++
            }
            if (!closed) {
                i = openStart + 1
            }
        }
        return inline
    }

    private fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf<IntRange>()
        var current = sorted.first()
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.first <= current.last + 1) {
                current = current.first..maxOf(current.last, next.last)
            } else {
                merged += current
                current = next
            }
        }
        merged += current
        return merged
    }

    private fun isInRanges(ranges: List<IntRange>, position: Int): Boolean =
        ranges.any { position in it }

    private fun transformOutsideProtected(markdown: String, transform: (String) -> String): String {
        val protected = protectedRanges(markdown)
        if (protected.isEmpty()) return transform(markdown)

        val out = StringBuilder()
        var lastEnd = 0
        for (range in protected) {
            if (range.first > lastEnd) {
                out.append(transform(markdown.substring(lastEnd, range.first)))
            }
            out.append(markdown.substring(range.first, range.last + 1))
            lastEnd = range.last + 1
        }
        if (lastEnd < markdown.length) {
            out.append(transform(markdown.substring(lastEnd)))
        }
        return out.toString()
    }

    data class InternalPageLink(val label: String, val slugRaw: String)

    private val mdInternalPageLinkPattern = Regex("""\[([^\]]*)\]\(/page/([^)]+)\)""")

    fun extractInternalPageLinks(markdown: String): List<InternalPageLink> {
        val protected = protectedRanges(markdown)
        val result = mutableListOf<InternalPageLink>()
        mdInternalPageLinkPattern.findAll(markdown).forEach { match ->
            if (isInRanges(protected, match.range.first)) return@forEach
            val raw = match.groupValues[2].trim()
            val decoded = runCatching { java.net.URLDecoder.decode(raw, Charsets.UTF_8) }.getOrDefault(raw)
            result += InternalPageLink(label = match.groupValues[1], slugRaw = decoded)
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
        return transformOutsideProtected(body) { segment ->
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
