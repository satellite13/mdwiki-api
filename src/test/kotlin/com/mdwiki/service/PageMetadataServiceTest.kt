package com.mdwiki.service

import com.mdwiki.model.Link
import com.mdwiki.model.Page
import com.mdwiki.model.Tag
import com.mdwiki.repository.LinkRepository
import com.mdwiki.repository.PageRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PageMetadataServiceTest {
    @Mock private lateinit var pageRepository: PageRepository
    @Mock private lateinit var linkRepository: LinkRepository
    @Mock private lateinit var wikilinkService: WikilinkService
    @Mock private lateinit var tagService: TagService

    private lateinit var pageMetadataService: PageMetadataService

    @BeforeEach
    fun setUp() {
        pageMetadataService = PageMetadataService(pageRepository, linkRepository, wikilinkService, tagService)
    }

    @Test
    fun `findBacklinks includes links whose target slug matches normalized title`() {
        val target = Page(id = UUID.randomUUID(), slug = "mcp", title = "MCP протокол")
        val source = Page(id = UUID.randomUUID(), slug = "source", title = "Source")
        val linkByTitle = Link(id = UUID.randomUUID(), sourcePage = source, targetPage = target, targetSlug = "mcp-протокол")
        whenever(pageRepository.findBySlug("mcp")).thenReturn(target)
        whenever(wikilinkService.normalizePageSlug("MCP протокол")).thenReturn("mcp-протокол")
        whenever(linkRepository.findByTargetSlug("mcp")).thenReturn(emptyList())
        whenever(linkRepository.findByTargetSlug("mcp-протокол")).thenReturn(listOf(linkByTitle))

        val backlinks = pageMetadataService.findBacklinks("mcp")

        assertEquals(1, backlinks.size)
        assertEquals(linkByTitle, backlinks.single())
    }

    @Test
    fun `findBacklinks merges slug and normalized title without duplicates`() {
        val target = Page(id = UUID.randomUUID(), slug = "mcp", title = "MCP протокол")
        val source = Page(id = UUID.randomUUID(), slug = "source", title = "Source")
        val sameLink = Link(id = UUID.randomUUID(), sourcePage = source, targetPage = target, targetSlug = "mcp")
        whenever(pageRepository.findBySlug("mcp")).thenReturn(target)
        whenever(wikilinkService.normalizePageSlug("MCP протокол")).thenReturn("mcp-протокол")
        whenever(linkRepository.findByTargetSlug("mcp")).thenReturn(listOf(sameLink))
        whenever(linkRepository.findByTargetSlug("mcp-протокол")).thenReturn(listOf(sameLink))

        val backlinks = pageMetadataService.findBacklinks("mcp")

        assertEquals(1, backlinks.size)
    }

    @Test
    fun `findBacklinks hides links from soft-deleted source pages`() {
        val target = Page(id = UUID.randomUUID(), slug = "target", title = "Target")
        val alive = Page(id = UUID.randomUUID(), slug = "alive", title = "Alive")
        val deleted = Page(id = UUID.randomUUID(), slug = "ghost", title = "Ghost")
            .apply { deletedAt = Instant.now() }
        val aliveLink = Link(id = UUID.randomUUID(), sourcePage = alive, targetPage = target, targetSlug = "target")
        val ghostLink = Link(id = UUID.randomUUID(), sourcePage = deleted, targetPage = target, targetSlug = "target")
        whenever(pageRepository.findBySlug("target")).thenReturn(target)
        whenever(wikilinkService.normalizePageSlug("Target")).thenReturn("target")
        whenever(linkRepository.findByTargetSlug("target")).thenReturn(listOf(aliveLink, ghostLink))

        val backlinks = pageMetadataService.findBacklinks("target")

        assertEquals(listOf(aliveLink), backlinks)
    }

    @Test
    fun `findBacklinks deduplicates multiple links from same source page`() {
        val target = Page(id = UUID.randomUUID(), slug = "target", title = "Target")
        val source = Page(id = UUID.randomUUID(), slug = "source", title = "Source")
        val firstLink = Link(id = UUID.randomUUID(), sourcePage = source, targetPage = target, targetSlug = "target")
        val secondLink = Link(id = UUID.randomUUID(), sourcePage = source, targetPage = target, targetSlug = "target")
        whenever(pageRepository.findBySlug("target")).thenReturn(target)
        whenever(wikilinkService.normalizePageSlug("Target")).thenReturn("target")
        whenever(linkRepository.findByTargetSlug("target")).thenReturn(listOf(firstLink, secondLink))

        val backlinks = pageMetadataService.findBacklinks("target")

        assertEquals(1, backlinks.size)
        assertTrue(backlinks.single() === firstLink || backlinks.single() === secondLink)
    }

    @Test
    fun `findBacklinks falls back to slug only when page is missing`() {
        val source = Page(id = UUID.randomUUID(), slug = "source", title = "Source")
        val link = Link(id = UUID.randomUUID(), sourcePage = source, targetPage = null, targetSlug = "orphan")
        whenever(pageRepository.findBySlug("orphan")).thenReturn(null)
        whenever(linkRepository.findByTargetSlug("orphan")).thenReturn(listOf(link))

        val backlinks = pageMetadataService.findBacklinks("orphan")

        assertEquals(listOf(link), backlinks)
        verify(linkRepository).findByTargetSlug("orphan")
    }

    @Test
    fun `syncLinksAndTags resolves link targets in batch and updates tags`() {
        val page = Page(id = UUID.randomUUID(), slug = "source", title = "Source")
        val targetPage = Page(id = UUID.randomUUID(), slug = "known", title = "Known")
        val content = "[[known]] [[unknown]] #kotlin"
        val tag = Tag(name = "kotlin")

        whenever(wikilinkService.extractWikilinks(content)).thenReturn(
            listOf(
                WikilinkService.Wikilink(slug = "known", displayText = null),
                WikilinkService.Wikilink(slug = "unknown", displayText = null)
            )
        )
        whenever(pageRepository.findAllBySlugIn(setOf("known", "unknown"))).thenReturn(listOf(targetPage))
        whenever(pageRepository.findByNormalizedTitle("unknown")).thenReturn(null)
        whenever(wikilinkService.extractTags(content)).thenReturn(setOf("kotlin"))
        whenever(tagService.getOrCreateTags(setOf("kotlin"))).thenReturn(setOf(tag))
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }

        pageMetadataService.syncLinksAndTags(page, content, cleanupOrphanedTags = true)

        verify(pageRepository).findAllBySlugIn(setOf("known", "unknown"))
        verify(pageRepository).findByNormalizedTitle("unknown")
        val linksCaptor = argumentCaptor<Link>()
        verify(linkRepository, times(2)).save(linksCaptor.capture())
        assertEquals(targetPage, linksCaptor.firstValue.targetPage)
        assertNull(linksCaptor.secondValue.targetPage)
        assertEquals(setOf(tag), page.tags)
        verify(tagService).cleanupOrphanedTags()
    }

    @Test
    fun `resolveIncomingLinks updates only dangling links`() {
        val page = Page(id = UUID.randomUUID(), slug = "target", title = "Target")
        val source = Page(id = UUID.randomUUID(), slug = "source", title = "Source")
        val danglingLink = Link(sourcePage = source, targetPage = null, targetSlug = "target")
        val resolvedLink = Link(sourcePage = source, targetPage = page, targetSlug = "target")
        whenever(wikilinkService.normalizePageSlug("Target")).thenReturn("target")
        whenever(linkRepository.findByTargetSlug("target")).thenReturn(listOf(danglingLink, resolvedLink))

        pageMetadataService.resolveIncomingLinks(page)

        assertEquals(page, danglingLink.targetPage)
        verify(linkRepository).save(danglingLink)
        verify(linkRepository, times(1)).save(any())
    }

    @Test
    fun `syncLinksAndTags resolves target by normalized title when slug misses`() {
        val page = Page(id = UUID.randomUUID(), slug = "source", title = "Source")
        val targetPage = Page(id = UUID.randomUUID(), slug = "mcp", title = "MCP протокол")
        val content = "[[mcp-протокол]]"
        whenever(wikilinkService.extractWikilinks(content)).thenReturn(
            listOf(WikilinkService.Wikilink(slug = "mcp-протокол", displayText = null))
        )
        whenever(pageRepository.findAllBySlugIn(setOf("mcp-протокол"))).thenReturn(emptyList())
        whenever(pageRepository.findByNormalizedTitle("mcp-протокол")).thenReturn(targetPage)
        whenever(wikilinkService.extractTags(content)).thenReturn(emptySet())
        whenever(tagService.getOrCreateTags(emptySet())).thenReturn(emptySet())
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }

        pageMetadataService.syncLinksAndTags(page, content, cleanupOrphanedTags = false)

        verify(pageRepository).findByNormalizedTitle("mcp-протокол")
        val linkCaptor = argumentCaptor<Link>()
        verify(linkRepository).save(linkCaptor.capture())
        assertEquals(targetPage, linkCaptor.firstValue.targetPage)
        assertEquals("mcp-протокол", linkCaptor.firstValue.targetSlug)
    }

    @Test
    fun `detachIncomingLinks nulls targetPage while keeping targetSlug`() {
        val target = Page(id = UUID.randomUUID(), slug = "target", title = "Target")
        val source = Page(id = UUID.randomUUID(), slug = "source", title = "Source")
        val incoming = Link(id = UUID.randomUUID(), sourcePage = source, targetPage = target, targetSlug = "target")
        whenever(linkRepository.findByTargetPage(target)).thenReturn(listOf(incoming))

        pageMetadataService.detachIncomingLinks(target)

        assertNull(incoming.targetPage)
        assertEquals("target", incoming.targetSlug)
        verify(linkRepository).save(incoming)
    }

    @Test
    fun `detachIncomingLinks is a no-op when there are no incoming links`() {
        val page = Page(id = UUID.randomUUID(), slug = "lonely", title = "Lonely")
        whenever(linkRepository.findByTargetPage(page)).thenReturn(emptyList())

        pageMetadataService.detachIncomingLinks(page)

        verify(linkRepository, never()).save(any<Link>())
    }

    @Test
    fun `resolveIncomingLinks resolves dangling links by normalized title`() {
        val page = Page(id = UUID.randomUUID(), slug = "mcp", title = "MCP протокол")
        val source = Page(id = UUID.randomUUID(), slug = "source", title = "Source")
        val danglingLink = Link(sourcePage = source, targetPage = null, targetSlug = "mcp-протокол")
        whenever(wikilinkService.normalizePageSlug("MCP протокол")).thenReturn("mcp-протокол")
        whenever(linkRepository.findByTargetSlug("mcp")).thenReturn(emptyList())
        whenever(linkRepository.findByTargetSlug("mcp-протокол")).thenReturn(listOf(danglingLink))

        pageMetadataService.resolveIncomingLinks(page)

        assertEquals(page, danglingLink.targetPage)
        verify(linkRepository).save(danglingLink)
    }
}
