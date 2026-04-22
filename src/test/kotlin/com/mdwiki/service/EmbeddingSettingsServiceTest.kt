package com.mdwiki.service

import com.mdwiki.config.EmbeddingProperties
import com.mdwiki.dto.UpdateEmbeddingSettingsRequest
import com.mdwiki.model.EmbeddingRuntimeSettings
import com.mdwiki.rag.EmbeddingProvider
import com.mdwiki.rag.EmbeddingProviderBuilder
import com.mdwiki.rag.SwitchableEmbeddingProvider
import com.mdwiki.repository.EmbeddingRuntimeSettingsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class EmbeddingSettingsServiceTest {
    private val repository: EmbeddingRuntimeSettingsRepository = mock()
    private val providerBuilder: EmbeddingProviderBuilder = mock()
    private val switchableProvider: SwitchableEmbeddingProvider = mock()
    private val embeddingProperties = EmbeddingProperties(dimension = 1536)

    private val service = EmbeddingSettingsService(
        repository = repository,
        providerBuilder = providerBuilder,
        switchableEmbeddingProvider = switchableProvider,
        embeddingProperties = embeddingProperties
    )

    @Test
    fun `updateSettings returns warning when probe dimension mismatches expected`() {
        val existing = EmbeddingRuntimeSettings(provider = "openai", model = "text-embedding-3-small")
        val candidateProvider: EmbeddingProvider = mock()
        whenever(repository.findBySingletonKey(EmbeddingRuntimeSettings.SINGLETON_KEY)).thenReturn(existing)
        whenever(providerBuilder.normalizeProvider("ollama")).thenReturn("ollama")
        whenever(providerBuilder.create("ollama", "nomic-embed-text")).thenReturn(candidateProvider)
        whenever(candidateProvider.embed("__mdwiki_embedding_probe__")).thenReturn(FloatArray(768))
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as EmbeddingRuntimeSettings }

        val response = service.updateSettings(
            UpdateEmbeddingSettingsRequest(provider = "ollama", model = "nomic-embed-text")
        )

        assertEquals("ollama", response.provider)
        assertEquals("nomic-embed-text", response.model)
        assertNotNull(response.warning)
        assertEquals(768, response.warning?.actualDimension)
        assertEquals(1536, response.warning?.expectedDimension)
        verify(switchableProvider).switchTo(candidateProvider)
    }

    @Test
    fun `updateSettings returns no warning when probe dimension matches expected`() {
        val existing = EmbeddingRuntimeSettings(provider = "openai", model = "text-embedding-3-small")
        val candidateProvider: EmbeddingProvider = mock()
        whenever(repository.findBySingletonKey(EmbeddingRuntimeSettings.SINGLETON_KEY)).thenReturn(existing)
        whenever(providerBuilder.normalizeProvider("openai")).thenReturn("openai")
        whenever(providerBuilder.create("openai", "text-embedding-3-small")).thenReturn(candidateProvider)
        whenever(candidateProvider.embed("__mdwiki_embedding_probe__")).thenReturn(FloatArray(1536))
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as EmbeddingRuntimeSettings }

        val response = service.updateSettings(
            UpdateEmbeddingSettingsRequest(provider = "openai", model = "text-embedding-3-small")
        )

        assertNull(response.warning)
        verify(providerBuilder).create(eq("openai"), eq("text-embedding-3-small"))
        verify(switchableProvider).switchTo(candidateProvider)
    }
}
