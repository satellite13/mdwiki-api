package com.mdwiki.controller

import com.mdwiki.dto.TagResponse
import com.mdwiki.service.TagService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class TagControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @MockitoBean private lateinit var tagService: TagService

    @Test
    @WithMockUser(roles = ["READER"])
    fun `GET tags returns all tags`() {
        whenever(tagService.findAll()).thenReturn(
            listOf(
                TagResponse(id = java.util.UUID.randomUUID(), name = "kotlin", pageCount = 3),
                TagResponse(id = java.util.UUID.randomUUID(), name = "spring", pageCount = 1)
            )
        )

        mockMvc.get("/api/tags").andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("kotlin") }
            jsonPath("$[0].pageCount") { value(3) }
            jsonPath("$[1].name") { value("spring") }
        }
    }
}
