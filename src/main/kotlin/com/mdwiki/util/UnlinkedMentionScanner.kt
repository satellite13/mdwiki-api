package com.mdwiki.util

object UnlinkedMentionScanner {
    data class Match(val startOffset: Int, val endOffset: Int)

    fun scan(markdown: String, title: String): List<Match> {
        if (markdown.isEmpty() || title.isBlank()) return emptyList()
        val protected = BooleanArray(markdown.length)

        fun protect(start: Int, end: Int) {
            for (index in start.coerceAtLeast(0) until end.coerceAtMost(markdown.length)) {
                protected[index] = true
            }
        }

        if (markdown.startsWith("---")) {
            val end = markdown.indexOf("\n---", 3)
            if (end >= 0) protect(0, end + 4)
        }
        Regex("""(?ms)^(```|~~~).*?^\1[ \t]*$""").findAll(markdown)
            .forEach { protect(it.range.first, it.range.last + 1) }
        Regex("""`[^`\n]*`""").findAll(markdown)
            .forEach { protect(it.range.first, it.range.last + 1) }
        Regex("""\[\[[^\]\n]+]]""").findAll(markdown)
            .forEach { protect(it.range.first, it.range.last + 1) }
        Regex("""!?\[[^\]\n]*]\([^)]+\)""").findAll(markdown)
            .forEach { protect(it.range.first, it.range.last + 1) }

        val result = mutableListOf<Match>()
        var from = 0
        while (from <= markdown.length - title.length) {
            val index = markdown.indexOf(title, from, ignoreCase = true)
            if (index < 0) break
            val end = index + title.length
            if ((index until end).none { protected[it] }) result += Match(index, end)
            from = end.coerceAtLeast(index + 1)
        }
        return result
    }
}
