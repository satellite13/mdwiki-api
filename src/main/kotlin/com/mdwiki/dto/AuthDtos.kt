package com.mdwiki.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @param:NotBlank @param:Size(min = 3, max = 100)
    val username: String,
    @param:NotBlank @param:Email
    val email: String,
    @param:NotBlank @param:Size(min = 8)
    val password: String
)

data class LoginRequest(
    @param:NotBlank
    val username: String,
    @param:NotBlank
    val password: String
)

data class AuthResponse(
    val token: String,
    val username: String,
    val role: String
)
