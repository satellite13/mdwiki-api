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

    /**
     * Заменяет только существующие простые YAML-поля внутри frontmatter. Внешние
     * ограждения, прочие строки YAML и тело документа возвращаются без изменений.
     */
    fun updateFields(markdown: String, fields: Map<String, String>): String {
        val match = matchFence(markdown)
        if (match == null) {
            val yaml = fields.entries.joinToString("\n") { "${it.key}: ${it.value}" }
            return "---\n$yaml\n---\n$markdown"
        }
        var yaml = match.groupValues[1]
        fields.forEach { (key, value) ->
            val line = Regex(
                """(?m)^([ \t]*${Regex.escape(key)}[ \t]*:)[^\r\n]*(\r?\n|${'$'})"""
            )
            val found = line.find(yaml)
            if (found != null) {
                val replacement = "${found.groupValues[1]} ${value}${found.groupValues[2]}"
                yaml = yaml.substring(0, found.range.first) + replacement +
                    yaml.substring(found.range.last + 1)
            } else {
                val separator = if (yaml.isEmpty() || yaml.endsWith("\n") || yaml.endsWith("\r\n")) "" else "\n"
                yaml += "$separator$key: $value\n"
            }
        }
        return markdown.substring(0, match.range.first) +
            markdown.substring(match.range.first, match.range.first + match.groupValues[0].length)
                .replace(match.groupValues[1], yaml) +
            markdown.substring(match.range.last + 1)
    }

    fun removeFields(markdown: String, keys: Collection<String>): String {
        val match = matchFence(markdown) ?: return markdown
        var yaml = match.groupValues[1]
        keys.forEach { key ->
            yaml = yaml.replace(Regex("""(?m)^[ \t]*${Regex.escape(key)}[ \t]*:[^\r\n]*(?:\r?\n|$)"""), "")
        }
        return markdown.substring(0, match.range.first) +
            match.groupValues[0].replace(match.groupValues[1], yaml) +
            markdown.substring(match.range.last + 1)
    }
}
