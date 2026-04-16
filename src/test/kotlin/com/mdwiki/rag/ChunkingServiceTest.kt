package com.mdwiki.rag

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ChunkingServiceTest {

    private val service = ChunkingService()

    @Test
    fun `splits markdown by headings`() {
        val md = """
            |# Main Title
            |
            |Introduction paragraph.
            |
            |## Section One
            |
            |Content of section one. It has multiple sentences.
            |More content here.
            |
            |## Section Two
            |
            |Content of section two.
        """.trimMargin()
        val chunks = service.chunk(md)
        assertEquals(3, chunks.size)
        assertEquals("Main Title", chunks[0].sectionHeading)
        assertTrue(chunks[0].text.contains("Introduction paragraph"))
        assertEquals("Section One", chunks[1].sectionHeading)
        assertEquals("Section Two", chunks[2].sectionHeading)
    }

    @Test
    fun `handles markdown with no headings`() {
        val chunks = service.chunk("Just a plain paragraph.\nAnother line.")
        assertEquals(1, chunks.size)
        assertNull(chunks[0].sectionHeading)
    }

    @Test
    fun `splits large sections into smaller chunks`() {
        val longContent = (1..100).joinToString(" ") { "Word$it is a sentence that adds some length." }
        val chunks = service.chunk("# Big Section\n\n$longContent")
        assertTrue(chunks.size > 1)
        chunks.forEach { assertTrue(it.text.length <= 2500) }
    }

    @Test
    fun `returns empty for blank input`() { assertTrue(service.chunk("").isEmpty()) }

    @Test
    fun `preserves chunk ordering`() {
        val chunks = service.chunk("# A\nText A\n## B\nText B\n## C\nText C")
        assertEquals(3, chunks.size)
        assertEquals(0, chunks[0].index)
        assertEquals(1, chunks[1].index)
        assertEquals(2, chunks[2].index)
    }

    @Test
    fun `ignores yaml frontmatter before headings`() {
        val md = """
            |---
            |title: X
            |---
            |# H1
            |Intro
        """.trimMargin()
        val chunks = service.chunk(md)
        assertEquals(1, chunks.size)
        assertEquals("H1", chunks[0].sectionHeading)
        assertTrue(chunks[0].text.contains("Intro"))
    }
}
