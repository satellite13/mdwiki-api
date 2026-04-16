package com.mdwiki.dto

import java.time.Instant

data class ApiErrorResponse(
    val error: String,
    val message: String,
    val path: String,
    val timestamp: Instant = Instant.now()
)
