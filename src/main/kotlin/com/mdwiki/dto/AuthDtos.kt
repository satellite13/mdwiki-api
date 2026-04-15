package com.mdwiki.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:NotBlank @field:Size(min = 3, max = 100)
    val username: String,
    @field:NotBlank @field:Email
    val email: String,
    @field:NotBlank @field:Size(min = 8)
    val password: String
)

data class LoginRequest(
    @field:NotBlank
    val username: String,
    @field:NotBlank
    val password: String
)

data class AuthResponse(
    val token: String,
    val username: String,
    val role: String
)
