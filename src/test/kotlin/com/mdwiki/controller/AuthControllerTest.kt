package com.mdwiki.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.mdwiki.dto.AuthResponse
import com.mdwiki.dto.LoginRequest
import com.mdwiki.dto.RegisterRequest
import com.mdwiki.error.ConflictException
import com.mdwiki.service.AuthService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var authService: AuthService

    @Test
    fun `POST register returns token`() {
        val response = AuthResponse(token = "jwt123", username = "newuser", role = "ADMIN")
        whenever(authService.register(any())).thenReturn(response)

        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest("newuser", "new@test.com", "password123")
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.token") { value("jwt123") }
            jsonPath("$.username") { value("newuser") }
            jsonPath("$.role") { value("ADMIN") }
        }
    }

    @Test
    fun `POST login returns token`() {
        val response = AuthResponse(token = "jwt456", username = "testuser", role = "EDITOR")
        whenever(authService.login(any())).thenReturn(response)

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                LoginRequest("testuser", "password123")
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.token") { value("jwt456") }
        }
    }

    @Test
    fun `POST register returns structured conflict error`() {
        whenever(authService.register(any())).thenThrow(ConflictException("Username already taken"))

        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest("newuser", "new@test.com", "password123")
            )
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("CONFLICT") }
            jsonPath("$.message") { value("Username already taken") }
            jsonPath("$.path") { value("/api/auth/register") }
        }
    }
}
