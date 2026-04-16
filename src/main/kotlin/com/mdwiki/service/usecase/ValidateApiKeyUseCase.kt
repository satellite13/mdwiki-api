package com.mdwiki.service.usecase

import com.mdwiki.model.User
import com.mdwiki.repository.ApiKeyRepository
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ValidateApiKeyUseCase(
    private val apiKeyRepository: ApiKeyRepository
) {
    fun execute(rawKey: String): User? {
        val hash = ApiKeyCrypto.hashKey(rawKey)
        val apiKey = apiKeyRepository.findByKeyHash(hash) ?: return null
        if (apiKey.expiresAt != null && apiKey.expiresAt!!.isBefore(Instant.now())) return null
        apiKey.lastUsedAt = Instant.now()
        apiKeyRepository.save(apiKey)
        return apiKey.user
    }
}
