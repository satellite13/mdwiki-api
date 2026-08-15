package com.mdwiki.service

import com.mdwiki.dto.BrokenLinkKind
import com.mdwiki.model.Link
import com.mdwiki.model.Page
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.LinkRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class BrokenLinkServiceTest {

    @Mock private lateinit var pageRepository: PageRepository
    @Mock private lateinit var linkRepository: LinkRepository
    @Mock private lateinit var wikilinkService: WikilinkService
    @Mock private lateinit var pageMetadataService: PageMetadataService
    @Mock private lateinit var frontmatterMetaService: FrontmatterMetaService
    @Mock private lateinit var wikiFileService: WikiFileService
    @Mock private lateinit var ragService: com.mdwiki.rag.RagService
    @Mock private lateinit var pageIndexer: DeferredPageIndexer
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var sectionIndexService: SectionIndexService

    @InjectMocks
    private lateinit var service: BrokenLinkService

    @Test
    fun `listBroken returns dangling wikilinks and unresolved markdown links`() {
        val source = Page(id = UUID.randomUUID(), slug = "from", title = "From", contentMd = "[[ghost]]")
        val dangling = Link(id = UUID.randomUUID(), sourcePage = source, targetPage = null, targetSlug = "ghost")
        val target = Page(id = UUID.randomUUID(), slug = "real", title = "Real", contentMd = "# Real\n\n[ok](/page/real)")
        val brokenMd = Page(id = UUID.randomUUID(), slug = "broken-md", title = "Broken MD", contentMd = "# X\n\nSee [g](/page/ghost).")

        whenever(linkRepository.findAllDangling()).thenReturn(listOf(dangling))
        whenever(pageRepository.findAllByDeletedAtIsNull()).thenReturn(listOf(target, brokenMd))
        whenever(wikilinkService.extractWikilinks(any())).thenReturn(listOf(WikilinkService.Wikilink("ghost", null)))
        whenever(wikilinkService.extractInternalPageLinks(any())).thenReturn(emptyList())
        whenever(wikilinkService.extractInternalPageLinks(argThat { contains("/page/ghost") }))
            .thenReturn(listOf(WikilinkService.InternalPageLink("g", "ghost")))
        whenever(wikilinkService.resolvesToPage("ghost", listOf(target, brokenMd))).thenReturn(false)

        val result = service.listBroken()

        assertEquals(2, result.size)
        assertTrue(result.any { it.kind == BrokenLinkKind.WIKILINK && it.brokenTarget == "ghost" })
        assertTrue(result.any { it.kind == BrokenLinkKind.MARKDOWN && it.sourceSlug == "broken-md" })
    }

    @Test
    fun `listBroken ignores dangling wikilinks that only appear inside code`() {
        val source = Page(id = UUID.randomUUID(), slug = "from", title = "From", contentMd = "`[[ghost]]`")
        val dangling = Link(id = UUID.randomUUID(), sourcePage = source, targetPage = null, targetSlug = "ghost")

        whenever(linkRepository.findAllDangling()).thenReturn(listOf(dangling))
        whenever(pageRepository.findAllByDeletedAtIsNull()).thenReturn(listOf(source))
        whenever(wikilinkService.extractWikilinks(any())).thenReturn(emptyList())

        val result = service.listBroken()

        assertTrue(result.none { it.kind == BrokenLinkKind.WIKILINK })
    }

    @Test
    fun `rewriteBrokenLinks updates only source page when sourceSlug is set`() {
        val user = User(username = "ed", email = "e@x", passwordHash = "x", role = UserRole.EDITOR)
        val target = Page(id = UUID.randomUUID(), slug = "real", title = "Real")
        val source = Page(id = UUID.randomUUID(), slug = "from", title = "From", contentMd = "[[ghost]]")
        val rewritten = "[[real]]"

        whenever(pageRepository.findBySlugAndDeletedAtIsNull("real")).thenReturn(target)
        whenever(userRepository.findByUsername("ed")).thenReturn(user)
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("from")).thenReturn(source)
        whenever(frontmatterMetaService.isLocked(source)).thenReturn(false)
        whenever(wikilinkService.normalizePageSlug("ghost")).thenReturn("ghost")
        whenever(wikilinkService.rewriteWikilinksReferencingNormalizedSlug(any(), eq("ghost"), eq("real"), eq(null)))
            .thenReturn(rewritten)
        whenever(wikilinkService.rewriteInternalPageLinks(rewritten, "ghost", "real", null)).thenReturn(rewritten)
        whenever(pageRepository.save(source)).thenAnswer { it.arguments[0] as Page }

        val result = service.rewriteBrokenLinks("ghost", "real", "from", "ed")

        assertEquals(1, result.pagesUpdated)
        verify(pageMetadataService).syncLinksAndTags(any(), eq(rewritten), eq(false))
    }
}
