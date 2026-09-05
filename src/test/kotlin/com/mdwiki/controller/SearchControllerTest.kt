package com.mdwiki.controller

import com.mdwiki.dto.RagSearchResult
import com.mdwiki.dto.SearchResult
import com.mdwiki.service.SearchService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
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

    @Test
    @WithMockUser(roles = ["READER"])
    fun `GET search rag returns results`() {
        whenever(searchService.ragSearch("kotlin", 10)).thenReturn(
            listOf(
                RagSearchResult(
                    chunkText = "Kotlin is great",
                    pageSlug = "kotlin-page",
                    pageTitle = "Kotlin Guide",
                    sectionHeading = "Intro",
                    snippet = "Kotlin is great",
                    score = 0.91,
                    tags = listOf("kotlin")
                )
            )
        )

        mockMvc.get("/api/search/rag?q=kotlin").andExpect {
            status { isOk() }
            jsonPath("$[0].pageSlug") { value("kotlin-page") }
            jsonPath("$[0].score") { value(0.91) }
            jsonPath("$[0].tags[0]") { value("kotlin") }
        }
    }

    @Test
    @WithMockUser(roles = ["READER"])
    fun `GET search forwards every requested tag`() {
        whenever(searchService.search("kotlin", tags = listOf("one", "two"))).thenReturn(emptyList())

        mockMvc.get("/api/search?q=kotlin&tags=one,two").andExpect { status { isOk() } }

        verify(searchService).search("kotlin", tags = listOf("one", "two"))
    }
}
