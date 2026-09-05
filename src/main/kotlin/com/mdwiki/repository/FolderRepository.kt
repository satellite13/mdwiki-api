package com.mdwiki.repository

import com.mdwiki.model.Folder
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FolderRepository : JpaRepository<Folder, UUID> {
    fun findByParentId(parentId: UUID?): List<Folder>
    fun existsByParentIdAndName(parentId: UUID?, name: String): Boolean
    fun findByOwnerIdAndParentIdIsNullAndName(ownerId: UUID, name: String): Folder?
    fun existsByOwnerIdAndParentIdAndName(ownerId: UUID, parentId: UUID?, name: String): Boolean
    fun existsByOwnerIsNullAndParentIdIsNullAndName(name: String): Boolean
    fun findAllByOwnerId(ownerId: UUID): List<Folder>

    @EntityGraph(attributePaths = ["parent", "owner"])
    override fun findAll(): List<Folder>
}
