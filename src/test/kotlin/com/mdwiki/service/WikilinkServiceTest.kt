package com.mdwiki.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WikilinkServiceTest {

    private val svc = WikilinkService()

    @Test
    fun `rewriteWikilinksReferencingNormalizedSlug updates matching wikilinks and preserves labels`() {
        val md = "See [[mcp|doc]] and [[MCP]] and [[other]]."
        val out = svc.rewriteWikilinksReferencingNormalizedSlug(md, "mcp", "mcp-протокол")
        assertEquals("See [[mcp-протокол|doc]] and [[mcp-протокол]] and [[other]].", out)
    }

    @Test
    fun `normalizePageSlug keeps cyrillic letters`() {
        assertEquals("mcp-протокол", svc.normalizePageSlug("MCP протокол"))
    }
}
