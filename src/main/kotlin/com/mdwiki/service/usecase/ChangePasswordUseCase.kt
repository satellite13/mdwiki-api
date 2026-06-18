package com.mdwiki.service.usecase

import com.mdwiki.dto.ChangePasswordRequest
import com.mdwiki.error.UnauthorizedException
import com.mdwiki.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class ChangePasswordUseCase(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {
    fun execute(username: String, request: ChangePasswordRequest) {
        val user = userRepository.findByUsername(username)
            ?: throw UnauthorizedException("Invalid credentials")

        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
            throw UnauthorizedException("Current password is incorrect")
        }

        user.passwordHash = passwordEncoder.encode(request.newPassword)
            ?: error("PasswordEncoder returned null")
        userRepository.save(user)
    }
}
