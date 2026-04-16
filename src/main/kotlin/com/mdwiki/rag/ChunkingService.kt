package com.mdwiki.rag

import com.mdwiki.config.WikiProperties
import com.mdwiki.util.MarkdownFrontmatter
import org.springframework.stereotype.Service

@Service
class ChunkingService(
    private val wikiProperties: WikiProperties
) {

    data class Chunk(val index: Int, val text: String, val sectionHeading: String?)

    companion object {
        private val HEADING_PATTERN = Regex("""^#{1,3}\s+(.+)$""", RegexOption.MULTILINE)
    }

    private val maxChunkChars: Int get() = wikiProperties.rag.maxChunkChars

    fun chunk(markdown: String): List<Chunk> {
        val md = MarkdownFrontmatter.strip(markdown)
        if (md.isBlank()) return emptyList()
        val sections = splitBySections(md)
        val chunks = mutableListOf<Chunk>()
        var index = 0
        for (section in sections) {
            val sectionChunks = splitLargeSection(section.text, section.heading, index)
            chunks.addAll(sectionChunks)
            index += sectionChunks.size
        }
        return chunks
    }

    private data class Section(val heading: String?, val text: String)

    private fun splitBySections(markdown: String): List<Section> {
        val matches = HEADING_PATTERN.findAll(markdown).toList()
        if (matches.isEmpty()) return listOf(Section(heading = null, text = markdown.trim()))
        val sections = mutableListOf<Section>()
        val beforeFirst = markdown.substring(0, matches.first().range.first).trim()
        if (beforeFirst.isNotBlank()) sections.add(Section(heading = null, text = beforeFirst))
        for (i in matches.indices) {
            val heading = matches[i].groupValues[1].trim()
            val start = matches[i].range.last + 1
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else markdown.length
            val content = markdown.substring(start, end).trim()
            if (content.isNotBlank() || heading.isNotBlank()) {
                sections.add(Section(heading = heading, text = content))
            }
        }
        return sections
    }

    private fun splitLargeSection(text: String, heading: String?, startIndex: Int): List<Chunk> {
        if (text.length <= maxChunkChars) return listOf(Chunk(index = startIndex, text = text, sectionHeading = heading))
        val chunks = mutableListOf<Chunk>()
        val paragraphs = text.split(Regex("""\n\s*\n"""))
        var currentChunk = StringBuilder()
        var index = startIndex
        for (paragraph in paragraphs) {
            if (paragraph.length > maxChunkChars) {
                // Flush current chunk first
                if (currentChunk.isNotEmpty()) {
                    chunks.add(Chunk(index = index, text = currentChunk.toString().trim(), sectionHeading = heading))
                    index++
                    currentChunk = StringBuilder()
                }
                // Split oversized paragraph by words
                val words = paragraph.split(" ")
                for (word in words) {
                    if (currentChunk.length + word.length + 1 > maxChunkChars && currentChunk.isNotEmpty()) {
                        chunks.add(Chunk(index = index, text = currentChunk.toString().trim(), sectionHeading = heading))
                        index++
                        currentChunk = StringBuilder()
                    }
                    if (currentChunk.isNotEmpty()) currentChunk.append(" ")
                    currentChunk.append(word)
                }
            } else {
                if (currentChunk.length + paragraph.length > maxChunkChars && currentChunk.isNotEmpty()) {
                    chunks.add(Chunk(index = index, text = currentChunk.toString().trim(), sectionHeading = heading))
                    index++
                    currentChunk = StringBuilder()
                }
                if (currentChunk.isNotEmpty()) currentChunk.append("\n\n")
                currentChunk.append(paragraph)
            }
        }
        if (currentChunk.isNotEmpty()) chunks.add(Chunk(index = index, text = currentChunk.toString().trim(), sectionHeading = heading))
        return chunks
    }
}
