package com.mdwiki.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateFolderRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 255)
    val name: String,
    val parentId: UUID? = null
)
data class UpdateFolderRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 255)
    val name: String
)
data class MoveFolderRequest(val parentId: UUID?)

data class FolderResponse(val id: UUID, val name: String, val parentId: UUID?, val sortOrder: Int, val createdAt: Instant)

data class FolderTreeNode(
    val id: String,
    val name: String,
    val type: String,
    val slug: String? = null,
    val children: List<FolderTreeNode> = emptyList()
)
