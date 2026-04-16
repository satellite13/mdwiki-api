package com.mdwiki.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MarkdownFrontmatterTest {

    @Test
    fun `strips yaml block at file start`() {
        val md = """
            |---
            |title: Hello
            |tags: [a, b]
            |---
            |
            |# Body
            |Text
        """.trimMargin()
        assertEquals(
            """
            |# Body
            |Text
            """.trimMargin(),
            MarkdownFrontmatter.strip(md)
        )
    }

    @Test
    fun `leaves horizontal rule in body`() {
        val md = "# T\n\n---\n\nmore"
        assertEquals(md, MarkdownFrontmatter.strip(md))
    }

    @Test
    fun `handles BOM`() {
        val md = "\uFEFF---\nlayout: x\n---\n\nHi"
        assertEquals("Hi", MarkdownFrontmatter.strip(md))
    }

    @Test
    fun `no closing fence returns original`() {
        val md = "---\nopen: true\n# still open"
        assertEquals(md, MarkdownFrontmatter.strip(md))
    }

    @Test
    fun `strips empty frontmatter block`() {
        val md = "---\n---\n\n# Hi"
        assertEquals("# Hi", MarkdownFrontmatter.strip(md))
    }

    @Test
    fun `extractYamlInner returns null without block`() {
        assertNull(MarkdownFrontmatter.extractYamlInner("# x"))
    }

    @Test
    fun `extractYamlInner returns trimmed yaml`() {
        val md = "---\nx: 1\n---\n\nz"
        assertEquals("x: 1", MarkdownFrontmatter.extractYamlInner(md))
    }
}
