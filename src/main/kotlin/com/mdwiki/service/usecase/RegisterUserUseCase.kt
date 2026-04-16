package com.mdwiki.service.usecase

import com.mdwiki.dto.AuthResponse
import com.mdwiki.dto.RegisterRequest
import com.mdwiki.error.ConflictException
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.JwtService
import org.springframework.security.crypto.password.PasswordEncoder

class RegisterUserUseCase(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder
) {
    fun execute(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByUsername(request.username)) {
            throw ConflictException("Username already taken")
        }
        if (userRepository.existsByEmail(request.email)) {
            throw ConflictException("Email already registered")
        }

        val isFirstUser = userRepository.count() == 0L
        val role = if (isFirstUser) UserRole.ADMIN else UserRole.READER

        val passwordHash = passwordEncoder.encode(request.password)
            ?: error("PasswordEncoder returned null")
        val user = User(
            username = request.username,
            email = request.email,
            passwordHash = passwordHash,
            role = role
        )
        userRepository.save(user)

        val token = jwtService.generateToken(user.username)
        return AuthResponse(token = token, username = user.username, role = user.role.name)
    }
}
