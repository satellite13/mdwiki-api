package com.mdwiki.service

import com.mdwiki.config.JwtProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(private val properties: JwtProperties) {

    /** SHA-256(secret) yields 32 bytes — satisfies JJWT minimum key length for HS256. */
    private val key: SecretKey = Keys.hmacShaKeyFor(
        MessageDigest.getInstance("SHA-256").digest(properties.secret.toByteArray(StandardCharsets.UTF_8))
    )

    fun generateToken(username: String): String {
        val now = Date()
        val expiry = Date(now.time + properties.expirationMs)
        return Jwts.builder()
            .subject(username)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun extractUsername(token: String): String {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
            .subject
    }

    fun validateToken(token: String): Boolean {
        return try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
            true
        } catch (e: Exception) {
            false
        }
    }
}
