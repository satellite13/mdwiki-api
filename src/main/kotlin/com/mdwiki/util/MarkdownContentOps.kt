package com.mdwiki.util

object MarkdownContentOps {
    data class ReplaceResult(val content: String, val replacements: Int)
    data class SectionSlice(val content: String, val heading: String, val ambiguous: Boolean = false)
    data class Truncated(val content: String, val truncated: Boolean, val fullLength: Int)

    private val HEADING = Regex("""^(#{1,6})\s+(.+?)\s*$""", RegexOption.MULTILINE)

    fun replaceExact(
        content: String,
        oldText: String,
        newText: String,
        replaceAll: Boolean
    ): ReplaceResult {
        if (oldText.isBlank()) {
            throw IllegalArgumentException("oldText must not be blank")
        }
        val matches = countOccurrences(content, oldText)
        if (matches == 0) {
            val hints = nearbySnippets(content, oldText)
            val hint = if (hints.isEmpty()) "" else " Nearby: ${hints.joinToString(" | ")}"
            throw IllegalArgumentException("oldText not found (0 times).$hint")
        }
        if (matches > 1 && !replaceAll) {
            throw IllegalArgumentException(
                "oldText matches $matches times; pass replaceAll=true or add more context"
            )
        }
        val updated = if (replaceAll) content.replace(oldText, newText) else content.replaceFirst(oldText, newText)
        return ReplaceResult(content = updated, replacements = if (replaceAll) matches else 1)
    }

    fun extractSection(content: String, heading: String): SectionSlice {
        val wanted = heading.trim()
        if (wanted.isBlank()) {
            throw IllegalArgumentException("heading must not be blank")
        }
        val headings = HEADING.findAll(content).toList()
        val matches = headings.filter { it.groupValues[2].trim() == wanted }
        if (matches.isEmpty()) {
            throw IllegalArgumentException("heading '$wanted' not found")
        }
        if (matches.size > 1) {
            throw IllegalArgumentException("heading '$wanted' is ambiguous (${matches.size} matches)")
        }
        val match = matches.single()
        val level = match.groupValues[1].length
        val start = match.range.first
        val end = headings
            .firstOrNull { it.range.first > start && it.groupValues[1].length <= level }
            ?.range
            ?.first
            ?: content.length
        return SectionSlice(content = content.substring(start, end).trimEnd(), heading = wanted)
    }

    fun truncate(content: String, maxChars: Int): Truncated {
        require(maxChars > 0) { "maxChars must be positive" }
        if (content.length <= maxChars) {
            return Truncated(content = content, truncated = false, fullLength = content.length)
        }
        return Truncated(content = content.substring(0, maxChars), truncated = true, fullLength = content.length)
    }

    internal fun nearbySnippets(content: String, oldText: String, limit: Int = 3): List<String> {
        val tokens = oldText
            .split(Regex("\\s+"))
            .map { it.trim(',', '.', '!', '?', ';', ':') }
            .filter { it.length >= 3 }
        if (tokens.isEmpty()) return emptyList()
        return content.lineSequence()
            .map { line -> line to tokens.count { token -> line.contains(token, ignoreCase = true) } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(limit)
            .toList()
    }

    private fun countOccurrences(content: String, oldText: String): Int {
        var count = 0
        var index = 0
        while (true) {
            val found = content.indexOf(oldText, index)
            if (found < 0) return count
            count++
            index = found + oldText.length
        }
    }
}
