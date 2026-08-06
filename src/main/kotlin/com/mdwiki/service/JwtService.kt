package com.mdwiki.service

import com.mdwiki.config.JwtProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

data class ParsedJwt(
    val username: String,
    val scope: String?,
    val expiresAt: Instant
)

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

    fun generateScopedToken(
        username: String,
        scope: String,
        expirationMs: Long = properties.scopedExpirationMs
    ): String {
        val now = Date()
        val expiry = Date(now.time + expirationMs)
        return Jwts.builder()
            .subject(username)
            .claim("scope", scope)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun parseToken(token: String): ParsedJwt {
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
        val scope = claims["scope"]?.toString()?.takeIf { it.isNotBlank() }
        return ParsedJwt(
            username = claims.subject,
            scope = scope,
            expiresAt = claims.expiration.toInstant()
        )
    }

    fun extractUsername(token: String): String = parseToken(token).username

    fun validateToken(token: String): Boolean {
        return try {
            parseToken(token)
            true
        } catch (_: Exception) {
            false
        }
    }
}
