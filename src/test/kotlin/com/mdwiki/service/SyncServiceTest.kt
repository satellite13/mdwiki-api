package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.model.Page
import com.mdwiki.repository.LinkRepository
import com.mdwiki.repository.PageRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.Mockito.atLeast
import org.mockito.kotlin.*
import java.io.File
import java.nio.file.Path
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SyncServiceTest {

    @Mock private lateinit var pageRepository: PageRepository
    @Mock private lateinit var linkRepository: LinkRepository
    @Mock private lateinit var wikilinkService: WikilinkService
    @Mock private lateinit var tagService: TagService

    private lateinit var syncService: SyncService

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        val props = WikiProperties(contentDir = tempDir.toString())
        syncService = SyncService(pageRepository, linkRepository, wikilinkService, tagService, props)
    }

    @Test
    fun `fullSync adds new files`() {
        File(tempDir.toFile(), "new-page.md").writeText("# New Page\nContent here")
        whenever(pageRepository.findAll()).thenReturn(emptyList())
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }
        whenever(wikilinkService.extractWikilinks(any())).thenReturn(emptyList())
        whenever(wikilinkService.extractTags(any())).thenReturn(emptySet())

        val result = syncService.fullSync()

        assertEquals(1, result.added)
        verify(pageRepository, atLeast(1)).save(argThat<Page> { slug == "new-page" })
    }

    @Test
    fun `fullSync removes deleted files`() {
        val page = Page(id = UUID.randomUUID(), slug = "deleted-page", title = "Deleted")
        page.filePath = tempDir.resolve("deleted-page.md").toString()
        whenever(pageRepository.findAll()).thenReturn(listOf(page))

        val result = syncService.fullSync()

        assertEquals(1, result.removed)
        verify(pageRepository).delete(page)
    }

    @Test
    fun `fullSync updates modified files`() {
        val file = File(tempDir.toFile(), "modified.md")
        file.writeText("Updated content")

        val page = Page(id = UUID.randomUUID(), slug = "modified", title = "Modified", contentMd = "Old content")
        page.filePath = file.absolutePath
        whenever(pageRepository.findAll()).thenReturn(listOf(page))
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }
        whenever(wikilinkService.extractWikilinks(any())).thenReturn(emptyList())
        whenever(wikilinkService.extractTags(any())).thenReturn(emptySet())

        val result = syncService.fullSync()

        assertEquals(1, result.updated)
        verify(pageRepository, atLeast(1)).save(argThat<Page> { contentMd == "Updated content" })
    }
}
