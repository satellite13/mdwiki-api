package com.mdwiki.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MarkdownContentOpsTest {

    @Test
    fun `replaces a unique exact fragment once`() {
        val result = MarkdownContentOps.replaceExact(
            content = "alpha\nbeta\ngamma",
            oldText = "beta",
            newText = "BETA",
            replaceAll = false
        )
        assertEquals("alpha\nBETA\ngamma", result.content)
        assertEquals(1, result.replacements)
    }

    @Test
    fun `rejects blank oldText`() {
        assertThrows<IllegalArgumentException> {
            MarkdownContentOps.replaceExact("hello", "  ", "x", replaceAll = false)
        }
    }

    @Test
    fun `rejects zero matches and includes a nearby snippet`() {
        val error = assertThrows<IllegalArgumentException> {
            MarkdownContentOps.replaceExact(
                content = "# Title\n\nThe quick brown fox\n\n## Other\n\nUnrelated",
                oldText = "The quick brown cat",
                newText = "changed",
                replaceAll = false
            )
        }
        assertTrue(error.message!!.contains("0 times") || error.message!!.contains("not found"))
        assertTrue(error.message!!.contains("quick brown fox"))
    }

    @Test
    fun `rejects multiple matches unless replaceAll`() {
        val error = assertThrows<IllegalArgumentException> {
            MarkdownContentOps.replaceExact("say foo and foo", "foo", "bar", replaceAll = false)
        }
        assertTrue(error.message!!.contains("2"))

        val replaced = MarkdownContentOps.replaceExact("say foo and foo", "foo", "bar", replaceAll = true)
        assertEquals("say bar and bar", replaced.content)
        assertEquals(2, replaced.replacements)
    }

    @Test
    fun `extracts a unique heading section including the heading line`() {
        val md = """
            |# Intro
            |before
            |
            |## API
            |first
            |
            |## Other
            |second
        """.trimMargin()
        val slice = MarkdownContentOps.extractSection(md, "API")
        assertEquals("## API\nfirst", slice.content.trim())
        assertEquals("API", slice.heading)
        assertFalse(slice.ambiguous)
    }

    @Test
    fun `rejects missing and duplicate headings`() {
        val md = "## API\none\n\n## API\ntwo"
        assertThrows<IllegalArgumentException> { MarkdownContentOps.extractSection(md, "Missing") }
        val dup = assertThrows<IllegalArgumentException> { MarkdownContentOps.extractSection(md, "API") }
        assertTrue(dup.message!!.contains("2") || dup.message!!.lowercase().contains("ambiguous"))
    }

    @Test
    fun `truncates content and reports remaining length`() {
        val slice = MarkdownContentOps.truncate("abcdefghij", maxChars = 4)
        assertEquals("abcd", slice.content)
        assertTrue(slice.truncated)
        assertEquals(10, slice.fullLength)
    }
}
