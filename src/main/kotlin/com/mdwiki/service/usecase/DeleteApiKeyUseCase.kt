package com.mdwiki.service.usecase

import com.mdwiki.error.ForbiddenException
import com.mdwiki.error.NotFoundException
import com.mdwiki.repository.ApiKeyRepository
import com.mdwiki.repository.UserRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class DeleteApiKeyUseCase(
    private val apiKeyRepository: ApiKeyRepository,
    private val userRepository: UserRepository
) {
    fun execute(keyId: UUID, username: String) {
        val user = userRepository.findByUsername(username) ?: throw NotFoundException("User not found")
        val apiKey = apiKeyRepository.findById(keyId).orElseThrow { NotFoundException("API key not found") }
        if (apiKey.user.id != user.id) {
            throw ForbiddenException("Cannot delete another user's API key")
        }
        apiKeyRepository.delete(apiKey)
    }
}
