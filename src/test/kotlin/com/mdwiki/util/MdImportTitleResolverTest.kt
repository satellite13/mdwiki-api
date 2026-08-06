package com.mdwiki.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MdImportTitleResolverTest {

    @Test
    fun `title from frontmatter wins over h1 and filename`() {
        val content = """
            ---
            title: From Frontmatter
            ---
            # From H1
            body
        """.trimIndent()
        assertEquals("From Frontmatter", MdImportTitleResolver.resolveTitle(content, "file-stem"))
    }

    @Test
    fun `title from h1 when no frontmatter title`() {
        val content = """
            ---
            tags: [a]
            ---
            # Heading One
            body
        """.trimIndent()
        assertEquals("Heading One", MdImportTitleResolver.resolveTitle(content, "file-stem"))
    }

    @Test
    fun `title from filename stem when no frontmatter or h1`() {
        assertEquals("My Note", MdImportTitleResolver.resolveTitle("just text", "My Note"))
    }

    @Test
    fun `filename stem strips md and markdown extensions`() {
        assertEquals("My Note", MdImportTitleResolver.filenameStem("My Note.md"))
        assertEquals("doc", MdImportTitleResolver.filenameStem("path/to/doc.markdown"))
    }

    @Test
    fun `isMarkdownFilename accepts md and markdown only`() {
        assertTrue(MdImportTitleResolver.isMarkdownFilename("a.md"))
        assertTrue(MdImportTitleResolver.isMarkdownFilename("a.MARKDOWN"))
        assertFalse(MdImportTitleResolver.isMarkdownFilename("a.txt"))
    }
}
