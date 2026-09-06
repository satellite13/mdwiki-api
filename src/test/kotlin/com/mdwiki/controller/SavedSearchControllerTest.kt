package com.mdwiki.controller

import com.mdwiki.error.ConflictException
import com.mdwiki.service.SavedSearchService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.put
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class SavedSearchControllerTest {
    @Autowired lateinit var mockMvc: MockMvc
    @MockitoBean lateinit var service: SavedSearchService

    @Test
    @WithMockUser(username = "reader", roles = ["READER"])
    fun `reader update unique constraint conflict is exposed as 409`() {
        val id = UUID.randomUUID()
        whenever(service.update(eq("reader"), eq(id), any()))
            .thenThrow(ConflictException("Saved search name already exists"))

        mockMvc.put("/api/me/saved-searches/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = """
              {"name":"Race","queryText":"q","mode":"HYBRID","tags":["one","two"],
               "minScore":0.75,"sort":"UPDATED","expectedVersion":3}
            """.trimIndent()
        }.andExpect {
            status { isConflict() }
            jsonPath("$.message") { value("Saved search name already exists") }
        }
    }
}
