package com.mdwiki.controller

import com.mdwiki.dto.ApiKeyCreatedResponse
import com.mdwiki.dto.ApiKeyResponse
import com.mdwiki.dto.CreateApiKeyRequest
import com.mdwiki.service.ApiKeyService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/api-keys")
class ApiKeyController(private val apiKeyService: ApiKeyService) {
    @PostMapping
    fun create(@RequestBody request: CreateApiKeyRequest, auth: Authentication): ApiKeyCreatedResponse =
        apiKeyService.create(request, auth.name)

    @GetMapping
    fun list(auth: Authentication): List<ApiKeyResponse> = apiKeyService.listKeys(auth.name)

    @DeleteMapping("/{keyId}")
    fun delete(@PathVariable keyId: UUID, auth: Authentication) = apiKeyService.deleteKey(keyId, auth.name)
}
