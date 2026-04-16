package com.mdwiki.service

import com.mdwiki.dto.LoginRequest
import com.mdwiki.dto.RegisterRequest
import com.mdwiki.error.ConflictException
import com.mdwiki.error.UnauthorizedException
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.usecase.LoginUserUseCase
import com.mdwiki.service.usecase.RegisterUserUseCase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@ExtendWith(MockitoExtension::class)
class AuthServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var jwtService: JwtService

    private lateinit var authService: AuthService
    private val passwordEncoder = BCryptPasswordEncoder()

    @BeforeEach
    fun setUp() {
        val registerUserUseCase = RegisterUserUseCase(userRepository, jwtService, passwordEncoder)
        val loginUserUseCase = LoginUserUseCase(userRepository, jwtService, passwordEncoder)
        authService = AuthService(registerUserUseCase, loginUserUseCase)
    }

    @Test
    fun `register creates first user as ADMIN`() {
        whenever(userRepository.existsByUsername("admin")).thenReturn(false)
        whenever(userRepository.existsByEmail("admin@test.com")).thenReturn(false)
        whenever(userRepository.count()).thenReturn(0)
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] }
        whenever(jwtService.generateToken("admin")).thenReturn("token123")

        val result = authService.register(RegisterRequest("admin", "admin@test.com", "password123"))

        assertEquals("token123", result.token)
        assertEquals("admin", result.username)
        assertEquals("ADMIN", result.role)

        verify(userRepository).save(argThat<User> { role == UserRole.ADMIN })
    }

    @Test
    fun `register creates subsequent users as READER`() {
        whenever(userRepository.existsByUsername("user2")).thenReturn(false)
        whenever(userRepository.existsByEmail("user2@test.com")).thenReturn(false)
        whenever(userRepository.count()).thenReturn(1)
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] }
        whenever(jwtService.generateToken("user2")).thenReturn("token456")

        val result = authService.register(RegisterRequest("user2", "user2@test.com", "password123"))

        assertEquals("READER", result.role)
        verify(userRepository).save(argThat<User> { role == UserRole.READER })
    }

    @Test
    fun `register throws on duplicate username`() {
        whenever(userRepository.existsByUsername("existing")).thenReturn(true)

        assertThrows<ConflictException> {
            authService.register(RegisterRequest("existing", "new@test.com", "password123"))
        }
    }

    @Test
    fun `login returns token for valid credentials`() {
        val hash = passwordEncoder.encode("password123")!!
        val user = User(username = "testuser", email = "test@test.com", passwordHash = hash, role = UserRole.EDITOR)
        whenever(userRepository.findByUsername("testuser")).thenReturn(user)
        whenever(jwtService.generateToken("testuser")).thenReturn("token789")

        val result = authService.login(LoginRequest("testuser", "password123"))

        assertEquals("token789", result.token)
        assertEquals("EDITOR", result.role)
    }

    @Test
    fun `login throws on invalid password`() {
        val user = User(username = "testuser", email = "test@test.com", passwordHash = "wronghash", role = UserRole.READER)
        whenever(userRepository.findByUsername("testuser")).thenReturn(user)

        assertThrows<UnauthorizedException> {
            authService.login(LoginRequest("testuser", "password123"))
        }
    }
}
