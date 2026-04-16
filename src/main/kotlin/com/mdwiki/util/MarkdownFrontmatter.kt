package com.mdwiki.util

object MarkdownFrontmatter {

    /**
     * Закрывающий `---` только как отдельная строка ([MULTILINE] `^`),
     * чтобы сработало и пустое тело (`---\n---\n`).
     */
    private val fence = Regex(
        """^\uFEFF?---[ \t]*\r?\n([\s\S]*?)^---[ \t]*(?:\r?\n|$)""",
        setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
    )

    private fun matchFence(markdown: String): MatchResult? = fence.find(markdown)

    /** Убирает YAML frontmatter в начале файла. */
    fun strip(markdown: String): String {
        val m = matchFence(markdown) ?: return markdown
        return markdown.substring(m.range.last + 1).dropWhile { it == '\r' || it == '\n' }
    }

    /** Сырой YAML между ограждениями (без внешних `---`), или `null`, если frontmatter нет. */
    fun extractYamlInner(markdown: String): String? {
        val m = matchFence(markdown) ?: return null
        return m.groupValues[1].trim()
    }
}
