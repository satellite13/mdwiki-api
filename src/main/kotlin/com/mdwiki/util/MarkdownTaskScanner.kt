package com.mdwiki.util

data class MarkdownOpenTask(
    val taskText: String,
    val sourceOffset: Int,
    val sourceLine: String
)

object MarkdownTaskScanner {
    private val openTaskPattern = Regex("""^[ \t]*- \[ \][ \t]+(.*)$""")
    private val fencePattern = Regex("""^[ \t]*(`{3,}|~{3,})""")

    fun scan(content: String): List<MarkdownOpenTask> {
        val tasks = mutableListOf<MarkdownOpenTask>()
        var offset = 0
        var activeFence: String? = null

        while (offset < content.length) {
            val lineEnd = content.indexOf('\n', offset).let { if (it == -1) content.length else it }
            val rawLine = content.substring(offset, lineEnd)
            val line = rawLine.removeSuffix("\r")
            val fence = fencePattern.find(line)?.groupValues?.get(1)

            if (activeFence != null) {
                if (isClosingFence(line, activeFence)) {
                    activeFence = null
                }
            } else if (fence != null) {
                activeFence = fence
            } else {
                val match = openTaskPattern.matchEntire(line)
                if (match != null) {
                    tasks += MarkdownOpenTask(
                        taskText = match.groupValues[1].trim(),
                        sourceOffset = offset + line.indexOf("- [ ]"),
                        sourceLine = line
                    )
                }
            }

            if (lineEnd == content.length) break
            offset = lineEnd + 1
        }

        return tasks
    }

    private fun isClosingFence(line: String, activeFence: String): Boolean {
        val leadingSpaces = line.takeWhile { it == ' ' }.length
        if (leadingSpaces > 3 || line.getOrNull(leadingSpaces) == '\t') return false

        val fenceLine = line.substring(leadingSpaces)
        if (fenceLine.firstOrNull() != activeFence.first()) return false

        val fenceLength = fenceLine.indexOfFirst { it != activeFence.first() }
            .let { if (it == -1) fenceLine.length else it }
        return fenceLength >= activeFence.length && fenceLine.substring(fenceLength).all { it == ' ' || it == '\t' }
    }
}
