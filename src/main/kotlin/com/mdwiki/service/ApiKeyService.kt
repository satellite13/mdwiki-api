package com.mdwiki.service

import com.mdwiki.dto.ApiKeyCreatedResponse
import com.mdwiki.dto.ApiKeyResponse
import com.mdwiki.dto.CreateApiKeyRequest
import com.mdwiki.model.User
import com.mdwiki.service.usecase.ApiKeyCrypto
import com.mdwiki.service.usecase.CreateApiKeyUseCase
import com.mdwiki.service.usecase.DeleteApiKeyUseCase
import com.mdwiki.service.usecase.ListApiKeysUseCase
import com.mdwiki.service.usecase.ValidateApiKeyUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ApiKeyService(
    private val createApiKeyUseCase: CreateApiKeyUseCase,
    private val validateApiKeyUseCase: ValidateApiKeyUseCase,
    private val listApiKeysUseCase: ListApiKeysUseCase,
    private val deleteApiKeyUseCase: DeleteApiKeyUseCase
) {
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
}
