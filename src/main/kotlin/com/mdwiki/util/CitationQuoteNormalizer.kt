package com.mdwiki.util

object CitationQuoteNormalizer {
    private val wikiLink = Regex("""\[\[([^\]|]+)(?:\|([^\]]+))?]]""")
    private val mdLink = Regex("""\[([^\]]+)]\([^)]+\)""")
    private val headingPrefix = Regex("""^#{1,6}\s+""", RegexOption.MULTILINE)
    private val codeFence = Regex("""```[^\n]*\n?|```""")
    private val excessBlank = Regex("""\n{3,}""")
    private val excessSpace = Regex("""[ \t]{2,}""")

    fun normalize(raw: String, maxChars: Int = 480): String {
        var text = raw.trim()
        if (text.isEmpty()) return text
        text = wikiLink.replace(text) { match ->
            match.groupValues[2].ifBlank { match.groupValues[1] }.trim()
        }
        text = mdLink.replace(text) { it.groupValues[1].trim() }
        text = codeFence.replace(text, "")
        text = headingPrefix.replace(text, "")
        text = text.replace("\r\n", "\n").replace('\r', '\n')
        text = excessBlank.replace(text, "\n\n")
        text = excessSpace.replace(text, " ")
        text = text.lines().joinToString("\n") { it.trimEnd() }.trim()
        if (text.length <= maxChars) return text
        val cut = text.take(maxChars)
        val boundary = maxOf(cut.lastIndexOf(' '), cut.lastIndexOf('\n'))
        val prefix = if (boundary >= maxChars / 2) cut.take(boundary) else cut
        return prefix.trimEnd() + "…"
    }
}
