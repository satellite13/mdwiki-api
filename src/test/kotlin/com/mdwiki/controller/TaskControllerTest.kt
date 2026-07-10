package com.mdwiki.controller

import com.mdwiki.dto.CompleteOpenTaskRequest
import com.mdwiki.dto.OpenTaskResponse
import com.mdwiki.service.OpenTaskService
import com.mdwiki.service.usecase.CompleteOpenTaskUseCase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @MockitoBean private lateinit var openTaskService: OpenTaskService
    @MockitoBean private lateinit var completeOpenTaskUseCase: CompleteOpenTaskUseCase

    private val documentId = UUID.randomUUID()
    private val updatedAt = Instant.parse("2026-07-10T09:00:00Z")

    @Test
    @WithMockUser(roles = ["READER"])
    fun `GET open tasks returns tasks for READER`() {
        whenever(openTaskService.listOpenTasks()).thenReturn(
            listOf(
                OpenTaskResponse(
                    documentId = documentId,
                    slug = "roadmap",
                    documentTitle = "Roadmap",
                    text = "Finish controller",
                    sourceOffset = 0,
                    sourceLine = "- [ ] Finish controller",
                    updatedAt = updatedAt,
                    locked = false
                )
            )
        )

        mockMvc.get("/api/tasks/open").andExpect {
            status { isOk() }
            jsonPath("$[0].documentTitle") { value("Roadmap") }
            jsonPath("$[0].text") { value("Finish controller") }
        }
        verify(openTaskService).listOpenTasks()
    }

    @Test
    @WithMockUser(roles = ["EDITOR"])
    fun `GET open tasks returns tasks for EDITOR`() {
        whenever(openTaskService.listOpenTasks()).thenReturn(emptyList())

        mockMvc.get("/api/tasks/open").andExpect {
            status { isOk() }
        }

        verify(openTaskService).listOpenTasks()
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET open tasks returns tasks for ADMIN`() {
        whenever(openTaskService.listOpenTasks()).thenReturn(emptyList())

        mockMvc.get("/api/tasks/open").andExpect {
            status { isOk() }
        }

        verify(openTaskService).listOpenTasks()
    }

    @Test
    @WithMockUser(roles = ["READER"])
    fun `POST complete task is forbidden for READER`() {
        mockMvc.post("/api/tasks/complete") {
            contentType = MediaType.APPLICATION_JSON
            content = completeRequestJson()
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    @WithMockUser(username = "editor", roles = ["EDITOR"])
    fun `POST complete task invokes use case with authenticated username`() {
        val request = completeRequest()

        mockMvc.post("/api/tasks/complete") {
            contentType = MediaType.APPLICATION_JSON
            content = completeRequestJson()
        }.andExpect {
            status { isOk() }
        }

        verify(completeOpenTaskUseCase).execute(eq(request), eq("editor"))
    }

    @Test
    @WithMockUser(username = "admin", roles = ["ADMIN"])
    fun `POST complete task invokes use case for ADMIN`() {
        val request = completeRequest()

        mockMvc.post("/api/tasks/complete") {
            contentType = MediaType.APPLICATION_JSON
            content = completeRequestJson()
        }.andExpect {
            status { isOk() }
        }

        verify(completeOpenTaskUseCase).execute(eq(request), eq("admin"))
    }

    @Test
    @WithMockUser(roles = ["EDITOR"])
    fun `POST complete task rejects negative sourceOffset`() {
        mockMvc.post("/api/tasks/complete") {
            contentType = MediaType.APPLICATION_JSON
            content = completeRequestJson().replace("\"sourceOffset\": 0", "\"sourceOffset\": -1")
        }.andExpect {
            status { isBadRequest() }
        }

        verifyNoInteractions(completeOpenTaskUseCase)
    }

    @Test
    @WithMockUser(roles = ["EDITOR"])
    fun `POST complete task rejects missing sourceLine`() {
        mockMvc.post("/api/tasks/complete") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "documentId": "$documentId",
                  "updatedAt": "$updatedAt",
                  "sourceOffset": 0,
                  "summary": "Done"
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
        }

        verifyNoInteractions(completeOpenTaskUseCase)
    }

    @Test
    @WithMockUser(roles = ["EDITOR"])
    fun `POST complete task rejects blank sourceLine`() {
        mockMvc.post("/api/tasks/complete") {
            contentType = MediaType.APPLICATION_JSON
            content = completeRequestJson().replace("\"sourceLine\": \"- [ ] Finish controller\"", "\"sourceLine\": \"\"")
        }.andExpect {
            status { isBadRequest() }
        }

        verifyNoInteractions(completeOpenTaskUseCase)
    }

    @Test
    @WithMockUser(roles = ["EDITOR"])
    fun `POST complete task rejects summary longer than 255 characters`() {
        mockMvc.post("/api/tasks/complete") {
            contentType = MediaType.APPLICATION_JSON
            content = completeRequestJson().replace("\"summary\": \"Done\"", "\"summary\": \"${"a".repeat(256)}\"")
        }.andExpect {
            status { isBadRequest() }
        }

        verifyNoInteractions(completeOpenTaskUseCase)
    }

    private fun completeRequest() = CompleteOpenTaskRequest(
        documentId = documentId,
        updatedAt = updatedAt,
        sourceOffset = 0,
        sourceLine = "- [ ] Finish controller",
        summary = "Done"
    )

    private fun completeRequestJson() = """
        {
          "documentId": "$documentId",
          "updatedAt": "$updatedAt",
          "sourceOffset": 0,
          "sourceLine": "- [ ] Finish controller",
          "summary": "Done"
        }
    """.trimIndent()
}
