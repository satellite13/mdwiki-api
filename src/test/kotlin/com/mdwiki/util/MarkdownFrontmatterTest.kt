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

    @Test
    fun `updates only requested frontmatter field preserving unknown lines and body bytes`() {
        val body = "# Body\r\n\r\nA paragraph.\r\n"
        val markdown = "---\r\n# keep this comment\r\nstatus: draft\r\nunknown: untouched\r\n---\r\n$body"

        val updated = MarkdownFrontmatter.updateFields(markdown, mapOf("status" to "published"))

        assertEquals(
            "---\r\n# keep this comment\r\nstatus: published\r\nunknown: untouched\r\n---\r\n$body",
            updated
        )
    }

    @Test
    fun `updates a top level property without corrupting literal nested yaml`() {
        val markdown = "---\ndescription: |\n  priority: low\n---\nText"

        assertEquals(
            "---\ndescription: |\n  priority: low\npriority: high\n---\nText",
            MarkdownFrontmatter.updateFields(markdown, mapOf("priority" to "high"))
        )
    }

    @Test
    fun `updates and removes complete block scalar fields without changing surrounding bytes`() {
        val body = "\r\n# Body\r\nunchanged\r\n"
        val markdown = "---\r\ntitle: Keep\r\nnotes: >2-\r\n    first line\r\n      nested line\r\n    second line\r\nflag: true\r\n---\r\n$body"

        val updated = MarkdownFrontmatter.updateFields(markdown, mapOf("notes" to "\"replacement\""))
        assertEquals("---\r\ntitle: Keep\r\nnotes: \"replacement\"\r\nflag: true\r\n---\r\n$body", updated)

        val removed = MarkdownFrontmatter.removeFields(markdown, listOf("notes"))
        assertEquals("---\r\ntitle: Keep\r\nflag: true\r\n---\r\n$body", removed)
    }

    @Test
    fun `updates and removes every literal and folded block scalar indicator`() {
        listOf("|", "|-", "|+", "|2", "|2-", "|2+", ">", ">-", ">+", ">2", ">2-", ">2+").forEach { indicator ->
            val markdown = "---\nnotes: $indicator\n  first line\n\n  second line\nnext: unchanged\n---\nBody"

            assertEquals(
                "---\nnotes: \"replacement\"\nnext: unchanged\n---\nBody",
                MarkdownFrontmatter.updateFields(markdown, mapOf("notes" to "\"replacement\"")),
                "update $indicator"
            )
            assertEquals(
                "---\nnext: unchanged\n---\nBody",
                MarkdownFrontmatter.removeFields(markdown, listOf("notes")),
                "remove $indicator"
            )
        }
    }

    @Test
    fun `updates and removes complete multiline sequence field without dangling entries`() {
        val markdown = "---\n" +
            "title: Keep\n" +
            "tags:\n" +
            "  # legacy tags\n" +
            "  - first\n" +
            "  - second\n" +
            "status: draft\n" +
            "---\n" +
            "# Body\n"

        assertEquals(
            "---\ntitle: Keep\ntags: [replacement]\nstatus: draft\n---\n# Body\n",
            MarkdownFrontmatter.updateFields(markdown, mapOf("tags" to "[replacement]"))
        )
        assertEquals(
            "---\ntitle: Keep\nstatus: draft\n---\n# Body\n",
            MarkdownFrontmatter.removeFields(markdown, listOf("tags"))
        )
    }

    @Test
    fun `updates and removes complete nested mapping while preserving subsequent field and body`() {
        val markdown = "---\n" +
            "metadata:\n" +
            "  author: Ada\n" +
            "  details:\n" +
            "    # retain only while field exists\n" +
            "    topics:\n" +
            "      - kotlin\n" +
            "      - yaml\n" +
            "\n" +
            "  published: true\n" +
            "next: unchanged\n" +
            "---\n" +
            "Body stays\n"

        assertEquals(
            "---\nmetadata: {updated: true}\nnext: unchanged\n---\nBody stays\n",
            MarkdownFrontmatter.updateFields(markdown, mapOf("metadata" to "{updated: true}"))
        )
        assertEquals(
            "---\nnext: unchanged\n---\nBody stays\n",
            MarkdownFrontmatter.removeFields(markdown, listOf("metadata"))
        )
    }
}
