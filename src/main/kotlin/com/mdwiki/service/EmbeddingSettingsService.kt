package com.mdwiki.service

import com.mdwiki.config.EmbeddingProperties
import com.mdwiki.dto.EmbeddingSettingsResponse
import com.mdwiki.dto.EmbeddingSettingsWarningResponse
import com.mdwiki.dto.UpdateEmbeddingSettingsRequest
import com.mdwiki.model.EmbeddingRuntimeSettings
import com.mdwiki.rag.EmbeddingProviderBuilder
import com.mdwiki.rag.SwitchableEmbeddingProvider
import com.mdwiki.repository.EmbeddingRuntimeSettingsRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class EmbeddingSettingsService(
    private val repository: EmbeddingRuntimeSettingsRepository,
    private val providerBuilder: EmbeddingProviderBuilder,
    private val switchableEmbeddingProvider: SwitchableEmbeddingProvider,
    private val embeddingProperties: EmbeddingProperties
) {
    private val log = LoggerFactory.getLogger(EmbeddingSettingsService::class.java)

    @PostConstruct
    fun initRuntimeProviderFromSettings() {
        val settings = getOrCreateSettings()
        val runtimeProvider = providerBuilder.create(settings.provider, settings.model)
        switchableEmbeddingProvider.switchTo(runtimeProvider)
        log.info(
            "Initialized runtime embedding settings: provider={}, model={}, dimension={}",
            settings.provider,
            settings.model,
            embeddingProperties.dimension
        )
    }

    @Transactional
    fun getSettings(): EmbeddingSettingsResponse {
        val settings = getOrCreateSettings()
        return EmbeddingSettingsResponse(
            provider = settings.provider,
            model = settings.model,
            expectedDimension = embeddingProperties.dimension
        )
    }

    @Transactional
    fun updateSettings(request: UpdateEmbeddingSettingsRequest): EmbeddingSettingsResponse {
        val settings = getOrCreateSettings()
        val provider = providerBuilder.normalizeProvider(request.provider)
        val model = request.model.trim()
        if (model.isBlank()) {
            throw IllegalArgumentException("Embedding model must not be blank")
        }

        val runtimeProvider = providerBuilder.create(provider, model)
        val actualDimension = runtimeProvider.embed("__mdwiki_embedding_probe__").size
        val expectedDimension = embeddingProperties.dimension

        settings.provider = provider
        settings.model = model
        settings.updatedAt = Instant.now()
        repository.save(settings)
        switchableEmbeddingProvider.switchTo(runtimeProvider)

        val warning = if (actualDimension != expectedDimension) {
            EmbeddingSettingsWarningResponse(
                code = "EMBEDDING_DIMENSION_MISMATCH",
                message = "Embedding dimension mismatch detected ($actualDimension vs expected $expectedDimension). " +
                    "Provider/model was updated, but you should run POST /api/sync/reindex before relying on search quality.",
                expectedDimension = expectedDimension,
                actualDimension = actualDimension
            )
        } else {
            null
        }

        log.info(
            "Updated embedding runtime settings: provider={}, model={}, expectedDimension={}, actualDimension={}",
            provider,
            model,
            expectedDimension,
            actualDimension
        )

        return EmbeddingSettingsResponse(
            provider = provider,
            model = model,
            expectedDimension = expectedDimension,
            warning = warning
        )
    }

    private fun getOrCreateSettings(): EmbeddingRuntimeSettings {
        repository.findBySingletonKey(EmbeddingRuntimeSettings.SINGLETON_KEY)?.let { return it }
        val provider = providerBuilder.normalizeProvider(embeddingProperties.provider)
        val model = providerBuilder.defaultModelFor(provider)
        val now = Instant.now()
        return repository.save(
            EmbeddingRuntimeSettings(
                singletonKey = EmbeddingRuntimeSettings.SINGLETON_KEY,
                provider = provider,
                model = model,
                createdAt = now,
                updatedAt = now
            )
        )
    }
}
