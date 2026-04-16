package com.mdwiki.repository

import com.mdwiki.model.Folder
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FolderRepository : JpaRepository<Folder, UUID> {
    fun findByParentId(parentId: UUID?): List<Folder>
    fun existsByParentIdAndName(parentId: UUID?, name: String): Boolean

    @EntityGraph(attributePaths = ["parent"])
    override fun findAll(): List<Folder>
}
