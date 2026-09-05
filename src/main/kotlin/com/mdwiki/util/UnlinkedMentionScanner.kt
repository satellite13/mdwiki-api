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

        if (markdown.startsWith("---\n") || markdown.startsWith("---\r\n")) {
            val end = Regex("""(?m)^---[ \t]*\r?$""").find(markdown, 3)?.range?.last
            if (end != null) protect(0, end + 1)
        }
        val fence = Regex("""(?m)^[ \t]{0,3}(`{3,}|~{3,})[^\r\n]*\r?$""")
        var fenceSearch = 0
        while (true) {
            val open = fence.find(markdown, fenceSearch) ?: break
            val marker = open.groupValues[1]
            val close = Regex("""(?m)^[ \t]{0,3}${Regex.escape(marker.first().toString())}{${marker.length},}[ \t]*\r?$""")
                .find(markdown, open.range.last + 1)
            val end = close?.range?.last?.plus(1) ?: markdown.length
            protect(open.range.first, end)
            fenceSearch = end
        }
        var cursor = 0
        while (cursor < markdown.length) {
            if (markdown[cursor] != '`' || protected[cursor]) { cursor++; continue }
            var run = 1
            while (cursor + run < markdown.length && markdown[cursor + run] == '`') run++
            val close = markdown.indexOf("`".repeat(run), cursor + run)
            if (close >= 0) {
                protect(cursor, close + run)
                cursor = close + run
            } else cursor += run
        }
        Regex("""\[\[[^\]\n]+]]""").findAll(markdown)
            .forEach { protect(it.range.first, it.range.last + 1) }
        Regex("""<[^>\r\n]+>""").findAll(markdown)
            .forEach { protect(it.range.first, it.range.last + 1) }
        cursor = 0
        while (cursor < markdown.length) {
            if (markdown[cursor] != '[' || protected[cursor]) { cursor++; continue }
            var depth = 1
            var closeLabel = cursor + 1
            while (closeLabel < markdown.length && depth > 0 && markdown[closeLabel] != '\n') {
                if (markdown[closeLabel] == '[') depth++
                if (markdown[closeLabel] == ']') depth--
                closeLabel++
            }
            if (depth != 0 || closeLabel >= markdown.length) { cursor++; continue }
            val delimiter = markdown[closeLabel]
            if (delimiter == '(' || delimiter == '[') {
                val closing = if (delimiter == '(') ')' else ']'
                var nested = 1
                var end = closeLabel + 1
                while (end < markdown.length && nested > 0 && markdown[end] != '\n') {
                    if (markdown[end] == delimiter) nested++
                    if (markdown[end] == closing) nested--
                    end++
                }
                if (nested == 0) {
                    protect(if (cursor > 0 && markdown[cursor - 1] == '!') cursor - 1 else cursor, end)
                    cursor = end
                    continue
                }
            }
            cursor++
        }
        Regex("""(?m)^[ \t]{0,3}\[[^\]\n]+]:[ \t]*\S+.*$""").findAll(markdown)
            .forEach { protect(it.range.first, it.range.last + 1) }

        val result = mutableListOf<Match>()
        var from = 0
        while (from <= markdown.length - title.length) {
            val index = markdown.indexOf(title, from, ignoreCase = true)
            if (index < 0) break
            val end = index + title.length
            val leftOk = index == 0 || !title.first().isLetterOrDigit() ||
                !markdown.codePointBefore(index).isWordCodePoint()
            val rightOk = end == markdown.length || !title.last().isLetterOrDigit() ||
                !markdown.codePointAt(end).isWordCodePoint()
            if (leftOk && rightOk && (index until end).none { protected[it] }) result += Match(index, end)
            from = end.coerceAtLeast(index + 1)
        }
        return result
    }

    private fun Int.isWordCodePoint() = Character.isLetterOrDigit(this) || this == '_'.code
}
