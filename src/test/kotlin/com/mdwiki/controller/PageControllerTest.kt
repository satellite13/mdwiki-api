package com.mdwiki.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.mdwiki.dto.*
import com.mdwiki.error.NotFoundException
import com.mdwiki.error.ConflictException
import com.mdwiki.error.ForbiddenException
import com.mdwiki.service.GraphService
import com.mdwiki.service.PageService
import com.mdwiki.service.usecase.DeletePageUseCase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageImpl
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.*
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class PageControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @MockitoBean private lateinit var pageService: PageService
    @MockitoBean private lateinit var graphService: GraphService

    private val samplePage = PageResponse(
        id = UUID.randomUUID(),
        slug = "test-page",
        title = "Test Page",
        contentMd = "Hello",
        tags = listOf("kotlin"),
        createdBy = "testuser",
        updatedBy = "testuser",
        folderId = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Test
    @WithMockUser(roles = ["READER"])
    fun `GET pages returns list`() {
        val item = PageListItem(id = samplePage.id, slug = "test-page", title = "Test Page", tags = listOf("kotlin"), updatedAt = Instant.now())
        whenever(pageService.findAll(any(), any())).thenReturn(PageImpl(listOf(item)))

        mockMvc.get("/api/pages").andExpect {
            status { isOk() }
            jsonPath("$[0].slug") { value("test-page") }
        }
    }

    @Test
    @WithMockUser(roles = ["READER"])
    fun `GET pages by slug returns page`() {
        whenever(pageService.findBySlug("test-page")).thenReturn(samplePage)

        mockMvc.get("/api/pages/test-page").andExpect {
            status { isOk() }
            jsonPath("$.title") { value("Test Page") }
        }
    }

    @Test
    @WithMockUser(roles = ["READER"])
    fun `GET page sections returns stable section map`() {
        val updatedAt = Instant.parse("2026-09-05T10:00:00Z")
        whenever(pageService.mapSections("test-page")).thenReturn(
            PageSectionMapResponse(
                slug = "test-page",
                updatedAt = updatedAt,
                sections = listOf(
                    PageSectionMapItem(
                        key = "overview-a1b2c3d4",
                        heading = "Overview",
                        headingPath = "Overview",
                        level = 1,
                        length = 42,
                        hash = "abc123",
                        includesChildren = false
                    )
                )
            )
        )

        mockMvc.get("/api/pages/test-page/sections").andExpect {
            status { isOk() }
            jsonPath("$.slug") { value("test-page") }
            jsonPath("$.sections[0].key") { value("overview-a1b2c3d4") }
            jsonPath("$.sections[0].heading") { value("Overview") }
        }
        verify(pageService).mapSections("test-page")
    }

    @Test
    @WithMockUser(roles = ["READER"])
    fun `GET pages by slug returns structured not found error`() {
        whenever(pageService.findBySlug("missing")).thenThrow(NotFoundException("Page not found: missing"))

        mockMvc.get("/api/pages/missing").andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("NOT_FOUND") }
            jsonPath("$.message") { value("Page not found: missing") }
            jsonPath("$.path") { value("/api/pages/missing") }
        }
    }

    @Test
    @WithMockUser(username = "editor", roles = ["EDITOR"])
    fun `POST pages creates page`() {
        whenever(pageService.create(any(), eq("editor"))).thenReturn(samplePage)

        mockMvc.post("/api/pages") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreatePageRequest("test-page", "Test Page", "Hello"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.slug") { value("test-page") }
        }
    }

    @Test
    @WithMockUser(username = "owner", roles = ["EDITOR"])
    fun `DELETE page propagates actor and soft mode`() {
        mockMvc.delete("/api/pages/test-page").andExpect {
            status { isOk() }
        }

        verify(pageService).delete("test-page", DeletePageUseCase.DeleteMode.SOFT, "owner")
    }

    @Test
    @WithMockUser(username = "admin", roles = ["ADMIN"])
    fun `DELETE page propagates actor and hard mode for admin`() {
        mockMvc.delete("/api/pages/test-page") {
            param("mode", "HARD")
        }.andExpect {
            status { isOk() }
        }

        verify(pageService).delete("test-page", DeletePageUseCase.DeleteMode.HARD, "admin")
    }

    @Test
    @WithMockUser(username = "bob", roles = ["EDITOR"])
    fun `DELETE owned page returns forbidden for foreign editor`() {
        whenever(pageService.delete("test-page", DeletePageUseCase.DeleteMode.SOFT, "bob"))
            .thenThrow(ForbiddenException("Folder belongs to another user"))

        mockMvc.delete("/api/pages/test-page").andExpect {
            status { isForbidden() }
            jsonPath("$.error") { value("FORBIDDEN") }
        }
    }

    @Test
    @WithMockUser(roles = ["READER"])
    fun `DELETE page is forbidden for reader`() {
        mockMvc.delete("/api/pages/test-page").andExpect {
            status { isForbidden() }
        }

        verify(pageService, org.mockito.kotlin.never()).delete(any(), any(), any())
    }

    @Test
    @WithMockUser(username = "editor", roles = ["EDITOR"])
    fun `PUT page rejects invalid explicit slug`() {
        mockMvc.put("/api/pages/test-page") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"slug":"Invalid slug"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("VALIDATION_ERROR") }
        }
    }

    @Test
    @WithMockUser(username = "editor", roles = ["EDITOR"])
    fun `PUT page returns conflict for an occupied explicit slug`() {
        whenever(pageService.update(eq("test-page"), any(), eq("editor")))
            .thenThrow(ConflictException("Page slug 'taken' already exists"))

        mockMvc.put("/api/pages/test-page") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"slug":"taken"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("CONFLICT") }
        }
    }

    @Test
    @WithMockUser(username = "editor", roles = ["EDITOR"])
    fun `POST pages import delegates to pageService`() {
        val importResult = ImportMdPagesResponse(
            results = listOf(
                ImportMdItemResult(
                    filename = "note.md",
                    slug = "note",
                    title = "Note",
                    status = ImportMdItemStatus.CREATED
                )
            ),
            created = 1,
            updated = 0,
            skipped = 0,
            errors = 0
        )
        whenever(pageService.importMd(any(), eq(null), eq(false), eq("editor"))).thenReturn(importResult)

        mockMvc.multipart("/api/pages/import") {
            file(org.springframework.mock.web.MockMultipartFile("files", "note.md", "text/markdown", "# Note".toByteArray()))
        }.andExpect {
            status { isOk() }
            jsonPath("$.created") { value(1) }
            jsonPath("$.results[0].status") { value("created") }
            jsonPath("$.results[0].slug") { value("note") }
        }
    }

    @Test
    @WithMockUser(roles = ["READER"])
    fun `POST pages forbidden for READER`() {
        mockMvc.post("/api/pages") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreatePageRequest("test", "Test", ""))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    @WithMockUser(roles = ["READER"])
    fun `GET backlinks returns links`() {
        whenever(pageService.getBacklinks("test-page")).thenReturn(
            listOf(BacklinkResponse("other-page", "Other Page"))
        )

        mockMvc.get("/api/pages/test-page/backlinks").andExpect {
            status { isOk() }
            jsonPath("$[0].slug") { value("other-page") }
        }
    }

    @Test
    @WithMockUser(roles = ["READER"])
    fun `GET page graph delegates to graphService with default depth`() {
        val graph = GraphResponse(
            nodes = listOf(GraphNode("test-page", "Test Page", listOf("kotlin"), true)),
            edges = emptyList()
        )
        whenever(graphService.getGraph("test-page", 1)).thenReturn(graph)

        mockMvc.get("/api/pages/test-page/graph").andExpect {
            status { isOk() }
            jsonPath("$.nodes[0].slug") { value("test-page") }
            // Kotlin boolean `isCurrent` → в JSON ключ `current` (Jackson bean naming)
            jsonPath("$.nodes[0].current") { value(true) }
        }
        verify(graphService).getGraph("test-page", 1)
    }

    @Test
    @WithMockUser(roles = ["READER"])
    fun `GET page graph passes depth query param`() {
        whenever(graphService.getGraph("test-page", 3)).thenReturn(GraphResponse(emptyList(), emptyList()))

        mockMvc.get("/api/pages/test-page/graph") {
            param("depth", "3")
        }.andExpect {
            status { isOk() }
        }
        verify(graphService).getGraph("test-page", 3)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET deleted pages returns deletedAt`() {
        val deletedAt = Instant.parse("2026-07-29T14:22:00Z")
        val item = PageListItem(
            id = samplePage.id,
            slug = "gone",
            title = "Gone",
            tags = emptyList(),
            updatedAt = Instant.now(),
            deletedAt = deletedAt
        )
        whenever(pageService.findDeleted()).thenReturn(listOf(item))

        mockMvc.get("/api/pages/deleted").andExpect {
            status { isOk() }
            jsonPath("$[0].slug") { value("gone") }
            jsonPath("$[0].deletedAt") { value("2026-07-29T14:22:00Z") }
        }
    }

    @Test
    @WithMockUser(roles = ["EDITOR"])
    fun `GET deleted pages forbidden for EDITOR`() {
        mockMvc.get("/api/pages/deleted").andExpect {
            status { isForbidden() }
        }
    }

    @Test
    @WithMockUser(roles = ["EDITOR"])
    fun `POST restore forbidden for EDITOR`() {
        mockMvc.post("/api/pages/gone/restore").andExpect {
            status { isForbidden() }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `POST restore allowed for ADMIN`() {
        whenever(pageService.restore("gone")).thenReturn(samplePage)

        mockMvc.post("/api/pages/gone/restore").andExpect {
            status { isOk() }
            jsonPath("$.slug") { value("test-page") }
        }
        verify(pageService).restore("gone")
    }
}
