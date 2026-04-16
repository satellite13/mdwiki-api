package com.mdwiki.service

import com.mdwiki.dto.CreateApiKeyRequest
import com.mdwiki.model.ApiKey
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.ApiKeyRepository
import com.mdwiki.repository.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ApiKeyServiceTest {

    @Mock private lateinit var apiKeyRepository: ApiKeyRepository
    @Mock private lateinit var userRepository: UserRepository
    private lateinit var apiKeyService: ApiKeyService

    @BeforeEach
    fun setUp() {
        apiKeyService = ApiKeyService(apiKeyRepository, userRepository)
    }

    @Test
    fun `create generates key and stores hash`() {
        val user = User(id = UUID.randomUUID(), username = "testuser", email = "t@t.com", passwordHash = "h")
        whenever(userRepository.findByUsername("testuser")).thenReturn(user)
        whenever(apiKeyRepository.save(any<ApiKey>())).thenAnswer {
            val key = it.arguments[0] as ApiKey
            ApiKey(id = UUID.randomUUID(), user = key.user, name = key.name, keyHash = key.keyHash, createdAt = key.createdAt, expiresAt = key.expiresAt)
        }
        val result = apiKeyService.create(CreateApiKeyRequest("My Key"), "testuser")
        assertNotNull(result.key)
        assertTrue(result.key.startsWith("mdw_"))
        assertEquals("My Key", result.name)
        verify(apiKeyRepository).save(argThat<ApiKey> { keyHash.length == 64 })
    }

    @Test
    fun `validateKey returns null for invalid key`() {
        whenever(apiKeyRepository.findByKeyHash(any())).thenReturn(null)
        assertNull(apiKeyService.validateKey("mdw_invalidkey"))
    }

    @Test
    fun `listKeys returns keys for user`() {
        val userId = UUID.randomUUID()
        val user = User(id = userId, username = "testuser", email = "t@t.com", passwordHash = "h")
        val apiKey = ApiKey(id = UUID.randomUUID(), user = user, name = "key1", keyHash = "hash1")
        whenever(userRepository.findByUsername("testuser")).thenReturn(user)
        whenever(apiKeyRepository.findByUserId(userId)).thenReturn(listOf(apiKey))
        val result = apiKeyService.listKeys("testuser")
        assertEquals(1, result.size)
        assertEquals("key1", result[0].name)
    }

    @Test
    fun `deleteKey removes key owned by user`() {
        val userId = UUID.randomUUID()
        val user = User(id = userId, username = "testuser", email = "t@t.com", passwordHash = "h")
        val keyId = UUID.randomUUID()
        val apiKey = ApiKey(id = keyId, user = user, name = "key1", keyHash = "hash1")
        whenever(userRepository.findByUsername("testuser")).thenReturn(user)
        whenever(apiKeyRepository.findById(keyId)).thenReturn(java.util.Optional.of(apiKey))
        apiKeyService.deleteKey(keyId, "testuser")
        verify(apiKeyRepository).delete(apiKey)
    }

    @Test
    fun `deleteKey throws for key not owned by user`() {
        val user = User(id = UUID.randomUUID(), username = "testuser", email = "t@t.com", passwordHash = "h")
        val otherUser = User(id = UUID.randomUUID(), username = "other", email = "o@t.com", passwordHash = "h")
        val keyId = UUID.randomUUID()
        val apiKey = ApiKey(id = keyId, user = otherUser, name = "key1", keyHash = "hash1")
        whenever(userRepository.findByUsername("testuser")).thenReturn(user)
        whenever(apiKeyRepository.findById(keyId)).thenReturn(java.util.Optional.of(apiKey))
        assertThrows<IllegalArgumentException> { apiKeyService.deleteKey(keyId, "testuser") }
    }
}
