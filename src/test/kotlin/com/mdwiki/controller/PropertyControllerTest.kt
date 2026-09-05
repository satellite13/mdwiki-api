package com.mdwiki.controller

import com.mdwiki.service.PropertyService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.mockito.kotlin.verify

@SpringBootTest
@AutoConfigureMockMvc
class PropertyControllerTest {
    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var properties: PropertyService

    @Test
    @WithMockUser(username = "reader", roles = ["READER"])
    fun `reader can read definitions but cannot mutate them`() {
        mvc.get("/api/property-definitions").andExpect { status { isOk() } }
        mvc.post("/api/property-definitions") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"key":"priority","displayName":"Priority","type":"TEXT","config":{},"required":false}"""
        }.andExpect { status { isForbidden() } }
        mvc.post("/api/admin/properties/reindex").andExpect { status { isForbidden() } }
    }

    @Test
    @WithMockUser(username = "editor", roles = ["EDITOR"])
    fun `editor cannot administer definitions`() {
        mvc.post("/api/property-definitions") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"key":"priority","displayName":"Priority","type":"TEXT","config":{},"required":false}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    @WithMockUser(username = "reader", roles = ["READER"])
    fun `page properties use the authenticated actor for folder access`() {
        mvc.get("/api/pages/private/properties").andExpect { status { isOk() } }

        verify(properties).pageProperties("private", "reader")
    }
}
