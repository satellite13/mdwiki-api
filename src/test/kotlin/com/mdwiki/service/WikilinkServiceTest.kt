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

    @Test
    fun `rewriteWikilinksReferencingNormalizedSlug handles cyrillic title pointing to ASCII slug`() {
        // Scenario: page has slug="schema" and title="Схема Вики"
        // Another page links to it via [[Схема Вики]] which normalizes to "схема-вики"
        // When renaming schema → wiki-schema, both [[schema]] and [[Схема Вики]] should update

        val md = "See [[schema]] and [[Схема Вики]] and [[other]]."
        val out = svc.rewriteWikilinksReferencingNormalizedSlug(
            md,
            oldNormalizedSlug = "schema",
            newSlug = "wiki-schema",
            oldNormalizedTitle = "схема-вики"
        )
        assertEquals("See [[wiki-schema]] and [[wiki-schema]] and [[other]].", out)
    }

    @Test
    fun `rewriteWikilinksReferencingNormalizedSlug preserves labels with cyrillic title`() {
        val md = "See [[Схема Вики|документация]]."
        val out = svc.rewriteWikilinksReferencingNormalizedSlug(
            md,
            oldNormalizedSlug = "schema",
            newSlug = "wiki-schema",
            oldNormalizedTitle = "схема-вики"
        )
        assertEquals("See [[wiki-schema|документация]].", out)
    }

    @Test
    fun `rewriteInternalPageLinks updates matching markdown page links`() {
        val md = "See [doc](/page/ghost) and [ok](/page/real)."
        val out = svc.rewriteInternalPageLinks(md, "ghost", "real-page")
        assertEquals("See [doc](/page/real-page) and [ok](/page/real).", out)
    }

    @Test
    fun `rewriteInternalPageLinks handles cyrillic slug in href`() {
        val md = "See [x](/page/${java.net.URLEncoder.encode("глава-17", Charsets.UTF_8)})."
        val out = svc.rewriteInternalPageLinks(md, "глава-17", "glava-17")
        assertEquals("See [x](/page/glava-17).", out)
    }

    @Test
    fun `extractWikilinks ignores wikilinks inside inline code and fenced blocks`() {
        val md = """
            Real [[page-a]] and `[[wikilinks]]` plus:
            ```
            [[ghost]]
            ```
        """.trimIndent()
        val links = svc.extractWikilinks(md)
        assertEquals(listOf("page-a"), links.map { it.slug })
    }

    @Test
    fun `rewriteWikilinksReferencingNormalizedSlug leaves matches inside code unchanged`() {
        val md = "[[mcp]] and `[[mcp]]` and ```\n[[mcp]]\n```"
        val out = svc.rewriteWikilinksReferencingNormalizedSlug(md, "mcp", "mcp-протокол")
        assertEquals("[[mcp-протокол]] and `[[mcp]]` and ```\n[[mcp]]\n```", out)
    }
}
