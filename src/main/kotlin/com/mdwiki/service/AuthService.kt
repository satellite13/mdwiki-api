package com.mdwiki.service

import com.mdwiki.dto.AuthResponse
import com.mdwiki.dto.LoginRequest
import com.mdwiki.dto.RegisterRequest
import com.mdwiki.service.usecase.LoginUserUseCase
import com.mdwiki.service.usecase.RegisterUserUseCase
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val registerUserUseCase: RegisterUserUseCase,
    private val loginUserUseCase: LoginUserUseCase
) {
    fun register(request: RegisterRequest): AuthResponse = registerUserUseCase.execute(request)

    fun login(request: LoginRequest): AuthResponse = loginUserUseCase.execute(request)
}
