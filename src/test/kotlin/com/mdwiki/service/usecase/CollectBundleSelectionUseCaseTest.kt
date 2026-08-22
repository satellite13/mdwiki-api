package com.mdwiki.service.usecase

import com.mdwiki.config.WikiProperties
import com.mdwiki.dto.BundleExportRequest
import com.mdwiki.model.Attachment
import com.mdwiki.model.Folder
import com.mdwiki.model.Page
import com.mdwiki.repository.AttachmentRepository
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.PageRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CollectBundleSelectionUseCaseTest {

    @Mock private lateinit var folderRepository: FolderRepository
    @Mock private lateinit var pageRepository: PageRepository
    @Mock private lateinit var attachmentRepository: AttachmentRepository

    @TempDir lateinit var contentRoot: Path

    private lateinit var useCase: CollectBundleSelectionUseCase

    private val bookId = UUID.randomUUID()
    private val ch1Id = UUID.randomUUID()
    private val otherId = UUID.randomUUID()

    private lateinit var book: Folder
    private lateinit var ch1: Folder
    private lateinit var other: Folder
    private lateinit var intro: Page
    private lateinit var chapter1: Page
    private lateinit var otherPage: Page

    @BeforeEach
    fun setUp() {
        useCase = CollectBundleSelectionUseCase(
            folderRepository,
            pageRepository,
            attachmentRepository,
            WikiProperties(contentDir = contentRoot.toString())
        )
        book = Folder(id = bookId, name = "Book")
        ch1 = Folder(id = ch1Id, name = "Ch1", parent = book)
        other = Folder(id = otherId, name = "Other")
        intro = Page(id = UUID.randomUUID(), slug = "intro", title = "Intro", contentMd = "hi", folder = book)
        chapter1 = Page(
            id = UUID.randomUUID(),
            slug = "chapter-1",
            title = "Chapter 1",
            contentMd = "see ![](/api/uploads/pic.png)",
            folder = ch1
        )
        otherPage = Page(id = UUID.randomUUID(), slug = "other", title = "Other", contentMd = "x", folder = other)
    }

    private fun stubTree() {
        whenever(folderRepository.findAll()).thenReturn(listOf(book, ch1, other))
        whenever(pageRepository.findByFolderId(bookId)).thenReturn(listOf(intro))
        whenever(pageRepository.findByFolderId(ch1Id)).thenReturn(listOf(chapter1))
    }

    @Test
    fun `selecting folder expands descendants and uses relative folder paths`() {
        stubTree()
        whenever(attachmentRepository.findByPageIdIn(any())).thenReturn(emptyList())
        whenever(attachmentRepository.findByStoredName("pic.png")).thenReturn(
            Attachment(
                id = UUID.randomUUID(),
                originalName = "diagram.png",
                storedName = "pic.png",
                contentType = "image/png",
                sizeBytes = 4
            )
        )
        Files.createDirectories(contentRoot.resolve("uploads"))
        Files.write(contentRoot.resolve("uploads/pic.png"), byteArrayOf(1, 2, 3, 4))

        val result = useCase.execute(BundleExportRequest(folderIds = listOf(bookId)))

        assertEquals(listOf(listOf("Book"), listOf("Book", "Ch1")), result.folders.map { it.path })
        assertEquals(setOf("intro", "chapter-1"), result.pages.map { it.slug }.toSet())
        assertEquals(listOf("Book"), result.pages.first { it.slug == "intro" }.folderPath)
        assertEquals(listOf("Book", "Ch1"), result.pages.first { it.slug == "chapter-1" }.folderPath)
        assertEquals(1, result.attachments.size)
        assertEquals("pic.png", result.attachments[0].storedName)
        assertEquals("diagram.png", result.attachments[0].originalName)
        assertTrue(result.warnings.isEmpty())
        assertTrue(otherPage.slug !in result.pages.map { it.slug })
    }

    @Test
    fun `selecting a page without its folder puts it at bundle root`() {
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("chapter-1")).thenReturn(chapter1)
        whenever(attachmentRepository.findByPageIdIn(any())).thenReturn(emptyList())
        whenever(attachmentRepository.findByStoredName("pic.png")).thenReturn(null)

        val result = useCase.execute(BundleExportRequest(pageSlugs = listOf("chapter-1")))

        assertTrue(result.folders.isEmpty())
        assertEquals(listOf<String>(), result.pages.single().folderPath)
        assertTrue(result.warnings.any { it.contains("pic.png") })
        assertTrue(result.attachments.isEmpty())
    }

    @Test
    fun `includes attachments linked by page_id even when markdown has no url`() {
        val orphanFile = Attachment(
            id = UUID.randomUUID(),
            originalName = "notes.pdf",
            storedName = "notes.pdf",
            contentType = "application/pdf",
            sizeBytes = 2,
            page = intro
        )
        stubTree()
        whenever(attachmentRepository.findByPageIdIn(any())).thenReturn(listOf(orphanFile))
        whenever(attachmentRepository.findByStoredName("pic.png")).thenReturn(null)
        Files.createDirectories(contentRoot.resolve("uploads"))
        Files.write(contentRoot.resolve("uploads/notes.pdf"), byteArrayOf(9, 9))

        val result = useCase.execute(BundleExportRequest(folderIds = listOf(bookId)))

        assertEquals(setOf("notes.pdf"), result.attachments.map { it.storedName }.toSet())
    }

    @Test
    fun `nested selected folder is absorbed by ancestor selection`() {
        stubTree()
        whenever(attachmentRepository.findByPageIdIn(any())).thenReturn(emptyList())
        whenever(attachmentRepository.findByStoredName("pic.png")).thenReturn(null)

        val result = useCase.execute(BundleExportRequest(folderIds = listOf(bookId, ch1Id)))

        assertEquals(listOf(listOf("Book"), listOf("Book", "Ch1")), result.folders.map { it.path })
        assertEquals(2, result.pages.size)
    }
}
