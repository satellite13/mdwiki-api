package com.mdwiki.repository

import com.mdwiki.model.ApiKey
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ApiKeyRepository : JpaRepository<ApiKey, UUID> {
    fun findByKeyHash(keyHash: String): ApiKey?
    fun findByUserId(userId: UUID): List<ApiKey>
}
