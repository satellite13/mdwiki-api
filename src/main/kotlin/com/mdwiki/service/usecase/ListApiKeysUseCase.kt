package com.mdwiki.service.usecase

import com.mdwiki.dto.ApiKeyResponse
import com.mdwiki.error.NotFoundException
import com.mdwiki.model.ApiKey
import com.mdwiki.repository.ApiKeyRepository
import com.mdwiki.repository.UserRepository
import org.springframework.stereotype.Component

@Component
class ListApiKeysUseCase(
    private val apiKeyRepository: ApiKeyRepository,
    private val userRepository: UserRepository
) {
    fun execute(username: String): List<ApiKeyResponse> {
        val user = userRepository.findByUsername(username) ?: throw NotFoundException("User not found")
        return apiKeyRepository.findByUserId(user.id!!).map { it.toResponse() }
    }

    private fun ApiKey.toResponse() = ApiKeyResponse(
        id = id!!,
        name = name,
        lastUsedAt = lastUsedAt,
        createdAt = createdAt,
        expiresAt = expiresAt
    )
}
