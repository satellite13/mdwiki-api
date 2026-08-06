package com.mdwiki.service.usecase

import com.mdwiki.config.JwtProperties
import com.mdwiki.error.ForbiddenException
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.UserRepository
import com.mdwiki.security.JwtScopes
import com.mdwiki.service.JwtService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MintScopedRestTokenUseCaseTest {

    @Mock private lateinit var userRepository: UserRepository

    private val jwtProperties = JwtProperties(
        secret = "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha",
        expirationMs = 86_400_000,
        scopedExpirationMs = 600_000
    )
    private val jwtService = JwtService(jwtProperties)

    private fun useCase() = MintScopedRestTokenUseCase(userRepository, jwtService, jwtProperties)

    @Test
    fun `editor gets scoped pages import token`() {
        whenever(userRepository.findByUsername("editor")).thenReturn(
            User(id = UUID.randomUUID(), username = "editor", email = "e@e.com", passwordHash = "h", role = UserRole.EDITOR)
        )

        val minted = useCase().execute("editor")

        assertEquals(JwtScopes.PAGES_IMPORT, minted.scope)
        assertEquals(600, minted.expiresInSeconds)
        assertTrue(jwtService.validateToken(minted.token))
        val parsed = jwtService.parseToken(minted.token)
        assertEquals("editor", parsed.username)
        assertEquals(JwtScopes.PAGES_IMPORT, parsed.scope)
    }

    @Test
    fun `reader cannot mint token`() {
        whenever(userRepository.findByUsername("reader")).thenReturn(
            User(id = UUID.randomUUID(), username = "reader", email = "r@r.com", passwordHash = "h", role = UserRole.READER)
        )

        assertThrows<ForbiddenException> {
            useCase().execute("reader")
        }
    }
}
