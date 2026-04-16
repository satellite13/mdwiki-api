package com.mdwiki.service

import com.mdwiki.dto.AuthResponse
import com.mdwiki.dto.LoginRequest
import com.mdwiki.dto.RegisterRequest
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.usecase.LoginUserUseCase
import com.mdwiki.service.usecase.RegisterUserUseCase
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder
) {
    private val registerUserUseCase = RegisterUserUseCase(
        userRepository = userRepository,
        jwtService = jwtService,
        passwordEncoder = passwordEncoder
    )
    private val loginUserUseCase = LoginUserUseCase(
        userRepository = userRepository,
        jwtService = jwtService,
        passwordEncoder = passwordEncoder
    )

    fun register(request: RegisterRequest): AuthResponse = registerUserUseCase.execute(request)

    fun login(request: LoginRequest): AuthResponse = loginUserUseCase.execute(request)
}
