package com.mdwiki.util

object MarkdownSectionParser {
    data class ParsedSection(
        val stableKey: String,
        val heading: String?,
        val headingLevel: Int,
        val headingPath: String,
        val sortOrder: Int,
        val startOffset: Int,
        val bodyStartOffset: Int,
        val endOffset: Int,
        val explicitId: String? = null
    )

    private val HEADING = Regex("""^(#{1,6})\s+(.+?)\s*$""", RegexOption.MULTILINE)
    private val EXPLICIT_ID = Regex("""^(.*?)\s*\{#([A-Za-z0-9_.:-]+)\}\s*$""")
    private val FENCE_OPEN = Regex("""^ {0,3}(`{3,}|~{3,})(.*)$""")

    fun parse(markdown: String): List<ParsedSection> {
        val sections = mutableListOf<ParsedSection>()
        var sortOrder = 0

        val fence = frontmatterRange(markdown)
        val bodyStart = if (fence != null) {
            sections.add(
                ParsedSection(
                    stableKey = "_frontmatter",
                    heading = null,
                    headingLevel = 0,
                    headingPath = "",
                    sortOrder = sortOrder++,
                    startOffset = fence.first,
                    bodyStartOffset = fence.first,
                    endOffset = fence.last
                )
            )
            fence.last
        } else {
            0
        }

        val fences = fencedCodeRanges(markdown)
        val headingMatches = HEADING.findAll(markdown)
            .filter { it.range.first >= bodyStart }
            .filter { match -> fences.none { match.range.first in it } }
            .toList()
        val firstHeadingStart = headingMatches.firstOrNull()?.range?.first ?: markdown.length
        if (firstHeadingStart > bodyStart && markdown.substring(bodyStart, firstHeadingStart).isNotBlank()) {
            sections.add(
                ParsedSection(
                    stableKey = "_preamble",
                    heading = null,
                    headingLevel = 0,
                    headingPath = "",
                    sortOrder = sortOrder++,
                    startOffset = bodyStart,
                    bodyStartOffset = bodyStart,
                    endOffset = firstHeadingStart
                )
            )
        } else if (headingMatches.isEmpty() && markdown.substring(bodyStart).isNotBlank()) {
            sections.add(
                ParsedSection(
                    stableKey = "_preamble",
                    heading = null,
                    headingLevel = 0,
                    headingPath = "",
                    sortOrder = sortOrder++,
                    startOffset = bodyStart,
                    bodyStartOffset = bodyStart,
                    endOffset = markdown.length
                )
            )
        }

        data class Frame(val level: Int, val heading: String)

        val stack = ArrayDeque<Frame>()
        val usedKeys = linkedSetOf<String>().also { used ->
            sections.forEach { used.add(it.stableKey) }
        }

        headingMatches.forEachIndexed { index, match ->
            val level = match.groupValues[1].length
            val rawTitle = match.groupValues[2].trim()
            val (title, explicitId) = splitExplicitId(rawTitle)
            while (stack.isNotEmpty() && stack.last().level >= level) {
                stack.removeLast()
            }
            stack.addLast(Frame(level, title))
            val headingPath = stack.joinToString("::") { it.heading }
            val start = match.range.first
            val end = headingMatches
                .drop(index + 1)
                .firstOrNull { it.groupValues[1].length <= level }
                ?.range
                ?.first
                ?: markdown.length
            val bodyStartOffset = headingLineEnd(markdown, match.range.last)
            val baseKey = explicitId?.takeIf { it.isNotBlank() }
                ?: pathToKey(headingPath)
            val stableKey = uniqueKey(baseKey, usedKeys)
            usedKeys.add(stableKey)
            sections.add(
                ParsedSection(
                    stableKey = stableKey,
                    heading = title,
                    headingLevel = level,
                    headingPath = headingPath,
                    sortOrder = sortOrder++,
                    startOffset = start,
                    bodyStartOffset = bodyStartOffset.coerceAtMost(end),
                    endOffset = end,
                    explicitId = explicitId
                )
            )
        }
        return sections
    }

    private fun fencedCodeRanges(markdown: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var offset = 0
        var openStart: Int? = null
        var openMarker: String? = null
        for (line in markdown.splitToSequence("\n")) {
            val lineStart = offset
            val lineEnd = (offset + line.length).let { if (it < markdown.length && markdown[it] == '\n') it + 1 else it }
            val match = FENCE_OPEN.matchEntire(line.trimEnd('\r'))
            if (openMarker == null) {
                if (match != null) {
                    openStart = lineStart
                    openMarker = match.groupValues[1]
                }
            } else if (match != null) {
                val closer = match.groupValues[1]
                if (closer[0] == openMarker[0] && closer.length >= openMarker.length && match.groupValues[2].isBlank()) {
                    ranges.add(openStart!! until lineEnd)
                    openStart = null
                    openMarker = null
                }
            }
            offset = lineEnd
        }
        if (openStart != null) {
            ranges.add(openStart until markdown.length)
        }
        return ranges
    }

    private fun frontmatterRange(markdown: String): IntRange? {
        val stripped = MarkdownFrontmatter.strip(markdown)
        if (stripped == markdown) return null
        val end = markdown.length - stripped.length
        if (end <= 0) return null
        return 0 until end
    }

    private fun splitExplicitId(rawTitle: String): Pair<String, String?> {
        val match = EXPLICIT_ID.matchEntire(rawTitle) ?: return rawTitle to null
        return match.groupValues[1].trim() to match.groupValues[2]
    }

    private fun pathToKey(headingPath: String): String {
        val key = headingPath.split("::")
            .map { PageSlugNormalizer.normalize(it) }
            .filter { it.isNotBlank() }
            .joinToString("/")
        return key.ifBlank { "section" }
    }

    private fun uniqueKey(base: String, used: Set<String>): String {
        if (base !in used) return base
        var counter = 2
        while ("$base-$counter" in used) counter++
        return "$base-$counter"
    }

    private fun headingLineEnd(markdown: String, lastIndexOfMatch: Int): Int {
        val after = lastIndexOfMatch + 1
        if (after < markdown.length && markdown[after] == '\r') {
            return if (after + 1 < markdown.length && markdown[after + 1] == '\n') after + 2 else after + 1
        }
        if (after < markdown.length && markdown[after] == '\n') return after + 1
        return after
    }
}
