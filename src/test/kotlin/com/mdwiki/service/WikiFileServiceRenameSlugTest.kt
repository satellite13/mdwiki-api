package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.model.Folder
import com.mdwiki.model.Page
import com.mdwiki.repository.FolderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import java.io.File
import java.nio.file.Path
import java.util.UUID
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Регрессия: при переименовании по slug нельзя вызывать [resolvePageFile] с ленивым [Page.folder],
 * иначе после flush в транзакции возможен LazyInitializationException.
 */
@ExtendWith(MockitoExtension::class)
class WikiFileServiceRenameSlugTest {

    @Mock
    private lateinit var fileWatcherService: FileWatcherService

    @Mock
    private lateinit var folderRepository: FolderRepository

    @TempDir
    lateinit var tempDir: Path

    private lateinit var wikiFileService: WikiFileService

    @BeforeEach
    fun setUp() {
        wikiFileService = WikiFileService(
            WikiProperties(contentDir = tempDir.toString()),
            fileWatcherService,
            folderRepository
        )
    }

    @Test
    fun `renamePageFileToSlug uses only filePath parent and does not access Folder`() {
        val subDir = tempDir.resolve("Информация").toFile().apply { mkdirs() }
        val oldFile = File(subDir, "mcp.md").apply { writeText("# MCP") }
        val folder: Folder = mock()

        val page = Page(
            id = UUID.randomUUID(),
            slug = "mcp",
            title = "MCP",
            contentMd = "# MCP"
        )
        page.filePath = oldFile.absolutePath
        page.folder = folder

        wikiFileService.renamePageFileToSlug(page, "mcp-протокол")

        assertEquals("mcp-протокол", page.slug)
        assertFalse(oldFile.exists(), "old file should be renamed away")
        assertTrue(File(subDir, "mcp-протокол.md").exists())
        assertTrue(page.filePath!!.endsWith("mcp-протокол.md"))
        verifyNoInteractions(folder)
    }

    @Test
    fun `createOrRewritePageFile uses filePath only and does not touch lazy Folder`() {
        val subDir = tempDir.resolve("Информация").toFile().apply { mkdirs() }
        val file = File(subDir, "note.md").apply { writeText("old") }
        val folder: Folder = mock()

        val page = Page(
            id = UUID.randomUUID(),
            slug = "note",
            title = "Note",
            contentMd = "old"
        )
        page.filePath = file.absolutePath
        page.folder = folder

        wikiFileService.createOrRewritePageFile(page, "new")

        assertEquals("new", file.readText())
        assertEquals(file.absolutePath, page.filePath)
        verifyNoInteractions(folder)
        verifyNoInteractions(folderRepository)
    }

    @Test
    fun `transaction rollback leaves source file unchanged and creates no target`() {
        val source = tempDir.resolve("old.md").toFile().apply { writeText("original") }
        val page = Page(id = UUID.randomUUID(), slug = "old", title = "Old", contentMd = "original")
            .apply { filePath = source.absolutePath }
        TransactionSynchronizationManager.initSynchronization()
        try {
            wikiFileService.renamePageFileToSlug(page, "new")

            assertTrue(source.exists())
            assertFalse(tempDir.resolve("new.md").toFile().exists())
            TransactionSynchronizationManager.getSynchronizations().forEach {
                it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
            }
            assertTrue(source.exists())
            assertEquals("original", source.readText())
            assertFalse(tempDir.resolve("new.md").toFile().exists())
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `rename never overwrites an existing target file`() {
        val source = tempDir.resolve("old.md").toFile().apply { writeText("source") }
        val target = tempDir.resolve("new.md").toFile().apply { writeText("target") }
        val page = Page(id = UUID.randomUUID(), slug = "old", title = "Old", contentMd = "source")
            .apply { filePath = source.absolutePath }

        assertThrows<IllegalStateException> {
            wikiFileService.renamePageFileToSlug(page, "new")
        }

        assertEquals("old", page.slug)
        assertEquals(source.absolutePath, page.filePath)
        assertEquals("source", source.readText())
        assertEquals("target", target.readText())
    }
}
