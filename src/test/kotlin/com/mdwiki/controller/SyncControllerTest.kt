package com.mdwiki.controller

import com.mdwiki.service.SyncService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class SyncControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @MockitoBean private lateinit var syncService: SyncService

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `POST sync triggers full sync`() {
        whenever(syncService.fullSync()).thenReturn(SyncService.SyncResult(2, 1, 0))

        mockMvc.post("/api/sync").andExpect {
            status { isOk() }
            jsonPath("$.added") { value(2) }
            jsonPath("$.updated") { value(1) }
            jsonPath("$.removed") { value(0) }
        }
    }

    @Test
    @WithMockUser(roles = ["EDITOR"])
    fun `POST sync forbidden for non-ADMIN`() {
        mockMvc.post("/api/sync").andExpect {
            status { isForbidden() }
        }
    }
}
