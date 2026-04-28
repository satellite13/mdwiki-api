package com.mdwiki.controller

import com.mdwiki.dto.EmbeddingSettingsResponse
import com.mdwiki.dto.EmbeddingSettingsWarningResponse
import com.mdwiki.dto.UpdateEmbeddingSettingsRequest
import com.mdwiki.service.EmbeddingSettingsService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class AdminEmbeddingSettingsControllerTest {
    private val embeddingSettingsService: EmbeddingSettingsService = mock()
    private val controller = AdminEmbeddingSettingsController(embeddingSettingsService)

    @Test
    fun `getSettings returns service payload`() {
        whenever(embeddingSettingsService.getSettings()).thenReturn(
            EmbeddingSettingsResponse(
                provider = "openai",
                model = "text-embedding-3-small",
                baseUrl = "https://api.openai.com/v1",
                apiKeyConfigured = true,
                expectedDimension = 1536
            )
        )

        val response = controller.getSettings()

        assertEquals("openai", response.provider)
        assertEquals("text-embedding-3-small", response.model)
        assertEquals("https://api.openai.com/v1", response.baseUrl)
        assertEquals(true, response.apiKeyConfigured)
        assertEquals(1536, response.expectedDimension)
        verify(embeddingSettingsService).getSettings()
    }

    @Test
    fun `updateSettings returns warning from service`() {
        val request = UpdateEmbeddingSettingsRequest(provider = "ollama", model = "nomic-embed-text", baseUrl = "http://localhost:11434")
        whenever(embeddingSettingsService.updateSettings(request)).thenReturn(
            EmbeddingSettingsResponse(
                provider = "ollama",
                model = "nomic-embed-text",
                baseUrl = "http://localhost:11434",
                apiKeyConfigured = false,
                expectedDimension = 1536,
                warning = EmbeddingSettingsWarningResponse(
                    code = "EMBEDDING_DIMENSION_MISMATCH",
                    message = "Embedding dimension mismatch detected",
                    expectedDimension = 1536,
                    actualDimension = 768
                )
            )
        )

        val response = controller.updateSettings(request)

        assertEquals("ollama", response.provider)
        assertEquals("http://localhost:11434", response.baseUrl)
        assertNotNull(response.warning)
        assertEquals(768, response.warning?.actualDimension)
        verify(embeddingSettingsService).updateSettings(request)
    }
}
