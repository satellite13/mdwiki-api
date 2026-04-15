package com.mdwiki.dto

import java.util.UUID

data class TagResponse(
    val id: UUID,
    val name: String,
    val pageCount: Long
)
