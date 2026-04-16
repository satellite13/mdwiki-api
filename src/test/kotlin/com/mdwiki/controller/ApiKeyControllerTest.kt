package com.mdwiki.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.mdwiki.dto.ApiKeyCreatedResponse
import com.mdwiki.dto.ApiKeyResponse
import com.mdwiki.dto.CreateApiKeyRequest
import com.mdwiki.service.ApiKeyService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ApiKeyControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @MockitoBean private lateinit var apiKeyService: ApiKeyService

    @Test
    @WithMockUser(username = "testuser", roles = ["READER"])
    fun `POST api-keys creates key`() {
        val response = ApiKeyCreatedResponse(UUID.randomUUID(), "My Key", "mdw_abc123", Instant.now(), null)
        whenever(apiKeyService.create(any(), eq("testuser"))).thenReturn(response)
        mockMvc.post("/api/api-keys") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateApiKeyRequest("My Key"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.key") { value("mdw_abc123") }
        }
    }

    @Test
    @WithMockUser(username = "testuser", roles = ["READER"])
    fun `GET api-keys returns list`() {
        whenever(apiKeyService.listKeys("testuser")).thenReturn(
            listOf(ApiKeyResponse(UUID.randomUUID(), "key1", null, Instant.now(), null))
        )
        mockMvc.get("/api/api-keys").andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("key1") }
        }
    }

    @Test
    @WithMockUser(username = "testuser", roles = ["READER"])
    fun `DELETE api-keys deletes key`() {
        mockMvc.delete("/api/api-keys/${UUID.randomUUID()}").andExpect { status { isOk() } }
    }

    @Test
    fun `POST api-keys requires auth`() {
        mockMvc.post("/api/api-keys") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateApiKeyRequest("test"))
        }.andExpect { status { isForbidden() } }
    }
}
