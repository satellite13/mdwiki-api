package com.mdwiki.service

import com.mdwiki.model.Page
import com.mdwiki.model.PageSection
import com.mdwiki.repository.PageSectionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SectionIndexServiceTest {
    @Mock private lateinit var pageSectionRepository: PageSectionRepository
    @Captor private lateinit var saved: ArgumentCaptor<List<PageSection>>

    @Test
    fun `inserts parsed sections and deletes stale keys`() {
        val page = page("# Keep\nold\n\n# Gone\nbye")
        val stale = PageSection(
            id = UUID.randomUUID(),
            page = page,
            stableKey = "gone",
            heading = "Gone",
            headingLevel = 1,
            headingPath = "Gone",
            sortOrder = 1,
            startOffset = 0,
            endOffset = 1,
            contentHash = "old"
        )
        val keep = PageSection(
            id = UUID.randomUUID(),
            page = page,
            stableKey = "keep",
            heading = "Keep",
            headingLevel = 1,
            headingPath = "Keep",
            sortOrder = 0,
            startOffset = 0,
            endOffset = 1,
            contentHash = "old"
        )
        whenever(pageSectionRepository.findByPageIdOrderBySortOrder(page.id!!)).thenReturn(listOf(keep, stale))
        whenever(pageSectionRepository.saveAll(any<List<PageSection>>())).thenAnswer { it.arguments[0] }

        val updated = "# Keep\nnew"
        SectionIndexService(pageSectionRepository).rebuild(page, updated)

        verify(pageSectionRepository).deleteAll(listOf(stale))
        verify(pageSectionRepository).saveAll(saved.capture())
        val upserted = saved.value
        assertEquals(listOf("keep"), upserted.map { it.stableKey })
        assertEquals(keep.id, upserted.single().id)
        assertEquals("new", updated.substring(upserted.single().startOffset, upserted.single().endOffset).substringAfter('\n').trim())
    }

    @Test
    fun `rematch by heading path when key changes`() {
        val page = page("# API\nbody")
        val existing = PageSection(
            id = UUID.randomUUID(),
            page = page,
            stableKey = "old-key",
            heading = "API",
            headingLevel = 1,
            headingPath = "API",
            sortOrder = 0,
            startOffset = 0,
            endOffset = 1,
            contentHash = "x"
        )
        whenever(pageSectionRepository.findByPageIdOrderBySortOrder(page.id!!)).thenReturn(listOf(existing))
        whenever(pageSectionRepository.saveAll(any<List<PageSection>>())).thenAnswer { it.arguments[0] }

        SectionIndexService(pageSectionRepository).rebuild(page, "# API\nbody")

        verify(pageSectionRepository, never()).deleteAll(any<List<PageSection>>())
        verify(pageSectionRepository).saveAll(saved.capture())
        assertEquals(existing.id, saved.value.single().id)
        assertEquals("api", saved.value.single().stableKey)
    }

    @Test
    fun `listOrRebuild returns existing rows when they match live markdown`() {
        val page = page("# API\nbody")
        val existing = PageSection(
            id = UUID.randomUUID(),
            page = page,
            stableKey = "api",
            heading = "API",
            headingLevel = 1,
            headingPath = "API",
            sortOrder = 0,
            startOffset = 0,
            endOffset = page.contentMd!!.length,
            contentHash = SectionIndexService.hashOf(page.contentMd!!, 0, page.contentMd!!.length)
        )
        whenever(pageSectionRepository.findByPageIdOrderBySortOrder(page.id!!)).thenReturn(listOf(existing))

        val listed = SectionIndexService(pageSectionRepository).listOrRebuild(page)

        assertEquals(listOf(existing), listed)
        verify(pageSectionRepository, never()).saveAll(any<List<PageSection>>())
    }

    @Test
    fun `listOrRebuild rebuilds when stored offsets no longer match content`() {
        val page = page("# API\nchanged body")
        val stale = PageSection(
            id = UUID.randomUUID(),
            page = page,
            stableKey = "api",
            heading = "API",
            headingLevel = 1,
            headingPath = "API",
            sortOrder = 0,
            startOffset = 0,
            endOffset = 3,
            contentHash = "stale"
        )
        whenever(pageSectionRepository.findByPageIdOrderBySortOrder(page.id!!)).thenReturn(listOf(stale))
        whenever(pageSectionRepository.saveAll(any<List<PageSection>>())).thenAnswer { it.arguments[0] }

        SectionIndexService(pageSectionRepository).listOrRebuild(page)

        verify(pageSectionRepository).saveAll(saved.capture())
        assertEquals("api", saved.value.single().stableKey)
        assertEquals(page.contentMd!!.length, saved.value.single().endOffset)
    }

    private fun page(content: String) = Page(
        id = UUID.randomUUID(),
        slug = "note",
        title = "Note",
        contentMd = content
    )
}
