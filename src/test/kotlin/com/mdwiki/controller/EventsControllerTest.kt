package com.mdwiki.controller

import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.JwtService
import com.mdwiki.service.ParsedJwt
import com.mdwiki.service.TreeEventsService
import java.time.Instant
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@SpringBootTest
@AutoConfigureMockMvc
class EventsControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var jwtService: JwtService

    // Нужен JwtAuthenticationFilter (header-путь); сам контроллер его больше не использует.
    @MockitoBean
    private lateinit var userRepository: UserRepository

    @MockitoBean
    private lateinit var treeEventsService: TreeEventsService

    @Test
    fun `tree events without token returns 401`() {
        mockMvc.get("/api/events/tree").andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `tree events without token and sse Accept stays 401 not 406`() {
        mockMvc.get("/api/events/tree") {
            accept = MediaType.TEXT_EVENT_STREAM
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `tree events with blank token returns 401`() {
        mockMvc.get("/api/events/tree") {
            param("token", "   ")
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `tree events with invalid jwt returns 401`() {
        whenever(jwtService.validateToken("bad")).thenReturn(false)

        mockMvc.get("/api/events/tree") {
            param("token", "bad")
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `tree events with valid bearer but missing user returns 401`() {
        whenever(jwtService.validateToken("t")).thenReturn(true)
        whenever(jwtService.parseToken("t")).thenReturn(
            ParsedJwt(username = "ghost", scope = null, expiresAt = Instant.now().plusSeconds(60))
        )
        whenever(userRepository.findByUsername("ghost")).thenReturn(null)

        mockMvc.get("/api/events/tree") {
            header("Authorization", "Bearer t")
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `tree events with valid query token returns event stream`() {
        whenever(jwtService.validateToken("ok")).thenReturn(true)
        whenever(treeEventsService.subscribe()).thenAnswer { SseEmitter(30_000L) }

        mockMvc.get("/api/events/tree") {
            param("token", "ok")
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM) }
        }
    }

    @Test
    fun `tree events with valid bearer token returns event stream`() {
        whenever(jwtService.validateToken("ok")).thenReturn(true)
        whenever(jwtService.parseToken("ok")).thenReturn(
            ParsedJwt(username = "alice", scope = null, expiresAt = Instant.now().plusSeconds(60))
        )
        whenever(userRepository.findByUsername("alice")).thenReturn(
            User(username = "alice", email = "a@b", passwordHash = "x", role = UserRole.READER)
        )
        whenever(treeEventsService.subscribe()).thenAnswer { SseEmitter(30_000L) }

        mockMvc.get("/api/events/tree") {
            header("Authorization", "Bearer ok")
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM) }
        }
    }
}
