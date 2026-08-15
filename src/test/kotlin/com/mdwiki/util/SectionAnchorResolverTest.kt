package com.mdwiki.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SectionAnchorResolverTest {

    @Test
    fun `resolves unique heading to stable key`() {
        val md = "# Intro\nbefore\n\n## API\nendpoint\n"
        assertEquals("intro/api", SectionAnchorResolver.resolveKey(md, "API", "endpoint"))
    }

    @Test
    fun `picks smallest section that contains the chunk when headings collide`() {
        val md = """
            |# Intro
            |
            |## API
            |first endpoint
            |
            |# Other
            |
            |## API
            |second endpoint
        """.trimMargin()

        assertEquals("intro/api", SectionAnchorResolver.resolveKey(md, "API", "first endpoint"))
        assertEquals("other/api", SectionAnchorResolver.resolveKey(md, "API", "second endpoint"))
    }

    @Test
    fun `maps null heading to preamble`() {
        val md = "lead text\n\n# Title\nbody"
        assertEquals("_preamble", SectionAnchorResolver.resolveKey(md, null, "lead text"))
    }

    @Test
    fun `strips explicit id from RAG heading`() {
        val md = "## API {#api-v1}\nbody\n"
        assertEquals("api-v1", SectionAnchorResolver.resolveKey(md, "API {#api-v1}", "body"))
    }

    @Test
    fun `returns null when heading is unknown`() {
        assertNull(SectionAnchorResolver.resolveKey("# Intro\nbody", "Missing", "body"))
    }
}
