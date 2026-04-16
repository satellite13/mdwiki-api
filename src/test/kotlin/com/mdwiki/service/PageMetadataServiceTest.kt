package com.mdwiki.service

import com.mdwiki.model.Link
import com.mdwiki.model.Page
import com.mdwiki.model.Tag
import com.mdwiki.repository.LinkRepository
import com.mdwiki.repository.PageRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
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
        whenever(wikilinkService.extractTags(content)).thenReturn(setOf("kotlin"))
        whenever(tagService.getOrCreateTags(setOf("kotlin"))).thenReturn(setOf(tag))
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }

        pageMetadataService.syncLinksAndTags(page, content, cleanupOrphanedTags = true)

        verify(pageRepository).findAllBySlugIn(setOf("known", "unknown"))
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
        whenever(linkRepository.findByTargetSlug("target")).thenReturn(listOf(danglingLink, resolvedLink))

        pageMetadataService.resolveIncomingLinks(page)

        assertEquals(page, danglingLink.targetPage)
        verify(linkRepository).save(danglingLink)
        verify(linkRepository, times(1)).save(any())
    }
}
