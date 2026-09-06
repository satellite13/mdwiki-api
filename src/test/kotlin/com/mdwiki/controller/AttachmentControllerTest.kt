package com.mdwiki.controller

import com.mdwiki.dto.AttachmentResponse
import com.mdwiki.service.AttachmentService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class AttachmentControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var attachmentService: AttachmentService

    private val sample = AttachmentResponse(
        id = UUID.randomUUID(),
        originalName = "a.png",
        storedName = "uuid.png",
        contentType = "image/png",
        sizeBytes = 4,
        uploadedBy = "ed",
        pageId = null,
        url = "/api/uploads/uuid.png",
        createdAt = Instant.now()
    )

    @Test
    @WithMockUser(roles = ["READER"])
    fun `GET attachments delegates to service`() {
        whenever(attachmentService.list(0, 50, null, null, "user")).thenReturn(
            org.springframework.data.domain.PageImpl(listOf(sample))
        )

        mockMvc.get("/api/attachments").andExpect {
            status { isOk() }
            jsonPath("$[0].storedName") { value("uuid.png") }
        }
    }

    @Test
    @WithMockUser(username = "ed", roles = ["EDITOR"])
    fun `POST attachments accepts multipart file`() {
        whenever(attachmentService.upload(any(), eq("ed"), isNull())).thenReturn(sample)

        mockMvc.multipart("/api/attachments") {
            file(MockMultipartFile("file", "a.png", "image/png", byteArrayOf(1, 2, 3, 4)))
        }.andExpect {
            status { isOk() }
            jsonPath("$.storedName") { value("uuid.png") }
        }
    }

    @Test
    @WithMockUser(username = "ed", roles = ["EDITOR"])
    fun `DELETE attachments calls service`() {
        mockMvc.delete("/api/attachments/${sample.id}").andExpect {
            status { isOk() }
        }
        verify(attachmentService).delete(sample.id, "ed")
    }

    @Test
    @WithMockUser(roles = ["READER"])
    fun `POST attachments forbidden for reader`() {
        mockMvc.multipart("/api/attachments") {
            file(MockMultipartFile("file", "a.png", "image/png", byteArrayOf(1)))
        }.andExpect {
            status { isForbidden() }
        }
    }
}
