package com.mdwiki.mapper

import com.mdwiki.dto.FolderResponse
import com.mdwiki.model.Folder

fun Folder.toResponse(): FolderResponse = FolderResponse(
    id = id!!,
    name = name,
    parentId = parent?.id,
    sortOrder = sortOrder,
    createdAt = createdAt
)
