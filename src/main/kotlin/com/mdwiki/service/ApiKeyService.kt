package com.mdwiki.service

import com.mdwiki.dto.ApiKeyCreatedResponse
import com.mdwiki.dto.ApiKeyResponse
import com.mdwiki.dto.CreateApiKeyRequest
import com.mdwiki.model.User
import com.mdwiki.repository.ApiKeyRepository
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.usecase.ApiKeyCrypto
import com.mdwiki.service.usecase.CreateApiKeyUseCase
import com.mdwiki.service.usecase.DeleteApiKeyUseCase
import com.mdwiki.service.usecase.ListApiKeysUseCase
import com.mdwiki.service.usecase.ValidateApiKeyUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.UUID

@Service
class ApiKeyService(
    private val apiKeyRepository: ApiKeyRepository,
    private val userRepository: UserRepository
) {
    private val secureRandom = SecureRandom()
    private val createApiKeyUseCase = CreateApiKeyUseCase(
        apiKeyRepository = apiKeyRepository,
        userRepository = userRepository,
        hashKey = ::hashKey,
        generateRawKey = ::generateRawKey
    )
    private val validateApiKeyUseCase = ValidateApiKeyUseCase(
        apiKeyRepository = apiKeyRepository,
        hashKey = ::hashKey
    )
    private val listApiKeysUseCase = ListApiKeysUseCase(
        apiKeyRepository = apiKeyRepository,
        userRepository = userRepository
    )
    private val deleteApiKeyUseCase = DeleteApiKeyUseCase(
        apiKeyRepository = apiKeyRepository,
        userRepository = userRepository
    )

    fun create(request: CreateApiKeyRequest, username: String): ApiKeyCreatedResponse =
        createApiKeyUseCase.execute(request, username)

    @Transactional
    fun validateKey(rawKey: String): User? {
        val user = validateApiKeyUseCase.execute(rawKey) ?: return null
        // ApiKey.user is LAZY; force initialization within active transaction.
        user.role
        return user
    }

    fun listKeys(username: String): List<ApiKeyResponse> = listApiKeysUseCase.execute(username)

    fun deleteKey(keyId: UUID, username: String) = deleteApiKeyUseCase.execute(keyId, username)

    fun hashKey(rawKey: String): String = ApiKeyCrypto.hashKey(rawKey)

    private fun generateRawKey(): String = ApiKeyCrypto.generateRawKey(secureRandom)
}
