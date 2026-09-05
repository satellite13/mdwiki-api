package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.dto.CreatePageRequest
import com.mdwiki.dto.UpdatePageRequest
import com.mdwiki.error.ConflictException
import com.mdwiki.error.AppException
import com.mdwiki.error.NotFoundException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.mdwiki.model.Page
import com.mdwiki.model.PageSection
import com.mdwiki.model.User
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.LinkRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.rag.RagService
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.usecase.CreatePageUseCase
import com.mdwiki.service.usecase.DeletePageUseCase
import com.mdwiki.service.usecase.ImportMdPagesUseCase
import com.mdwiki.service.usecase.PatchPageUseCase
import com.mdwiki.service.usecase.PatchSectionUseCase
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
import java.util.Optional
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@ExtendWith(MockitoExtension::class)
class PageServiceTest {
    private val objectMapper = jacksonObjectMapper()

    @Mock private lateinit var pageRepository: PageRepository
    @Mock private lateinit var linkRepository: LinkRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var folderRepository: FolderRepository
    @Mock private lateinit var pageMetadataService: PageMetadataService
    @Mock private lateinit var ragService: RagService
    @Mock private lateinit var pageIndexer: DeferredPageIndexer
    private val frontmatterMetaService = FrontmatterMetaService()
    @Mock private lateinit var fileWatcherService: FileWatcherService
    @Mock private lateinit var treeEventsService: TreeEventsService
    @Mock private lateinit var folderService: FolderService
    @Mock private lateinit var syncService: SyncService
    @Mock private lateinit var sectionIndexService: SectionIndexService

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
            pageMetadataService, pageIndexer, wikiFileService, frontmatterMetaService, wikilinkService,
            sectionIndexService
        )
        val updatePageUseCase = UpdatePageUseCase(
            pageRepository, userRepository, folderRepository,
            pageMetadataService, pageIndexer, wikiFileService, frontmatterMetaService,
            wikilinkService, linkRepository, syncService, sectionIndexService
        )
        val deletePageUseCase = DeletePageUseCase(
            pageRepository, pageMetadataService, ragService, wikiFileService, syncService, frontmatterMetaService
        )
        val importMdPagesUseCase = ImportMdPagesUseCase(
            pageRepository, createPageUseCase, updatePageUseCase, wikilinkService
        )
        val patchPageUseCase = PatchPageUseCase(
            pageRepository, frontmatterMetaService, updatePageUseCase
        )
        val patchSectionUseCase = PatchSectionUseCase(
            pageRepository, frontmatterMetaService, updatePageUseCase
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
            deletePageUseCase,
            importMdPagesUseCase,
            patchPageUseCase,
            patchSectionUseCase,
            sectionIndexService
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
        whenever(pageRepository.findActiveBySlugForUpdate("my-page")).thenReturn(page)
        whenever(userRepository.findByUsername("editor")).thenReturn(user)
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }

        pageService.update("my-page", UpdatePageRequest(title = "New", contentMd = "new content"), "editor")

        // Slug is preserved unless explicitly requested to change
        verify(pageRepository).save(argThat<Page> {
            slug == "my-page" && title == "New" && contentMd == "new content"
        })
        verify(pageRepository).findActiveBySlugForUpdate("my-page")
        assertTrue(tempDir.resolve("my-page.md").toFile().exists())
        assertEquals("new content", tempDir.resolve("my-page.md").toFile().readText())
        verify(sectionIndexService).rebuild(argThat { slug == "my-page" }, eq("new content"))
    }

    @Test
    fun `mapSections rebuilds empty index`() {
        val page = Page(
            id = UUID.randomUUID(),
            slug = "note",
            title = "Note",
            contentMd = "## API\nbody",
            updatedAt = Instant.parse("2026-08-15T10:00:00Z")
        )
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("note")).thenReturn(page)
        whenever(sectionIndexService.listOrRebuild(page)).thenReturn(
            listOf(
                PageSection(
                    id = UUID.randomUUID(),
                    page = page,
                    stableKey = "api",
                    heading = "API",
                    headingLevel = 2,
                    headingPath = "API",
                    sortOrder = 0,
                    startOffset = 0,
                    endOffset = 10,
                    contentHash = "abc"
                )
            )
        )

        val map = pageService.mapSections("note")
        assertEquals("note", map.slug)
        assertEquals(listOf("api"), map.sections.map { it.key })
        assertEquals("API", map.sections.single().headingPath)
        assertEquals(false, map.sections.single().includesChildren)
    }

    @Test
    fun `mapSections marks parent section as including children`() {
        val page = Page(
            id = UUID.randomUUID(),
            slug = "note",
            title = "Note",
            contentMd = "# Intro\nbefore\n\n## API\nbody",
            updatedAt = Instant.parse("2026-08-15T10:00:00Z")
        )
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("note")).thenReturn(page)
        whenever(sectionIndexService.listOrRebuild(page)).thenReturn(
            listOf(
                PageSection(
                    id = UUID.randomUUID(),
                    page = page,
                    stableKey = "intro",
                    heading = "Intro",
                    headingLevel = 1,
                    headingPath = "Intro",
                    sortOrder = 0,
                    startOffset = 0,
                    endOffset = 30,
                    contentHash = "parent"
                ),
                PageSection(
                    id = UUID.randomUUID(),
                    page = page,
                    stableKey = "intro/api",
                    heading = "API",
                    headingLevel = 2,
                    headingPath = "Intro::API",
                    sortOrder = 1,
                    startOffset = 16,
                    endOffset = 30,
                    contentHash = "child"
                )
            )
        )

        val map = pageService.mapSections("note")
        assertEquals(true, map.sections.single { it.key == "intro" }.includesChildren)
        assertEquals(false, map.sections.single { it.key == "intro/api" }.includesChildren)
    }

    @Test
    fun `update rejects stale expectedUpdatedAt`() {
        val updatedAt = Instant.parse("2026-08-15T10:00:00Z")
        val page = Page(
            id = UUID.randomUUID(),
            slug = "my-page",
            title = "Old",
            contentMd = "old content",
            updatedAt = updatedAt
        )
        whenever(pageRepository.findActiveBySlugForUpdate("my-page")).thenReturn(page)
        whenever(userRepository.findByUsername("editor")).thenReturn(
            User(id = UUID.randomUUID(), username = "editor", email = "e@t.com", passwordHash = "h")
        )

        assertThrows<ConflictException> {
            pageService.update(
                "my-page",
                UpdatePageRequest(
                    contentMd = "new content",
                    expectedUpdatedAt = updatedAt.minusSeconds(1)
                ),
                "editor"
            )
        }
        verify(pageRepository, never()).save(any<Page>())
    }

    @Test
    fun `update accepts matching expectedUpdatedAt`() {
        val updatedAt = Instant.parse("2026-08-15T10:00:00Z")
        val page = Page(
            id = UUID.randomUUID(),
            slug = "my-page",
            title = "Old",
            contentMd = "old content",
            updatedAt = updatedAt
        )
        page.filePath = tempDir.resolve("my-page.md").toString()
        tempDir.resolve("my-page.md").toFile().writeText("old content")
        val user = User(id = UUID.randomUUID(), username = "editor", email = "e@t.com", passwordHash = "h")
        whenever(pageRepository.findActiveBySlugForUpdate("my-page")).thenReturn(page)
        whenever(userRepository.findByUsername("editor")).thenReturn(user)
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }

        pageService.update(
            "my-page",
            UpdatePageRequest(contentMd = "new content", expectedUpdatedAt = updatedAt),
            "editor"
        )

        verify(pageRepository).save(argThat<Page> { contentMd == "new content" })
    }

    @Test
    fun `update accepts expectedUpdatedAt that differs only below postgres microsecond precision`() {
        val stored = Instant.parse("2026-08-15T10:00:00.123456Z")
        val fromPreviousResponse = stored.plusNanos(789)
        val page = Page(
            id = UUID.randomUUID(),
            slug = "my-page",
            title = "Old",
            contentMd = "old content",
            updatedAt = stored
        )
        page.filePath = tempDir.resolve("my-page.md").toString()
        tempDir.resolve("my-page.md").toFile().writeText("old content")
        val user = User(id = UUID.randomUUID(), username = "editor", email = "e@t.com", passwordHash = "h")
        whenever(pageRepository.findActiveBySlugForUpdate("my-page")).thenReturn(page)
        whenever(userRepository.findByUsername("editor")).thenReturn(user)
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }

        pageService.update(
            "my-page",
            UpdatePageRequest(contentMd = "new content", expectedUpdatedAt = fromPreviousResponse),
            "editor"
        )

        verify(pageRepository).save(argThat<Page> { contentMd == "new content" })
    }

    @Test
    fun `findBySlug pulls from disk when markdown exists but db has no active row`() {
        val slug = "from-disk"
        tempDir.resolve("$slug.md").toFile().writeText("# T\nbody")
        val saved = Page(id = UUID.randomUUID(), slug = slug, title = "T", contentMd = "body")
        whenever(pageRepository.findBySlugAndDeletedAtIsNull(slug)).thenReturn(null, saved)
        whenever(pageRepository.findFirstByNormalizedTitle(slug)).thenReturn(null)
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
        whenever(pageRepository.findFirstByNormalizedTitle("mcp-протокол")).thenReturn(page)

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
    fun `findBySlug parses title from content frontmatter when frontmatterMeta is null`() {
        val page = Page(
            id = UUID.randomUUID(),
            slug = "agentic-patterns-glava-2-marshrutizaciya",
            title = "agentic-patterns-glava-2-marshrutizaciya",
            contentMd = "---\ntitle: Глава 2. Маршрутизация\n---\n# Body"
        )
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("agentic-patterns-glava-2-marshrutizaciya")).thenReturn(page)

        val result = pageService.findBySlug("agentic-patterns-glava-2-marshrutizaciya")

        assertEquals("Глава 2. Маршрутизация", result.title)
    }

    @Test
    fun `findBySlug extracts raw title line when frontmatter yaml is partially broken`() {
        val page = Page(
            id = UUID.randomUUID(),
            slug = "agentic-patterns-glava-2-marshrutizaciya",
            title = "agentic-patterns-glava-2-marshrutizaciya",
            contentMd = "---\ntitle: \"Глава 2. Маршрутизация\"\nbroken: [\n---\n# Body"
        )
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("agentic-patterns-glava-2-marshrutizaciya")).thenReturn(page)

        val result = pageService.findBySlug("agentic-patterns-glava-2-marshrutizaciya")

        assertEquals("Глава 2. Маршрутизация", result.title)
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
        whenever(pageRepository.findFirstByNormalizedTitle("missing")).thenReturn(null)

        assertThrows<NotFoundException> {
            pageService.findBySlug("missing")
        }
    }

    @Test
    fun `findBySlug throws for nonexistent page`() {
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("nonexistent")).thenReturn(null)
        whenever(pageRepository.findFirstByNormalizedTitle("nonexistent")).thenReturn(null)
        assertThrows<NotFoundException> { pageService.findBySlug("nonexistent") }
    }

    @Test
    fun `update throws for nonexistent page`() {
        whenever(pageRepository.findActiveBySlugForUpdate("nonexistent")).thenReturn(null)
        assertThrows<NotFoundException> {
            pageService.update("nonexistent", UpdatePageRequest(title = "New"), "testuser")
        }
    }

    @Test
    fun `update rejects content change on locked page`() {
        val lockedMd = "---\nlocked: true\n---\nbody"
        val page = Page(id = UUID.randomUUID(), slug = "locked-page", title = "Locked", contentMd = lockedMd)
        page.filePath = tempDir.resolve("locked-page.md").toString()
        tempDir.resolve("locked-page.md").toFile().writeText(lockedMd)
        frontmatterMetaService.refreshFromContent(page, lockedMd)

        val user = User(id = UUID.randomUUID(), username = "editor", email = "e@t.com", passwordHash = "h")
        whenever(pageRepository.findActiveBySlugForUpdate("locked-page")).thenReturn(page)
        whenever(userRepository.findByUsername("editor")).thenReturn(user)

        assertThrows<com.mdwiki.error.ForbiddenException> {
            pageService.update(
                "locked-page",
                UpdatePageRequest(contentMd = "---\nlocked: true\n---\nchanged"),
                "editor"
            )
        }
    }

    @Test
    fun `update allows unlocking locked page`() {
        val lockedMd = "---\nlocked: true\n---\nbody"
        val unlockedMd = "body"
        val page = Page(id = UUID.randomUUID(), slug = "locked-page", title = "Locked", contentMd = lockedMd)
        page.filePath = tempDir.resolve("locked-page.md").toString()
        tempDir.resolve("locked-page.md").toFile().writeText(lockedMd)
        frontmatterMetaService.refreshFromContent(page, lockedMd)

        val user = User(id = UUID.randomUUID(), username = "editor", email = "e@t.com", passwordHash = "h")
        whenever(pageRepository.findActiveBySlugForUpdate("locked-page")).thenReturn(page)
        whenever(userRepository.findByUsername("editor")).thenReturn(user)
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }

        val result = pageService.update(
            "locked-page",
            UpdatePageRequest(contentMd = unlockedMd),
            "editor"
        )

        assertEquals(unlockedMd, result.contentMd)
        assertFalse(result.locked)
        assertEquals(unlockedMd, tempDir.resolve("locked-page.md").toFile().readText())
    }

    @Test
    fun `update preserves slug when title changes`() {
        val pageId = UUID.randomUUID()
        val page = Page(id = pageId, slug = "schema", title = "Schema", contentMd = "content")
        page.filePath = tempDir.resolve("schema.md").toString()
        tempDir.resolve("schema.md").toFile().writeText("content")

        val user = User(id = UUID.randomUUID(), username = "editor", email = "e@t.com", passwordHash = "h")
        whenever(pageRepository.findActiveBySlugForUpdate("schema")).thenReturn(page)
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
        stubRenameLocks(page)
        whenever(userRepository.findByUsername("editor")).thenReturn(user)
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
    fun `slug rename locks incoming candidates in UUID order and rewrites fresh state`() {
        val page = renameablePage()
        val lowId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val highId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")
        val freshLow = Page(id = lowId, slug = "low", title = "Low", contentMd = "fresh low [[old-slug]]")
        val freshHigh = Page(id = highId, slug = "high", title = "High", contentMd = "fresh high [[old-slug]]")
        val lockIds = listOf(page.id!!, lowId, highId).sortedBy(UUID::toString)
        stubRenameLocks(page, listOf(freshHigh, freshLow))
        whenever(userRepository.findByUsername("editor")).thenReturn(
            User(id = UUID.randomUUID(), username = "editor", email = "e@t.com", passwordHash = "h")
        )
        whenever(pageRepository.findBySlug("new-slug")).thenReturn(null)
        whenever(pageRepository.saveAndFlush(any<Page>())).thenAnswer { it.arguments[0] }
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }

        pageService.update("old-slug", UpdatePageRequest(slug = "new-slug"), "editor")

        verify(pageRepository).findAllActiveByIdInForUpdate(lockIds)
        verify(pageRepository).save(argThat<Page> {
            id == lowId && contentMd == "fresh low [[new-slug]]"
        })
        verify(pageRepository).save(argThat<Page> {
            id == highId && contentMd == "fresh high [[new-slug]]"
        })
        verify(pageRepository, never()).save(argThat<Page> {
            contentMd?.startsWith("stale") == true
        })
        inOrder(pageRepository) {
            verify(pageRepository).acquireTransactionAdvisoryLock(MultiPageMutationLock.KEY)
            verify(pageRepository).findActiveIdBySlug("old-slug")
            verify(pageRepository).findAllActiveByIdInForUpdate(lockIds)
        }
    }

    @Test
    fun `combined title and slug rewrite uses the old title identity`() {
        val page = renameablePage().apply { title = "Old Title" }
        val incoming = Page(
            id = UUID.randomUUID(),
            slug = "incoming",
            title = "Incoming",
            contentMd = "[[old-title]] and [[old-slug]]"
        )
        stubRenameLocks(page, listOf(incoming))
        whenever(userRepository.findByUsername("editor")).thenReturn(
            User(id = UUID.randomUUID(), username = "editor", email = "e@t.com", passwordHash = "h")
        )
        whenever(pageRepository.findBySlug("new-slug")).thenReturn(null)
        whenever(pageRepository.saveAndFlush(any<Page>())).thenAnswer { it.arguments[0] }
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }

        pageService.update(
            "old-slug",
            UpdatePageRequest(title = "New Title", slug = "new-slug"),
            "editor"
        )

        verify(pageRepository).save(argThat<Page> {
            id == incoming.id && contentMd == "[[new-slug]] and [[new-slug]]"
        })
        verify(linkRepository, times(1)).updateAllTargetSlugs("old-slug", "new-slug")
    }

    @Test
    fun `combined slug and folder update publishes exactly one final file after commit`() {
        val folderId = UUID.randomUUID()
        val folder = com.mdwiki.model.Folder(id = folderId, name = "Archive")
        val page = renameablePage().apply {
            contentMd = "old"
        }
        val source = tempDir.resolve("old-slug.md").toFile().apply { writeText("old") }
        page.filePath = source.absolutePath
        stubCombinedFileUpdate(page, folder)
        TransactionSynchronizationManager.initSynchronization()
        try {
            pageService.update(
                "old-slug",
                UpdatePageRequest(slug = "new-slug", folderId = folderId, contentMd = "new"),
                "editor"
            )

            val target = tempDir.resolve("Archive/new-slug.md").toFile()
            assertTrue(source.exists())
            assertFalse(target.exists())
            val synchronizations = TransactionSynchronizationManager.getSynchronizations()
            assertEquals(1, synchronizations.size)
            synchronizations.forEach { it.afterCommit() }
            synchronizations.forEach { it.afterCompletion(TransactionSynchronization.STATUS_COMMITTED) }

            assertFalse(source.exists())
            assertTrue(target.exists())
            assertEquals("new", target.readText())
            assertFalse(tempDir.resolve("new-slug.md").toFile().exists())
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `combined slug and folder rollback leaves filesystem untouched`() {
        val folderId = UUID.randomUUID()
        val folder = com.mdwiki.model.Folder(id = folderId, name = "Archive")
        val page = renameablePage()
        val source = tempDir.resolve("old-slug.md").toFile().apply { writeText("old") }
        page.filePath = source.absolutePath
        stubCombinedFileUpdate(page, folder)
        TransactionSynchronizationManager.initSynchronization()
        try {
            pageService.update(
                "old-slug",
                UpdatePageRequest(slug = "new-slug", folderId = folderId, contentMd = "new"),
                "editor"
            )
            TransactionSynchronizationManager.getSynchronizations().forEach {
                it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
            }

            assertTrue(source.exists())
            assertEquals("old", source.readText())
            assertFalse(tempDir.resolve("Archive").toFile().exists())
            assertFalse(tempDir.resolve("new-slug.md").toFile().exists())
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `combined slug and folder update never overwrites final destination`() {
        val folderId = UUID.randomUUID()
        val folder = com.mdwiki.model.Folder(id = folderId, name = "Archive")
        val page = renameablePage()
        val source = tempDir.resolve("old-slug.md").toFile().apply { writeText("old") }
        val target = tempDir.resolve("Archive/new-slug.md").toFile().apply {
            parentFile.mkdirs()
            writeText("occupied")
        }
        page.filePath = source.absolutePath
        stubCombinedFileUpdate(page, folder, persist = false)

        assertThrows<IllegalStateException> {
            pageService.update(
                "old-slug",
                UpdatePageRequest(slug = "new-slug", folderId = folderId, contentMd = "new"),
                "editor"
            )
        }

        assertEquals("old", source.readText())
        assertEquals("occupied", target.readText())
        verify(pageRepository, never()).saveAndFlush(any<Page>())
    }

    private fun stubRenameLocks(page: Page, others: List<Page> = emptyList()) {
        val sourceId = page.id!!
        val allPages = others + page
        val lockIds = allPages.mapNotNull { it.id }.distinct().sortedBy(UUID::toString)
        whenever(pageRepository.findActiveIdBySlug(page.slug)).thenReturn(sourceId)
        whenever(pageRepository.findAllActiveIds()).thenReturn(lockIds)
        whenever(pageRepository.findAllActiveByIdInForUpdate(lockIds))
            .thenReturn(allPages.sortedBy { it.id.toString() })
    }

    private fun stubCombinedFileUpdate(
        page: Page,
        folder: com.mdwiki.model.Folder,
        persist: Boolean = true
    ) {
        stubRenameLocks(page)
        whenever(userRepository.findByUsername("editor")).thenReturn(
            User(id = UUID.randomUUID(), username = "editor", email = "e@t.com", passwordHash = "h")
        )
        whenever(folderRepository.findById(folder.id!!)).thenReturn(Optional.of(folder))
        whenever(pageRepository.findBySlug("new-slug")).thenReturn(null)
        if (persist) {
            whenever(pageRepository.saveAndFlush(any<Page>())).thenAnswer { it.arguments[0] }
            whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }
        }
    }

    @Test
    fun `explicit slug rename rejects blank or invalid slug`() {
        val page = renameablePage()

        for (invalidSlug in listOf("", "   ", "Invalid Slug", "bad_slug", "-bad")) {
            val error = assertThrows<AppException> {
                pageService.update(page.slug, UpdatePageRequest(slug = invalidSlug), "editor")
            }
            assertEquals(HttpStatus.BAD_REQUEST, error.status)
        }
        verify(pageRepository, never()).saveAndFlush(any<Page>())
    }

    @Test
    fun `explicit slug rename conflicts with active or soft-deleted page without suffixing`() {
        whenever(userRepository.findByUsername("editor")).thenReturn(
            User(id = UUID.randomUUID(), username = "editor", email = "e@t.com", passwordHash = "h")
        )
        for (deletedAt in listOf<Instant?>(null, Instant.parse("2026-09-05T10:00:00Z"))) {
            reset(pageRepository)
            val page = renameablePage()
            val collision = Page(
                id = UUID.randomUUID(),
                slug = "taken",
                title = "Taken",
                contentMd = ""
            ).apply { this.deletedAt = deletedAt }
            stubRenameLocks(page)
            whenever(pageRepository.findBySlug("taken")).thenReturn(collision)

            assertThrows<ConflictException> {
                pageService.update("old-slug", UpdatePageRequest(slug = "taken"), "editor")
            }
        }
        verify(pageRepository, never()).findBySlug("taken-2")
    }

    private fun renameablePage(): Page {
        val page = Page(
            id = UUID.randomUUID(),
            slug = "old-slug",
            title = "Old",
            contentMd = "content"
        )
        page.filePath = tempDir.resolve("old-slug.md").toString()
        tempDir.resolve("old-slug.md").toFile().writeText("content")
        return page
    }

    @Test
    fun `delete throws when page and markdown file both missing`() {
        whenever(pageRepository.findBySlugForUpdate("nonexistent")).thenReturn(null)
        assertThrows<NotFoundException> {
            pageService.delete("nonexistent")
        }
    }

    @Test
    fun `delete removes orphan markdown file when no database row`() {
        val slug = "only-on-disk"
        tempDir.resolve("$slug.md").toFile().writeText("# x")
        whenever(pageRepository.findBySlugForUpdate(slug)).thenReturn(null)

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
        whenever(pageRepository.findBySlugForUpdate(slug)).thenReturn(page)
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
    fun `delete soft-deletes page by setting deletedAt and moving file to trash`() {
        val file = tempDir.resolve("doomed.md").toFile().apply { writeText("doomed body") }
        val page = Page(id = UUID.randomUUID(), slug = "doomed", title = "Doomed")
        page.filePath = file.absolutePath

        whenever(pageRepository.findBySlugForUpdate("doomed")).thenReturn(page)
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }

        pageService.delete("doomed")

        assertNotNull(page.deletedAt)
        val trashFile = tempDir.resolve(".trash/doomed.md").toFile()
        assertTrue(trashFile.exists())
        assertFalse(file.exists())
        assertEquals(trashFile.absolutePath, page.filePath)
        verify(pageRepository).save(page)
        verify(pageRepository).findBySlugForUpdate("doomed")
        verify(pageRepository, never()).delete(any<Page>())
    }

    @Test
    fun `restore returns file from trash and clears deletedAt`() {
        val trashFile = tempDir.resolve(".trash/doomed.md").toFile().apply {
            parentFile.mkdirs()
            writeText("doomed body")
        }
        val page = Page(id = UUID.randomUUID(), slug = "doomed", title = "Doomed")
        page.filePath = trashFile.absolutePath
        page.deletedAt = Instant.now()

        whenever(pageRepository.findBySlugForUpdate("doomed")).thenReturn(page)
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }

        pageService.restore("doomed")

        assertNull(page.deletedAt)
        assertFalse(trashFile.exists())
        val restoredFile = tempDir.resolve("doomed.md").toFile()
        assertTrue(restoredFile.exists())
        assertEquals(restoredFile.absolutePath, page.filePath)
        verify(sectionIndexService).rebuild(page)
    }
}
