package com.mdwiki.repository

import com.mdwiki.model.EmbeddingRuntimeSettings
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EmbeddingRuntimeSettingsRepository : JpaRepository<EmbeddingRuntimeSettings, UUID> {
    fun findBySingletonKey(singletonKey: String): EmbeddingRuntimeSettings?
}
