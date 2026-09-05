package com.mdwiki.repository

import com.mdwiki.model.SavedSearch
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SavedSearchRepository : JpaRepository<SavedSearch, UUID> {
    fun findAllByUserIdOrderByUpdatedAtDesc(userId: UUID): List<SavedSearch>
    fun findByIdAndUserId(id: UUID, userId: UUID): SavedSearch?
    fun existsByUserIdAndNameIgnoreCase(userId: UUID, name: String): Boolean
}
