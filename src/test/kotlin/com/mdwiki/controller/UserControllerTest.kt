package com.mdwiki.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.mdwiki.dto.UpdateUserRoleRequest
import com.mdwiki.dto.UserResponse
import com.mdwiki.model.UserRole
import com.mdwiki.service.UserService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.put
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @MockitoBean private lateinit var userService: UserService

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET users returns list`() {
        val user = UserResponse(UUID.randomUUID(), "testuser", "test@test.com", UserRole.READER)
        whenever(userService.findAll()).thenReturn(listOf(user))

        mockMvc.get("/api/users").andExpect {
            status { isOk() }
            jsonPath("$[0].username") { value("testuser") }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `PUT user role updates role`() {
        val userId = UUID.randomUUID()
        val updated = UserResponse(userId, "testuser", "test@test.com", UserRole.EDITOR)
        whenever(userService.updateRole(userId, UpdateUserRoleRequest(UserRole.EDITOR))).thenReturn(updated)

        mockMvc.put("/api/users/$userId/role") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(UpdateUserRoleRequest(UserRole.EDITOR))
        }.andExpect {
            status { isOk() }
            jsonPath("$.role") { value("EDITOR") }
        }
    }

    @Test
    @WithMockUser(roles = ["EDITOR"])
    fun `GET users forbidden for non-ADMIN`() {
        mockMvc.get("/api/users").andExpect {
            status { isForbidden() }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"], username = "admin")
    fun `DELETE user returns no content`() {
        val userId = UUID.randomUUID()
        doNothing().whenever(userService).delete(userId, "admin")

        mockMvc.delete("/api/users/$userId").andExpect {
            status { isNoContent() }
        }
    }

    @Test
    @WithMockUser(roles = ["EDITOR"])
    fun `DELETE user forbidden for non-ADMIN`() {
        mockMvc.delete("/api/users/${UUID.randomUUID()}").andExpect {
            status { isForbidden() }
        }
    }
}
