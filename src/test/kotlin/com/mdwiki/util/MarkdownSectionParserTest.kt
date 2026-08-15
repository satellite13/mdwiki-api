package com.mdwiki.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownSectionParserTest {

    @Test
    fun `parses frontmatter preamble and nested heading path`() {
        val md = """
            |---
            |title: Note
            |---
            |intro text
            |
            |# Architecture
            |overview
            |
            |## API
            |endpoint
            |
            |## Other
            |tail
        """.trimMargin()

        val sections = MarkdownSectionParser.parse(md)
        assertEquals(listOf("_frontmatter", "_preamble", "architecture", "architecture/api", "architecture/other"), sections.map { it.stableKey })
        assertEquals("Architecture::API", sections.single { it.stableKey == "architecture/api" }.headingPath)
        assertEquals(2, sections.single { it.stableKey == "architecture/api" }.headingLevel)
        assertEquals("intro text", md.substring(sections.single { it.stableKey == "_preamble" }.startOffset, sections.single { it.stableKey == "_preamble" }.endOffset).trim())
        assertTrue(md.substring(sections.single { it.stableKey == "architecture/api" }.startOffset, sections.single { it.stableKey == "architecture/api" }.endOffset).contains("## API"))
        val architecture = md.substring(
            sections.single { it.stableKey == "architecture" }.startOffset,
            sections.single { it.stableKey == "architecture" }.endOffset
        )
        assertTrue(architecture.contains("overview"))
        assertTrue(architecture.contains("## API"))
    }

    @Test
    fun `uses explicit heading id and suffixes colliding keys`() {
        val md = """
            |## API {#api-v1}
            |one
            |
            |## API
            |two
            |
            |## API
            |three
        """.trimMargin()

        val sections = MarkdownSectionParser.parse(md)
        assertEquals(listOf("api-v1", "api", "api-2"), sections.map { it.stableKey })
        assertEquals("API", sections[0].heading)
        assertEquals("api-v1", sections[0].explicitId)
    }

    @Test
    fun `body range starts after the heading line`() {
        val md = "## Title\nbody\n"
        val section = MarkdownSectionParser.parse(md).single()
        assertEquals("## Title\n", md.substring(section.startOffset, section.bodyStartOffset))
        assertEquals("body\n", md.substring(section.bodyStartOffset, section.endOffset))
    }

    @Test
    fun `ignores headings inside fenced code blocks`() {
        val md = """
            |# Real
            |before
            |
            |```md
            |# Fake
            |inside
            |```
            |
            |## Child
            |after
            |
            |~~~
            |# Also fake
            |~~~
        """.trimMargin()

        val sections = MarkdownSectionParser.parse(md)
        assertEquals(listOf("real", "real/child"), sections.map { it.stableKey })
        val real = md.substring(
            sections.single { it.stableKey == "real" }.startOffset,
            sections.single { it.stableKey == "real" }.endOffset
        )
        assertTrue(real.contains("# Fake"))
        assertTrue(real.contains("## Child"))
    }

    @Test
    fun `page without headings is a single preamble`() {
        val md = "just text"
        val sections = MarkdownSectionParser.parse(md)
        assertEquals(listOf("_preamble"), sections.map { it.stableKey })
        assertNull(sections[0].heading)
        assertEquals(0, sections[0].headingLevel)
        assertEquals("", sections[0].headingPath)
    }
}
