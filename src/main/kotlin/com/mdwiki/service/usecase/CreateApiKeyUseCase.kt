package com.mdwiki.service.usecase

import com.mdwiki.dto.ApiKeyCreatedResponse
import com.mdwiki.dto.CreateApiKeyRequest
import com.mdwiki.error.NotFoundException
import com.mdwiki.model.ApiKey
import com.mdwiki.repository.ApiKeyRepository
import com.mdwiki.repository.UserRepository
import org.springframework.stereotype.Component
import java.security.SecureRandom

@Component
class CreateApiKeyUseCase(
    private val apiKeyRepository: ApiKeyRepository,
    private val userRepository: UserRepository
) {
    private val secureRandom = SecureRandom()

    fun execute(request: CreateApiKeyRequest, username: String): ApiKeyCreatedResponse {
        val user = userRepository.findByUsername(username) ?: throw NotFoundException("User not found")
        val rawKey = ApiKeyCrypto.generateRawKey(secureRandom)
        val hash = ApiKeyCrypto.hashKey(rawKey)
        val apiKey = apiKeyRepository.save(
            ApiKey(user = user, name = request.name, keyHash = hash, expiresAt = request.expiresAt)
        )
        return ApiKeyCreatedResponse(
            id = apiKey.id!!,
            name = apiKey.name,
            key = rawKey,
            createdAt = apiKey.createdAt,
            expiresAt = apiKey.expiresAt
        )
    }
}
