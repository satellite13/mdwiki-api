package com.mdwiki.service

import com.mdwiki.config.JwtProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JwtServiceTest {

    private lateinit var jwtService: JwtService
    private val properties = JwtProperties(
        secret = "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha",
        expirationMs = 3600000
    )

    @BeforeEach
    fun setUp() {
        jwtService = JwtService(properties)
    }

    @Test
    fun `generateToken creates valid token`() {
        val token = jwtService.generateToken("testuser")
        assertNotNull(token)
        assertTrue(token.isNotBlank())
    }

    @Test
    fun `extractUsername returns correct username`() {
        val token = jwtService.generateToken("testuser")
        val username = jwtService.extractUsername(token)
        assertEquals("testuser", username)
    }

    @Test
    fun `validateToken returns true for valid token`() {
        val token = jwtService.generateToken("testuser")
        assertTrue(jwtService.validateToken(token))
    }

    @Test
    fun `validateToken returns false for tampered token`() {
        val token = jwtService.generateToken("testuser")
        assertFalse(jwtService.validateToken(token + "tampered"))
    }
}
