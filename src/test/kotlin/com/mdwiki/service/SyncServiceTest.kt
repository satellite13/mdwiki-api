package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.model.Folder
import com.mdwiki.model.Page
import com.mdwiki.rag.RagService
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.service.WikiFileService
import com.mdwiki.service.usecase.WikiSyncEngine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.Mockito.atLeast
import org.mockito.kotlin.doNothing
import org.mockito.ArgumentMatchers.isNull
import org.mockito.kotlin.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SyncServiceTest {

    @Mock private lateinit var pageRepository: PageRepository
    @Mock private lateinit var folderRepository: FolderRepository
    @Mock private lateinit var pageMetadataService: PageMetadataService
    @Mock private lateinit var ragService: RagService
    private val frontmatterMetaService = FrontmatterMetaService()
    @Mock private lateinit var fileWatcherService: FileWatcherService
    @Mock private lateinit var treeEventsService: TreeEventsService
    @Mock private lateinit var folderService: FolderService
    @Mock private lateinit var attachmentService: AttachmentService
    @Mock private lateinit var platformTransactionManager: PlatformTransactionManager
    @Mock private lateinit var sectionIndexService: SectionIndexService

    private lateinit var syncService: SyncService

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        val props = WikiProperties(contentDir = tempDir.toString())
        mockPagedFindAll(emptyList())
        whenever(folderRepository.findAll()).thenReturn(emptyList())
        whenever(folderRepository.findByParentId(isNull())).thenReturn(emptyList())
        whenever(folderRepository.save(any<Folder>())).thenAnswer { it.getArgument(0) }
        whenever(platformTransactionManager.getTransaction(any())).thenReturn(mock<TransactionStatus>())
        doNothing().whenever(platformTransactionManager).commit(any())
        doNothing().whenever(platformTransactionManager).rollback(any())
        val wikiFileService = WikiFileService(props, fileWatcherService, folderRepository)
        val wikiSyncEngine = WikiSyncEngine(
            pageRepository,
            pageMetadataService,
            props,
            ragService,
            frontmatterMetaService,
            folderRepository,
            wikiFileService,
            mock<DeferredPageIndexer>(),
            sectionIndexService
        )
        whenever(attachmentService.syncFromDisk()).thenReturn(AttachmentService.AttachmentSyncResult(0))
        syncService = SyncService(
            pageRepository,
            folderRepository,
            props,
            treeEventsService,
            folderService,
            wikiFileService,
            wikiSyncEngine,
            ragService,
            attachmentService,
            platformTransactionManager
        )
    }

    @Test
    fun `fullSync adds new files`() {
        File(tempDir.toFile(), "new-page.md").writeText("# New Page\nContent here")
        mockPagedFindAll(emptyList())
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }
        val result = syncService.fullSync()

        assertEquals(1, result.added)
        inOrder(pageRepository) {
            verify(pageRepository).acquireTransactionAdvisoryLock(MultiPageMutationLock.KEY)
            verify(pageRepository).findAll(any<Pageable>())
        }
        verify(pageRepository, atLeast(1)).save(argThat<Page> { slug == "new-page" && folder == null })
    }

    @Test
    fun `fullSync creates folder chain for nested markdown`() {
        val docs = File(tempDir.toFile(), "Docs").also { assertTrue(it.mkdirs()) }
        File(docs, "nested.md").writeText("# Nested\nx")
        mockPagedFindAll(emptyList())
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }
        val docId = UUID.randomUUID()
        val docFolder = Folder(id = docId, name = "Docs", parent = null)
        whenever(folderRepository.findByParentId(isNull())).thenReturn(emptyList())
        whenever(folderRepository.save(argThat<Folder> { name == "Docs" && parent == null })).thenReturn(docFolder)

        val result = syncService.fullSync()

        assertEquals(1, result.added)
        verify(pageRepository).save(argThat<Page> { slug == "nested" && folder?.id == docId })
    }

    @Test
    fun `fullSync removes deleted files`() {
        val page = Page(id = UUID.randomUUID(), slug = "deleted-page", title = "Deleted")
        page.filePath = tempDir.resolve("deleted-page.md").toString()
        mockPagedFindAll(listOf(page))

        val result = syncService.fullSync()

        assertEquals(1, result.removed)
        // Перед hard-delete должны быть зачищены и исходящие, и входящие ссылки,
        // иначе FK fk_links_target ломает удаление.
        verify(pageMetadataService).deleteSourceLinks(page)
        verify(pageMetadataService).detachIncomingLinks(page)
        verify(pageRepository).delete(page)
    }

    @Test
    fun `fullSync restores soft-deleted page when file still exists on disk`() {
        val file = File(tempDir.toFile(), "ghost.md")
        val body = "# Ghost\nbody"
        file.writeText(body)
        val page = Page(id = UUID.randomUUID(), slug = "ghost", title = "Ghost", contentMd = body)
        page.filePath = file.absolutePath
        page.deletedAt = Instant.now()
        mockPagedFindAll(listOf(page))
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }

        val result = syncService.fullSync()

        assertEquals(1, result.updated)
        verify(pageRepository).save(argThat<Page> { slug == "ghost" && deletedAt == null })
    }

    @Test
    fun `fullSync keeps soft-deleted page when its file is missing (trash)`() {
        val page = Page(id = UUID.randomUUID(), slug = "trashed", title = "Trashed")
        page.filePath = tempDir.resolve(".trash/trashed.md").toString()
        page.deletedAt = Instant.now()
        mockPagedFindAll(listOf(page))

        val result = syncService.fullSync()

        assertEquals(0, result.removed)
        verify(pageRepository, never()).delete(any<Page>())
    }

    @Test
    fun `fullSync ignores markdown files inside trash dir`() {
        val trash = File(tempDir.toFile(), ".trash").also { assertTrue(it.mkdirs()) }
        File(trash, "hidden.md").writeText("# Hidden\nx")
        mockPagedFindAll(emptyList())

        val result = syncService.fullSync()

        assertEquals(0, result.added)
    }

    @Test
    fun `fullSync updates modified files`() {
        val file = File(tempDir.toFile(), "modified.md")
        file.writeText("Updated content")

        val page = Page(id = UUID.randomUUID(), slug = "modified", title = "Modified", contentMd = "Old content")
        page.filePath = file.absolutePath
        mockPagedFindAll(listOf(page))
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }
        val result = syncService.fullSync()

        assertEquals(1, result.updated)
        verify(pageRepository, atLeast(1)).save(argThat<Page> { contentMd == "Updated content" })
    }

    @Test
    fun `fullSync iterates through paged existing pages`() {
        val firstPageEntity = Page(id = UUID.randomUUID(), slug = "stale-1", title = "Stale 1")
        val secondPageEntity = Page(id = UUID.randomUUID(), slug = "stale-2", title = "Stale 2")
        val page0 = PageImpl(listOf(firstPageEntity), PageRequest.of(0, 1), 2)
        val page1 = PageImpl(listOf(secondPageEntity), PageRequest.of(1, 1), 2)
        whenever(pageRepository.findAll(any<Pageable>())).thenReturn(page0, page1)

        val result = syncService.fullSync()

        assertEquals(2, result.removed)
        verify(pageRepository).delete(firstPageEntity)
        verify(pageRepository).delete(secondPageEntity)
        verify(pageRepository, atLeast(2)).findAll(any<Pageable>())
    }

    private fun mockPagedFindAll(pages: List<Page>) {
        whenever(pageRepository.findAll(any<Pageable>())).thenReturn(PageImpl(pages))
    }
}
