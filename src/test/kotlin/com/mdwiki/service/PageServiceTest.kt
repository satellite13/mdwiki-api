package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.dto.CreatePageRequest
import com.mdwiki.dto.UpdatePageRequest
import com.mdwiki.error.ConflictException
import com.mdwiki.error.NotFoundException
import com.mdwiki.model.Page
import com.mdwiki.model.User
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.rag.RagService
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.usecase.CreatePageUseCase
import com.mdwiki.service.usecase.DeletePageUseCase
import com.mdwiki.service.usecase.UpdatePageUseCase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.nio.file.Path
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PageServiceTest {

    @Mock private lateinit var pageRepository: PageRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var folderRepository: FolderRepository
    @Mock private lateinit var pageMetadataService: PageMetadataService
    @Mock private lateinit var ragService: RagService
    private val frontmatterMetaService = FrontmatterMetaService()
    @Mock private lateinit var fileWatcherService: FileWatcherService
    @Mock private lateinit var treeEventsService: TreeEventsService

    private lateinit var pageService: PageService
    private lateinit var wikiFileService: WikiFileService

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        val props = WikiProperties(contentDir = tempDir.toString())
        wikiFileService = WikiFileService(props, fileWatcherService)
        val createPageUseCase = CreatePageUseCase(
            pageRepository, userRepository, folderRepository,
            pageMetadataService, ragService, wikiFileService, frontmatterMetaService
        )
        val updatePageUseCase = UpdatePageUseCase(
            pageRepository, userRepository, folderRepository,
            pageMetadataService, ragService, wikiFileService, frontmatterMetaService
        )
        val deletePageUseCase = DeletePageUseCase(
            pageRepository, pageMetadataService, ragService, wikiFileService
        )
        pageService = PageService(
            pageRepository,
            pageMetadataService,
            treeEventsService,
            createPageUseCase,
            updatePageUseCase,
            deletePageUseCase
        )
    }

    @Test
    fun `create saves page and writes file`() {
        val user = User(id = UUID.randomUUID(), username = "testuser", email = "t@t.com", passwordHash = "h")
        whenever(userRepository.findByUsername("testuser")).thenReturn(user)
        whenever(pageRepository.existsBySlug("test-page")).thenReturn(false)
        whenever(pageRepository.save(any<Page>())).thenAnswer {
            val p = it.arguments[0] as Page
            Page(id = UUID.randomUUID(), slug = p.slug, title = p.title, contentMd = p.contentMd, filePath = p.filePath, createdBy = p.createdBy, updatedBy = p.updatedBy)
        }
        val request = CreatePageRequest(slug = "test-page", title = "Test Page", contentMd = "Hello")
        pageService.create(request, "testuser")

        verify(pageRepository, atLeastOnce()).save(argThat<Page> {
            slug == "test-page" && title == "Test Page" && contentMd == "Hello"
        })

        val file = tempDir.resolve("test-page.md")
        assertTrue(file.toFile().exists())
        assertEquals("Hello", file.toFile().readText())
    }

    @Test
    fun `create throws on duplicate slug`() {
        whenever(pageRepository.existsBySlug("existing")).thenReturn(true)

        assertThrows<ConflictException> {
            pageService.create(CreatePageRequest("existing", "Existing"), "testuser")
        }
    }

    @Test
    fun `update modifies page and rewrites file`() {
        val page = Page(id = UUID.randomUUID(), slug = "my-page", title = "Old", contentMd = "old content")
        page.filePath = tempDir.resolve("my-page.md").toString()
        tempDir.resolve("my-page.md").toFile().writeText("old content")

        val user = User(id = UUID.randomUUID(), username = "editor", email = "e@t.com", passwordHash = "h")
        whenever(pageRepository.findBySlug("my-page")).thenReturn(page)
        whenever(userRepository.findByUsername("editor")).thenReturn(user)
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }
        pageService.update("my-page", UpdatePageRequest(title = "New", contentMd = "new content"), "editor")

        verify(pageRepository, atLeastOnce()).save(argThat<Page> {
            title == "New" && contentMd == "new content"
        })
        assertEquals("new content", tempDir.resolve("my-page.md").toFile().readText())
    }

    @Test
    fun `findBySlug falls back to normalized title`() {
        val page = Page(
            id = UUID.randomUUID(),
            slug = "mcp",
            title = "MCP протокол",
            contentMd = null
        )
        whenever(pageRepository.findBySlug("mcp-протокол")).thenReturn(null)
        whenever(pageRepository.findByNormalizedTitle("mcp-протокол")).thenReturn(page)

        val result = pageService.findBySlug("mcp-протокол")

        assertEquals("mcp", result.slug)
        assertEquals("MCP протокол", result.title)
    }

    @Test
    fun `findBySlug throws when neither slug nor normalized title matches`() {
        whenever(pageRepository.findBySlug("missing")).thenReturn(null)
        whenever(pageRepository.findByNormalizedTitle("missing")).thenReturn(null)

        assertThrows<NotFoundException> {
            pageService.findBySlug("missing")
        }
    }

    @Test
    fun `findBySlug throws for nonexistent page`() {
        whenever(pageRepository.findBySlug("nonexistent")).thenReturn(null)
        whenever(pageRepository.findByNormalizedTitle("nonexistent")).thenReturn(null)
        assertThrows<NotFoundException> { pageService.findBySlug("nonexistent") }
    }

    @Test
    fun `update throws for nonexistent page`() {
        whenever(pageRepository.findBySlug("nonexistent")).thenReturn(null)
        assertThrows<NotFoundException> {
            pageService.update("nonexistent", UpdatePageRequest(title = "New"), "testuser")
        }
    }

    @Test
    fun `delete throws for nonexistent page`() {
        whenever(pageRepository.findBySlug("nonexistent")).thenReturn(null)
        assertThrows<NotFoundException> {
            pageService.delete("nonexistent")
        }
    }

    @Test
    fun `delete removes page and file`() {
        val page = Page(id = UUID.randomUUID(), slug = "doomed", title = "Doomed")
        page.filePath = tempDir.resolve("doomed.md").toString()
        tempDir.resolve("doomed.md").toFile().writeText("bye")

        whenever(pageRepository.findBySlug("doomed")).thenReturn(page)

        pageService.delete("doomed")

        verify(pageRepository).delete(page)
        assertFalse(tempDir.resolve("doomed.md").toFile().exists())
    }
}
