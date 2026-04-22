package com.mdwiki.dto

import jakarta.validation.constraints.NotBlank

data class EmbeddingSettingsResponse(
    val provider: String,
    val model: String,
    val expectedDimension: Int,
    val warning: EmbeddingSettingsWarningResponse? = null
)

data class EmbeddingSettingsWarningResponse(
    val code: String,
    val message: String,
    val expectedDimension: Int,
    val actualDimension: Int
)

data class UpdateEmbeddingSettingsRequest(
    @field:NotBlank(message = "provider must not be blank")
    val provider: String,
    @field:NotBlank(message = "model must not be blank")
    val model: String
)
