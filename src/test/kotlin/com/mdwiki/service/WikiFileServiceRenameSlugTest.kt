package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.model.Folder
import com.mdwiki.model.Page
import com.mdwiki.repository.FolderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
}
