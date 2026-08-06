package com.mdwiki.service.usecase

import com.mdwiki.config.JwtProperties
import com.mdwiki.error.ForbiddenException
import com.mdwiki.error.NotFoundException
import com.mdwiki.model.UserRole
import com.mdwiki.repository.UserRepository
import com.mdwiki.security.JwtScopes
import com.mdwiki.service.JwtService
import org.springframework.stereotype.Component
import java.time.Instant

data class ScopedRestToken(
    val token: String,
    val scope: String,
    val expiresAt: Instant,
    val expiresInSeconds: Long
)

@Component
class MintScopedRestTokenUseCase(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val jwtProperties: JwtProperties
) {
    fun execute(username: String, scope: String = JwtScopes.PAGES_IMPORT): ScopedRestToken {
        val user = userRepository.findByUsername(username)
            ?: throw NotFoundException("User not found: $username")
        if (user.role != UserRole.EDITOR && user.role != UserRole.ADMIN) {
            throw ForbiddenException("EDITOR or ADMIN role required to mint REST tokens")
        }
        if (scope != JwtScopes.PAGES_IMPORT) {
            throw ForbiddenException("Unsupported token scope: $scope")
        }
        val expiresInMs = jwtProperties.scopedExpirationMs
        val token = jwtService.generateScopedToken(username, scope, expiresInMs)
        val parsed = jwtService.parseToken(token)
        return ScopedRestToken(
            token = token,
            scope = scope,
            expiresAt = parsed.expiresAt,
            expiresInSeconds = expiresInMs / 1000
        )
    }
}
