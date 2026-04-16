package com.mdwiki.service

import com.mdwiki.dto.AuthResponse
import com.mdwiki.dto.LoginRequest
import com.mdwiki.dto.RegisterRequest
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder
) {

    fun register(request: RegisterRequest): AuthResponse {
        require(!userRepository.existsByUsername(request.username)) { "Username already taken" }
        require(!userRepository.existsByEmail(request.email)) { "Email already registered" }

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

    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByUsername(request.username)
            ?: throw IllegalArgumentException("Invalid credentials")

        require(passwordEncoder.matches(request.password, user.passwordHash)) { "Invalid credentials" }

        val token = jwtService.generateToken(user.username)
        return AuthResponse(token = token, username = user.username, role = user.role.name)
    }
}
