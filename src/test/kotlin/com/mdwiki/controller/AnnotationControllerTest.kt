package com.mdwiki.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.mdwiki.dto.AnnotationResponse
import com.mdwiki.service.AnnotationService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.get
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class AnnotationControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @MockitoBean private lateinit var annotationService: AnnotationService

    private val annotation = AnnotationResponse(
        id = UUID.randomUUID(),
        pageId = UUID.randomUUID(),
        highlightedText = "selected",
        anchorContext = "selected context",
        comment = "comment",
        rangeStart = null,
        rangeEnd = null,
        color = "#ffeb3b",
        createdBy = "editor",
        createdAt = Instant.parse("2026-09-05T10:00:00Z"),
        updatedAt = Instant.parse("2026-09-05T10:00:00Z")
    )

    @Test
    @WithMockUser(username = "reader", roles = ["READER"])
    fun `annotation list propagates reader actor without private filtering`() {
        whenever(annotationService.listBySlug("note", "reader")).thenReturn(listOf(annotation))

        mockMvc.get("/api/pages/note/annotations").andExpect { status { isOk() } }
    }

    @Test
    @WithMockUser(roles = ["READER"])
    fun `READER cannot create update or delete annotations`() {
        val createBody = mapOf(
            "highlightedText" to "selected",
            "anchorContext" to "selected context"
        )
        mockMvc.post("/api/pages/note/annotations") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(createBody)
        }.andExpect { status { isForbidden() } }

        mockMvc.put("/api/annotations/${annotation.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"comment":"changed"}"""
        }.andExpect { status { isForbidden() } }

        mockMvc.delete("/api/annotations/${annotation.id}")
            .andExpect { status { isForbidden() } }
    }

    @Test
    @WithMockUser(username = "editor", roles = ["EDITOR"])
    fun `EDITOR can update annotations`() {
        whenever(annotationService.update(any(), any(), eq("editor"))).thenReturn(annotation.copy(comment = "changed"))

        mockMvc.put("/api/annotations/${annotation.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"comment":"changed","color":"#90caf9"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.comment") { value("changed") }
        }
    }

    @Test
    @WithMockUser(username = "editor", roles = ["EDITOR"])
    fun `update deserializes explicit annotation clear flags`() {
        whenever(annotationService.update(any(), any(), eq("editor"))).thenReturn(annotation.copy(comment = null, color = null))

        mockMvc.put("/api/annotations/${annotation.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"clearComment":true,"clearColor":true}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.comment") { doesNotExist() }
            jsonPath("$.color") { doesNotExist() }
        }

        verify(annotationService).update(
            eq(annotation.id),
            argThat { clearComment == true && clearColor == true && comment == null && color == null },
            eq("editor")
        )
    }

    @Test
    @WithMockUser(username = "admin", roles = ["ADMIN"])
    fun `admin delete propagates actor`() {
        mockMvc.delete("/api/annotations/${annotation.id}").andExpect { status { isOk() } }
        verify(annotationService).delete(annotation.id, "admin")
    }
}
