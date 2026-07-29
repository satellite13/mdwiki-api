package com.mdwiki.controller

import com.mdwiki.dto.AuthResponse
import com.mdwiki.dto.ChangePasswordRequest
import com.mdwiki.dto.LoginRequest
import com.mdwiki.dto.RegisterRequest
import com.mdwiki.service.AuthService
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): AuthResponse {
        return authService.register(request)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): AuthResponse {
        return authService.login(request)
    }

    @PostMapping("/change-password")
    fun changePassword(
        authentication: Authentication,
        @Valid @RequestBody request: ChangePasswordRequest
    ) {
        authService.changePassword(authentication.name, request)
    }
}
