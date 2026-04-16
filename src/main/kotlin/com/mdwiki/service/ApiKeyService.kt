package com.mdwiki.service

import com.mdwiki.dto.ApiKeyCreatedResponse
import com.mdwiki.dto.ApiKeyResponse
import com.mdwiki.dto.CreateApiKeyRequest
import com.mdwiki.model.ApiKey
import com.mdwiki.model.User
import com.mdwiki.repository.ApiKeyRepository
import com.mdwiki.repository.UserRepository
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

@Service
class ApiKeyService(
    private val apiKeyRepository: ApiKeyRepository,
    private val userRepository: UserRepository
) {
    private val secureRandom = SecureRandom()

    fun create(request: CreateApiKeyRequest, username: String): ApiKeyCreatedResponse {
        val user = userRepository.findByUsername(username) ?: throw NoSuchElementException("User not found")
        val rawKey = generateRawKey()
        val hash = hashKey(rawKey)
        val apiKey = apiKeyRepository.save(ApiKey(user = user, name = request.name, keyHash = hash, expiresAt = request.expiresAt))
        return ApiKeyCreatedResponse(id = apiKey.id!!, name = apiKey.name, key = rawKey, createdAt = apiKey.createdAt, expiresAt = apiKey.expiresAt)
    }

    fun validateKey(rawKey: String): User? {
        val hash = hashKey(rawKey)
        val apiKey = apiKeyRepository.findByKeyHash(hash) ?: return null
        if (apiKey.expiresAt != null && apiKey.expiresAt!!.isBefore(Instant.now())) return null
        apiKey.lastUsedAt = Instant.now()
        apiKeyRepository.save(apiKey)
        return apiKey.user
    }

    fun listKeys(username: String): List<ApiKeyResponse> {
        val user = userRepository.findByUsername(username) ?: throw NoSuchElementException("User not found")
        return apiKeyRepository.findByUserId(user.id!!).map { it.toResponse() }
    }

    fun deleteKey(keyId: UUID, username: String) {
        val user = userRepository.findByUsername(username) ?: throw NoSuchElementException("User not found")
        val apiKey = apiKeyRepository.findById(keyId).orElseThrow { NoSuchElementException("API key not found") }
        require(apiKey.user.id == user.id) { "Cannot delete another user's API key" }
        apiKeyRepository.delete(apiKey)
    }

    fun hashKey(rawKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(rawKey.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun generateRawKey(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return "mdw_${bytes.joinToString("") { "%02x".format(it) }}"
    }

    private fun ApiKey.toResponse() = ApiKeyResponse(id = id!!, name = name, lastUsedAt = lastUsedAt, createdAt = createdAt, expiresAt = expiresAt)
}
