package com.mdwiki.dto

import java.time.Instant
import java.util.UUID

data class CreateFolderRequest(val name: String, val parentId: UUID? = null)
data class UpdateFolderRequest(val name: String)
data class MoveFolderRequest(val parentId: UUID?)

data class FolderResponse(val id: UUID, val name: String, val parentId: UUID?, val sortOrder: Int, val createdAt: Instant)

data class FolderTreeNode(
    val id: String,
    val name: String,
    val type: String,
    val slug: String? = null,
    val children: List<FolderTreeNode> = emptyList()
)
