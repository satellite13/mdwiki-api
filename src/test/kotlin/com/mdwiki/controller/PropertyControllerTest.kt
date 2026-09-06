package com.mdwiki.controller

import com.mdwiki.dto.PageResponse
import com.mdwiki.dto.PropertyDefinitionResponse
import com.mdwiki.dto.PropertyDefinitionWriteRequest
import com.mdwiki.model.PropertyType
import com.mdwiki.service.PropertyService
import com.mdwiki.service.usecase.UpdatePageUseCase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class PropertyControllerTest {
    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var properties: PropertyService
    @MockitoBean lateinit var updatePage: UpdatePageUseCase

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
    @WithMockUser(username = "admin", roles = ["ADMIN"])
    fun `admin can create a property definition with plain config object`() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        whenever(properties.create(any(), eq("admin"))).thenReturn(
            PropertyDefinitionResponse(
                id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                key = "status",
                displayName = "Status",
                type = PropertyType.TEXT,
                config = emptyMap(),
                required = false,
                version = 1,
                createdAt = now,
                updatedAt = now
            )
        )

        mvc.post("/api/property-definitions") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"key":"status","displayName":"Status","type":"TEXT","config":{},"required":false}"""
        }.andExpect { status { isOk() } }

        verify(properties).create(check<PropertyDefinitionWriteRequest> {
            assert(it.key == "status")
            assert(it.config.isNullOrEmpty())
        }, eq("admin"))
    }

    @Test
    @WithMockUser(username = "editor", roles = ["EDITOR"])
    fun `patch page properties loads page inside service by slug`() {
        val updatedAt = Instant.parse("2026-01-01T00:00:00Z")
        whenever(properties.patchPage(eq("daily"), any(), eq("editor"))).thenReturn("---\nstatus: done\n---\n")
        whenever(updatePage.execute(eq("daily"), any(), eq("editor"))).thenReturn(
            PageResponse(
                id = UUID.randomUUID(),
                slug = "daily",
                title = "Daily",
                contentMd = "---\nstatus: done\n---\n",
                tags = emptyList(),
                createdBy = "editor",
                updatedBy = "editor",
                createdAt = updatedAt,
                updatedAt = updatedAt
            )
        )

        mvc.patch("/api/pages/daily/properties") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"expectedUpdatedAt":"$updatedAt","operations":[{"op":"SET","key":"status","value":"done"}]}"""
        }.andExpect { status { isOk() } }

        verify(properties).patchPage(eq("daily"), check {
            assert(it.operations.single().key == "status")
            assert(it.operations.single().value == "done")
        }, eq("editor"))
        verify(updatePage).execute(eq("daily"), any(), eq("editor"))
    }

    @Test
    @WithMockUser(username = "reader", roles = ["READER"])
    fun `page properties use the authenticated actor for folder access`() {
        mvc.get("/api/pages/private/properties").andExpect { status { isOk() } }

        verify(properties).pageProperties("private", "reader")
    }
}
