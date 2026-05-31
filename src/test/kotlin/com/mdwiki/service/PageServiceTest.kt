package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.dto.CreatePageRequest
import com.mdwiki.dto.UpdatePageRequest
import com.mdwiki.error.ConflictException
import com.mdwiki.error.NotFoundException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.mdwiki.model.Page
import com.mdwiki.model.User
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.LinkRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.rag.RagService
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.usecase.CreatePageUseCase
import com.mdwiki.service.usecase.DeletePageUseCase
import com.mdwiki.service.usecase.UpdatePageUseCase
import com.mdwiki.service.WikilinkService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PageServiceTest {
    private val objectMapper = jacksonObjectMapper()

    @Mock private lateinit var pageRepository: PageRepository
    @Mock private lateinit var linkRepository: LinkRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var folderRepository: FolderRepository
    @Mock private lateinit var pageMetadataService: PageMetadataService
    @Mock private lateinit var ragService: RagService
    private val frontmatterMetaService = FrontmatterMetaService()
    @Mock private lateinit var fileWatcherService: FileWatcherService
    @Mock private lateinit var treeEventsService: TreeEventsService
    @Mock private lateinit var folderService: FolderService
    @Mock private lateinit var syncService: SyncService

    private lateinit var pageService: PageService
    private lateinit var wikiFileService: WikiFileService

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        val props = WikiProperties(contentDir = tempDir.toString())
        wikiFileService = WikiFileService(props, fileWatcherService, folderRepository)
        val wikilinkService = WikilinkService()
        val createPageUseCase = CreatePageUseCase(
            pageRepository, userRepository, folderRepository,
            pageMetadataService, ragService, wikiFileService, frontmatterMetaService, wikilinkService
        )
        val updatePageUseCase = UpdatePageUseCase(
            pageRepository, userRepository, folderRepository,
            pageMetadataService, ragService, wikiFileService, frontmatterMetaService,
            wikilinkService, linkRepository, syncService
        )
        val deletePageUseCase = DeletePageUseCase(
            pageRepository, pageMetadataService, ragService, wikiFileService, syncService
        )
        pageService = PageService(
            pageRepository,
            pageMetadataService,
            treeEventsService,
            folderService,
            wikiFileService,
            syncService,
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
    fun `create respects explicitly provided slug over title-derived one`() {
        val user = User(id = UUID.randomUUID(), username = "u", email = "u@t.com", passwordHash = "h")
        whenever(userRepository.findByUsername("u")).thenReturn(user)
        whenever(pageRepository.existsBySlug("my-custom-slug")).thenReturn(false)
        whenever(pageRepository.save(any<Page>())).thenAnswer {
            val p = it.arguments[0] as Page
            Page(id = UUID.randomUUID(), slug = p.slug, title = p.title, contentMd = p.contentMd, filePath = p.filePath)
        }

        val request = CreatePageRequest(
            slug = "my-custom-slug",
            title = "Заголовок На Русском",
            contentMd = "x"
        )
        val result = pageService.create(request, "u")

        assertEquals("my-custom-slug", result.slug)
        verify(pageRepository, atLeastOnce()).save(argThat<Page> { slug == "my-custom-slug" })
    }

    @Test
    fun `create falls back to slug from title when request slug is blank`() {
        val user = User(id = UUID.randomUUID(), username = "u", email = "u@t.com", passwordHash = "h")
        whenever(userRepository.findByUsername("u")).thenReturn(user)
        whenever(pageRepository.existsBySlug("\u0437\u0430\u0433\u043e\u043b\u043e\u0432\u043e\u043a")).thenReturn(false)
        whenever(pageRepository.save(any<Page>())).thenAnswer {
            val p = it.arguments[0] as Page
            Page(id = UUID.randomUUID(), slug = p.slug, title = p.title, contentMd = p.contentMd, filePath = p.filePath)
        }

        val request = CreatePageRequest(slug = "   ", title = "Заголовок", contentMd = "x")
        val result = pageService.create(request, "u")

        assertEquals("\u0437\u0430\u0433\u043e\u043b\u043e\u0432\u043e\u043a", result.slug)
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
        val pageId = UUID.randomUUID()
        val page = Page(id = pageId, slug = "my-page", title = "Old", contentMd = "old content")
        page.filePath = tempDir.resolve("my-page.md").toString()
        tempDir.resolve("my-page.md").toFile().writeText("old content")

        val user = User(id = UUID.randomUUID(), username = "editor", email = "e@t.com", passwordHash = "h")
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("my-page")).thenReturn(page)
        whenever(userRepository.findByUsername("editor")).thenReturn(user)
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }

        pageService.update("my-page", UpdatePageRequest(title = "New", contentMd = "new content"), "editor")

        // Slug is preserved unless explicitly requested to change
        verify(pageRepository).save(argThat<Page> {
            slug == "my-page" && title == "New" && contentMd == "new content"
        })
        assertTrue(tempDir.resolve("my-page.md").toFile().exists())
        assertEquals("new content", tempDir.resolve("my-page.md").toFile().readText())
    }

    @Test
    fun `findBySlug pulls from disk when markdown exists but db has no active row`() {
        val slug = "from-disk"
        tempDir.resolve("$slug.md").toFile().writeText("# T\nbody")
        val saved = Page(id = UUID.randomUUID(), slug = slug, title = "T", contentMd = "body")
        whenever(pageRepository.findBySlugAndDeletedAtIsNull(slug)).thenReturn(null, saved)
        whenever(pageRepository.findByNormalizedTitle(slug)).thenReturn(null)
        doNothing().whenever(syncService).syncSingleFile(any())

        val result = pageService.findBySlug(slug)

        assertEquals(slug, result.slug)
        verify(syncService).syncSingleFile(argThat<File> { name == "$slug.md" })
        verify(pageRepository, times(2)).findBySlugAndDeletedAtIsNull(slug)
    }

    @Test
    fun `findBySlug falls back to normalized title`() {
        val page = Page(
            id = UUID.randomUUID(),
            slug = "mcp",
            title = "MCP протокол",
            contentMd = null
        )
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("mcp-протокол")).thenReturn(null)
        whenever(pageRepository.findByNormalizedTitle("mcp-протокол")).thenReturn(page)

        val result = pageService.findBySlug("mcp-протокол")

        assertEquals("mcp", result.slug)
        assertEquals("MCP протокол", result.title)
    }

    @Test
    fun `findBySlug uses frontmatter title over database title`() {
        val page = Page(
            id = UUID.randomUUID(),
            slug = "mcp",
            title = "DB Title",
            contentMd = "---\ntitle: Frontmatter Title\n---\n# Body"
        ).apply {
            frontmatterMeta = objectMapper.readTree("""{"title":"Frontmatter Title"}""")
        }
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("mcp")).thenReturn(page)

        val result = pageService.findBySlug("mcp")

        assertEquals("Frontmatter Title", result.title)
    }

    @Test
    fun `findBySlug falls back to slug when title is blank and no frontmatter title`() {
        val page = Page(
            id = UUID.randomUUID(),
            slug = "fallback-slug",
            title = "   ",
            contentMd = "content"
        ).apply {
            frontmatterMeta = objectMapper.readTree("""{"foo":"bar"}""")
        }
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("fallback-slug")).thenReturn(page)

        val result = pageService.findBySlug("fallback-slug")

        assertEquals("fallback-slug", result.title)
    }

    @Test
    fun `findBySlug throws when neither slug nor normalized title matches`() {
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("missing")).thenReturn(null)
        whenever(pageRepository.findByNormalizedTitle("missing")).thenReturn(null)

        assertThrows<NotFoundException> {
            pageService.findBySlug("missing")
        }
    }

    @Test
    fun `findBySlug throws for nonexistent page`() {
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("nonexistent")).thenReturn(null)
        whenever(pageRepository.findByNormalizedTitle("nonexistent")).thenReturn(null)
        assertThrows<NotFoundException> { pageService.findBySlug("nonexistent") }
    }

    @Test
    fun `update throws for nonexistent page`() {
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("nonexistent")).thenReturn(null)
        assertThrows<NotFoundException> {
            pageService.update("nonexistent", UpdatePageRequest(title = "New"), "testuser")
        }
    }

    @Test
    fun `update preserves slug when title changes`() {
        val pageId = UUID.randomUUID()
        val page = Page(id = pageId, slug = "schema", title = "Schema", contentMd = "content")
        page.filePath = tempDir.resolve("schema.md").toString()
        tempDir.resolve("schema.md").toFile().writeText("content")

        val user = User(id = UUID.randomUUID(), username = "editor", email = "e@t.com", passwordHash = "h")
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("schema")).thenReturn(page)
        whenever(userRepository.findByUsername("editor")).thenReturn(user)
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }

        // Update title to Cyrillic, slug should remain "schema"
        pageService.update("schema", UpdatePageRequest(title = "Схема Вики"), "editor")

        verify(pageRepository).save(argThat<Page> {
            slug == "schema" && title == "Схема Вики"
        })
    }

    @Test
    fun `update changes slug only when explicitly requested`() {
        val pageId = UUID.randomUUID()
        val page = Page(id = pageId, slug = "old-slug", title = "Old", contentMd = "content")
        page.filePath = tempDir.resolve("old-slug.md").toString()
        tempDir.resolve("old-slug.md").toFile().writeText("content")

        val user = User(id = UUID.randomUUID(), username = "editor", email = "e@t.com", passwordHash = "h")
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("old-slug")).thenReturn(page)
        whenever(userRepository.findByUsername("editor")).thenReturn(user)
        whenever(pageRepository.findAllByDeletedAtIsNull()).thenReturn(emptyList())
        whenever(pageRepository.findBySlug("new-slug")).thenReturn(null)
        whenever(linkRepository.updateAllTargetSlugs(any(), any())).thenReturn(0)
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }
        whenever(pageRepository.saveAndFlush(any<Page>())).thenAnswer { it.arguments[0] }

        pageService.update("old-slug", UpdatePageRequest(slug = "new-slug"), "editor")

        verify(pageRepository).save(argThat<Page> {
            slug == "new-slug"
        })
        assertFalse(tempDir.resolve("old-slug.md").toFile().exists())
        assertTrue(tempDir.resolve("new-slug.md").toFile().exists())
    }

    @Test
    fun `delete throws when page and markdown file both missing`() {
        whenever(pageRepository.findBySlug("nonexistent")).thenReturn(null)
        assertThrows<NotFoundException> {
            pageService.delete("nonexistent")
        }
    }

    @Test
    fun `delete removes orphan markdown file when no database row`() {
        val slug = "only-on-disk"
        tempDir.resolve("$slug.md").toFile().writeText("# x")
        whenever(pageRepository.findBySlug(slug)).thenReturn(null)

        pageService.delete(slug, DeletePageUseCase.DeleteMode.HARD)

        assertFalse(tempDir.resolve("$slug.md").toFile().exists())
        verify(pageRepository, never()).delete(any())
    }

    @Test
    fun `delete hard removes soft-deleted row and file from disk`() {
        val slug = "tomb"
        val id = UUID.randomUUID()
        val file = tempDir.resolve("$slug.md").toFile().apply { writeText("a") }
        val page = Page(id = id, slug = slug, title = "T", contentMd = "a").apply {
            filePath = file.absolutePath
            deletedAt = Instant.now()
        }
        whenever(pageRepository.findBySlug(slug)).thenReturn(page)
        doNothing().whenever(pageMetadataService).deleteSourceLinks(any())
        doNothing().whenever(pageMetadataService).detachIncomingLinks(any())
        doNothing().whenever(ragService).deletePageChunks(any())
        doNothing().whenever(pageMetadataService).cleanupOrphanedTags()

        pageService.delete(slug, DeletePageUseCase.DeleteMode.HARD)

        verify(pageMetadataService).deleteSourceLinks(page)
        verify(pageMetadataService).detachIncomingLinks(page)
        verify(pageRepository).delete(page)
        assertFalse(file.exists())
    }

    @Test
    fun `delete soft-deletes page by setting deletedAt`() {
        val page = Page(id = UUID.randomUUID(), slug = "doomed", title = "Doomed")
        page.filePath = tempDir.resolve("doomed.md").toString()

        whenever(pageRepository.findBySlug("doomed")).thenReturn(page)
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }

        pageService.delete("doomed")

        assertNotNull(page.deletedAt)
        verify(pageRepository).save(page)
        verify(pageRepository, never()).delete(any<Page>())
    }
}
