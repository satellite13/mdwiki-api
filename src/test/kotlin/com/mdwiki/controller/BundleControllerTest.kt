package com.mdwiki.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.mdwiki.dto.BundleExportRequest
import com.mdwiki.dto.BundleImportResponse
import com.mdwiki.dto.BundlePreviewResponse
import com.mdwiki.service.BundleService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class BundleControllerTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17")).apply {
            withDatabaseName("mdwiki_test")
            withUsername("test")
            withPassword("test")
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }


    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    @MockitoBean private lateinit var bundleService: BundleService

    @Test
    @WithMockUser(roles = ["READER"])
    fun `POST bundles preview forbidden for READER`() {
        mockMvc.post("/api/bundles/preview") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(BundleExportRequest(pageSlugs = listOf("intro")))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    @WithMockUser(username = "editor", roles = ["EDITOR"])
    fun `POST bundles preview delegates to service`() {
        whenever(bundleService.preview(any())).thenReturn(
            BundlePreviewResponse(
                folders = emptyList(),
                pages = emptyList(),
                attachments = emptyList(),
                attachmentCount = 0,
                attachmentBytes = 0,
                warnings = emptyList()
            )
        )

        mockMvc.post("/api/bundles/preview") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(BundleExportRequest(pageSlugs = listOf("intro")))
        }.andExpect {
            status { isOk() }
            jsonPath("$.attachmentCount") { value(0) }
        }
    }

    @Test
    @WithMockUser(username = "editor", roles = ["EDITOR"])
    fun `POST bundles import delegates to service`() {
        whenever(bundleService.importBundle(any(), eq(null), eq("editor"))).thenReturn(
            BundleImportResponse(1, 0, emptyList(), 0, emptyList())
        )

        mockMvc.multipart("/api/bundles/import") {
            file(MockMultipartFile("file", "book.zip", "application/zip", byteArrayOf(1, 2)))
        }.andExpect {
            status { isOk() }
            jsonPath("$.createdPages") { value(1) }
        }
        verify(bundleService).importBundle(any(), eq(null), eq("editor"))
    }

    @Test
    @WithMockUser(roles = ["READER"])
    fun `POST bundles import forbidden for READER`() {
        mockMvc.multipart("/api/bundles/import") {
            file(MockMultipartFile("file", "book.zip", "application/zip", byteArrayOf(1)))
        }.andExpect {
            status { isForbidden() }
        }
    }
}
