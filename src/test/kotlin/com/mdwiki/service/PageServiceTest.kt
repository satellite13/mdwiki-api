package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.dto.CreatePageRequest
import com.mdwiki.dto.UpdatePageRequest
import com.mdwiki.model.Page
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.LinkRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.UserRepository
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
    @Mock private lateinit var linkRepository: LinkRepository
    @Mock private lateinit var wikilinkService: WikilinkService
    @Mock private lateinit var tagService: TagService

    private lateinit var pageService: PageService

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        val props = WikiProperties(contentDir = tempDir.toString())
        pageService = PageService(pageRepository, userRepository, linkRepository, wikilinkService, tagService, props)
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
        whenever(wikilinkService.extractWikilinks(any())).thenReturn(emptyList())
        whenever(wikilinkService.extractTags(any())).thenReturn(emptySet())

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

        assertThrows<IllegalArgumentException> {
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
        whenever(wikilinkService.extractWikilinks(any())).thenReturn(emptyList())
        whenever(wikilinkService.extractTags(any())).thenReturn(emptySet())

        pageService.update("my-page", UpdatePageRequest(title = "New", contentMd = "new content"), "editor")

        verify(pageRepository, atLeastOnce()).save(argThat<Page> {
            title == "New" && contentMd == "new content"
        })
        assertEquals("new content", tempDir.resolve("my-page.md").toFile().readText())
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
