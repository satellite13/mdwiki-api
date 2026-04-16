package com.mdwiki.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WikilinkServiceTest {

    private val service = WikilinkService()

    @Test
    fun `extractWikilinks finds simple links`() {
        val md = "See [[my-page]] for details."
        val links = service.extractWikilinks(md)
        assertEquals(listOf(WikilinkService.Wikilink("my-page", null)), links)
    }

    @Test
    fun `extractWikilinks finds aliased links`() {
        val md = "See [[my-page|My Page]] for details."
        val links = service.extractWikilinks(md)
        assertEquals(listOf(WikilinkService.Wikilink("my-page", "My Page")), links)
    }

    @Test
    fun `extractWikilinks finds multiple links`() {
        val md = "Link to [[page-a]] and [[page-b|Page B]]."
        val links = service.extractWikilinks(md)
        assertEquals(2, links.size)
        assertEquals("page-a", links[0].slug)
        assertEquals("page-b", links[1].slug)
        assertEquals("Page B", links[1].displayText)
    }

    @Test
    fun `extractWikilinks returns empty for no links`() {
        val md = "No links here."
        val links = service.extractWikilinks(md)
        assertTrue(links.isEmpty())
    }

    @Test
    fun `extractWikilinks normalizes slug casing and spaces`() {
        val md = "[[MCP]] and [[My Page|Shown]]."
        val links = service.extractWikilinks(md)
        assertEquals(
            listOf(
                WikilinkService.Wikilink("mcp", null),
                WikilinkService.Wikilink("my-page", "Shown")
            ),
            links
        )
    }

    @Test
    fun `extractTags finds hashtags`() {
        val md = "This is about #kotlin and #spring-boot."
        val tags = service.extractTags(md)
        assertEquals(setOf("kotlin", "spring-boot"), tags)
    }

    @Test
    fun `extractTags ignores tags inside code blocks`() {
        val md = "Use `#comment` in code. Real #tag here."
        val tags = service.extractTags(md)
        assertEquals(setOf("tag"), tags)
    }

    @Test
    fun `extractTags supports cyrillic`() {
        val md = "Тема #разработка и #тест-кейс."
        val tags = service.extractTags(md)
        assertEquals(setOf("разработка", "тест-кейс"), tags)
    }
}
