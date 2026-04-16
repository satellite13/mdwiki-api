package com.mdwiki.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.mdwiki.dto.LoginRequest
import com.mdwiki.dto.RegisterRequest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("pgvector/pgvector:pg17").apply {
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

    @Test
    fun `full auth flow - register then login`() {
        // Register first user (should be ADMIN)
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest("admin", "admin@test.com", "password123")
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.role") { value("ADMIN") }
            jsonPath("$.token") { exists() }
        }

        // Login with same credentials
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                LoginRequest("admin", "password123")
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.role") { value("ADMIN") }
            jsonPath("$.token") { exists() }
        }

        // Register second user (should be READER)
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest("user2", "user2@test.com", "password123")
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.role") { value("READER") }
        }
    }
}
