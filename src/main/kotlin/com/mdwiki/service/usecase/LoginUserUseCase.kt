package com.mdwiki.service.usecase

import com.mdwiki.dto.AuthResponse
import com.mdwiki.dto.LoginRequest
import com.mdwiki.error.UnauthorizedException
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.JwtService
import org.springframework.security.crypto.password.PasswordEncoder

class LoginUserUseCase(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder
) {
    fun execute(request: LoginRequest): AuthResponse {
        val user = userRepository.findByUsername(request.username)
            ?: throw UnauthorizedException("Invalid credentials")

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw UnauthorizedException("Invalid credentials")
        }

        val token = jwtService.generateToken(user.username)
        return AuthResponse(token = token, username = user.username, role = user.role.name)
    }
}
