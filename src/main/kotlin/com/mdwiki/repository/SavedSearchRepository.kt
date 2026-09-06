package com.mdwiki.repository

import com.mdwiki.model.SavedSearch
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SavedSearchRepository : JpaRepository<SavedSearch, UUID> {
    fun findAllByUserIdOrderByUpdatedAtDesc(userId: UUID): List<SavedSearch>
    fun findByIdAndUserId(id: UUID, userId: UUID): SavedSearch?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SavedSearch s where s.id = :id and s.user.id = :userId")
    fun findByIdAndUserIdForUpdate(@Param("id") id: UUID, @Param("userId") userId: UUID): SavedSearch?
    fun existsByUserIdAndNameIgnoreCase(userId: UUID, name: String): Boolean
}
