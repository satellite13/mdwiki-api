package com.mdwiki.controller

import com.mdwiki.model.Tag
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
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class TagControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @MockitoBean private lateinit var tagService: TagService

    @Test
    @WithMockUser(roles = ["READER"])
    fun `GET tags returns all tags`() {
        whenever(tagService.findAll()).thenReturn(
            listOf(Tag(id = UUID.randomUUID(), name = "kotlin"), Tag(id = UUID.randomUUID(), name = "spring"))
        )

        mockMvc.get("/api/tags").andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("kotlin") }
            jsonPath("$[1].name") { value("spring") }
        }
    }
}
