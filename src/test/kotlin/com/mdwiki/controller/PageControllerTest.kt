package com.mdwiki.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.mdwiki.dto.*
import com.mdwiki.error.NotFoundException
import com.mdwiki.service.GraphService
import com.mdwiki.service.PageService
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
        contentHtml = null,
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
}
