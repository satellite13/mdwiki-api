package com.mdwiki.controller

import com.mdwiki.dto.SearchResult
import com.mdwiki.service.SearchService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @MockitoBean private lateinit var searchService: SearchService

    @Test
    @WithMockUser(roles = ["READER"])
    fun `GET search returns results`() {
        whenever(searchService.search("kotlin")).thenReturn(
            listOf(SearchResult(UUID.randomUUID(), "kotlin-page", "Kotlin Guide", "Kotlin is..."))
        )

        mockMvc.get("/api/search?q=kotlin").andExpect {
            status { isOk() }
            jsonPath("$[0].slug") { value("kotlin-page") }
        }
    }
}
