package com.mdwiki.util

object MarkdownFrontmatter {

    private data class Frontmatter(val yamlStart: Int, val yamlEnd: Int, val end: Int)

    private fun frontmatter(markdown: String): Frontmatter? {
        var lineEnd = markdown.indexOf('\n').let { if (it == -1) markdown.length else it }
        val opening = markdown.substring(0, lineEnd).removeSuffix("\r").removePrefix("\uFEFF")
        if (opening.trimEnd(' ', '\t') != "---" || lineEnd == markdown.length) return null
        val yamlStart = lineEnd + 1
        var cursor = yamlStart
        while (cursor <= markdown.length) {
            lineEnd = markdown.indexOf('\n', cursor).let { if (it == -1) markdown.length else it }
            val line = markdown.substring(cursor, lineEnd).removeSuffix("\r")
            if (line.trimEnd(' ', '\t') == "---") {
                return Frontmatter(yamlStart, cursor, if (lineEnd == markdown.length) lineEnd else lineEnd + 1)
            }
            if (lineEnd == markdown.length) return null
            cursor = lineEnd + 1
        }
        return null
    }

    /** Убирает YAML frontmatter в начале файла. */
    fun strip(markdown: String): String {
        val frontmatter = frontmatter(markdown) ?: return markdown
        return markdown.substring(frontmatter.end).dropWhile { it == '\r' || it == '\n' }
    }

    /** Сырой YAML между ограждениями (без внешних `---`), или `null`, если frontmatter нет. */
    fun extractYamlInner(markdown: String): String? {
        val frontmatter = frontmatter(markdown) ?: return null
        return markdown.substring(frontmatter.yamlStart, frontmatter.yamlEnd).trim()
    }

    /**
     * Заменяет только существующие простые YAML-поля внутри frontmatter. Внешние
     * ограждения, прочие строки YAML и тело документа возвращаются без изменений.
     */
    fun updateFields(markdown: String, fields: Map<String, String>): String {
        val frontmatter = frontmatter(markdown)
        if (frontmatter == null) {
            val yaml = fields.entries.joinToString("\n") { "${it.key}: ${it.value}" }
            return "---\n$yaml\n---\n$markdown"
        }
        var yaml = markdown.substring(frontmatter.yamlStart, frontmatter.yamlEnd)
        fields.forEach { (key, value) ->
            val line = topLevelFieldLine(yaml, key)
            if (line != null) {
                val lineEnd = yaml.indexOf('\n', line).let { if (it == -1) yaml.length else it }
                val contentEnd = if (lineEnd > line && yaml[lineEnd - 1] == '\r') lineEnd - 1 else lineEnd
                val lineEnding = yaml.substring(contentEnd, if (lineEnd < yaml.length) lineEnd + 1 else lineEnd)
                yaml = yaml.substring(0, line) + "$key: $value$lineEnding" +
                    yaml.substring(if (lineEnd < yaml.length) lineEnd + 1 else lineEnd)
            } else {
                val separator = if (yaml.isEmpty() || yaml.endsWith("\n") || yaml.endsWith("\r\n")) "" else "\n"
                yaml += "$separator$key: $value\n"
            }
        }
        return markdown.substring(0, frontmatter.yamlStart) + yaml + markdown.substring(frontmatter.yamlEnd)
    }

    fun removeFields(markdown: String, keys: Collection<String>): String {
        val frontmatter = frontmatter(markdown) ?: return markdown
        var yaml = markdown.substring(frontmatter.yamlStart, frontmatter.yamlEnd)
        keys.forEach { key ->
            val line = topLevelFieldLine(yaml, key) ?: return@forEach
            val lineEnd = yaml.indexOf('\n', line).let { if (it == -1) yaml.length else it }
            yaml = yaml.removeRange(line, if (lineEnd < yaml.length) lineEnd + 1 else lineEnd)
        }
        return markdown.substring(0, frontmatter.yamlStart) + yaml + markdown.substring(frontmatter.yamlEnd)
    }

    /** Finds an unindented canonical YAML mapping key; indented block scalars stay untouched. */
    private fun topLevelFieldLine(yaml: String, key: String): Int? {
        var cursor = 0
        while (cursor < yaml.length) {
            val lineEnd = yaml.indexOf('\n', cursor).let { if (it == -1) yaml.length else it }
            val line = yaml.substring(cursor, lineEnd).removeSuffix("\r")
            if (!line.startsWith(' ') && !line.startsWith('\t') &&
                line.startsWith(key) && line.length > key.length &&
                line[key.length] == ':' &&
                (line.length == key.length + 1 || line[key.length + 1].isWhitespace() || line[key.length + 1] == '#' || line[key.length + 1] == '"' || line[key.length + 1] == '\'')
            ) return cursor
            cursor = lineEnd + 1
        }
        return null
    }
}
