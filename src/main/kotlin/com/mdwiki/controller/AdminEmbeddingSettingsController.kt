package com.mdwiki.controller

import com.mdwiki.dto.EmbeddingSettingsResponse
import com.mdwiki.dto.UpdateEmbeddingSettingsRequest
import com.mdwiki.service.EmbeddingSettingsService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/embedding-settings")
class AdminEmbeddingSettingsController(
    private val embeddingSettingsService: EmbeddingSettingsService
) {
    @GetMapping
    fun getSettings(): EmbeddingSettingsResponse = embeddingSettingsService.getSettings()

    @PutMapping
    fun updateSettings(
        @Valid @RequestBody request: UpdateEmbeddingSettingsRequest
    ): EmbeddingSettingsResponse = embeddingSettingsService.updateSettings(request)
}
