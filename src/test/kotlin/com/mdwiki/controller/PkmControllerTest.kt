package com.mdwiki.controller

import com.mdwiki.service.PkmService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType

@SpringBootTest
@AutoConfigureMockMvc
class PkmControllerTest {
    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: PkmService

    @Test
    @WithMockUser(username = "reader", roles = ["READER"])
    fun `reader cannot capture or create daily note`() {
        mvc.post("/api/captures/text") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"text":"x"}"""
        }.andExpect { status { isForbidden() } }
        mvc.put("/api/me/daily-notes/2026-09-05")
            .andExpect { status { isForbidden() } }
        mvc.post("/api/pages/target/unlinked-mentions/link") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"sourceSlug":"source","startOffset":0,"endOffset":1,"expectedUpdatedAt":"2026-09-05T10:00:00Z"}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    @WithMockUser(username = "reader", roles = ["READER"])
    fun `reader can list own recent pages`() {
        whenever(service.listRecent(any(), any())).thenReturn(emptyList())
        mvc.get("/api/me/recent-pages").andExpect { status { isOk() } }
    }

    @Test
    @WithMockUser(username = "editor", roles = ["EDITOR"])
    fun `editor can access capture daily and mention mutations`() {
        mvc.post("/api/captures/text") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"text":"capture"}"""
        }.andExpect { status { isCreated() } }
        mvc.put("/api/me/daily-notes/2026-09-05").andExpect { status { isOk() } }
        mvc.post("/api/pages/target/unlinked-mentions/link") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"sourceSlug":"source","startOffset":0,"endOffset":1,"expectedUpdatedAt":"2026-09-05T10:00:00Z"}"""
        }.andExpect { status { isOk() } }
    }

    @Test
    @WithMockUser(username = "admin", roles = ["ADMIN"])
    fun `admin can capture`() {
        mvc.post("/api/captures/text") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"text":"capture"}"""
        }.andExpect { status { isCreated() } }
    }

    @Test
    @WithMockUser(username = "editor", roles = ["EDITOR"])
    fun `mention link rejects invalid offsets before service`() {
        mvc.post("/api/pages/target/unlinked-mentions/link") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"sourceSlug":"source","startOffset":-1,"endOffset":0,"expectedUpdatedAt":"2026-09-05T10:00:00Z"}"""
        }.andExpect { status { isBadRequest() } }
    }
}
