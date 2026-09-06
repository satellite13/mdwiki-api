package com.mdwiki.repository

import com.mdwiki.model.PagePropertyValue
import com.mdwiki.model.PagePropertyValueId
import com.mdwiki.model.PropertyDefinition
import com.mdwiki.model.SavedView
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PropertyDefinitionRepository : JpaRepository<PropertyDefinition, UUID> {
    fun findAllByDeletedAtIsNullOrderByDisplayNameAsc(): List<PropertyDefinition>
    fun findByKeyIgnoreCaseAndDeletedAtIsNull(key: String): PropertyDefinition?
    fun existsByKeyIgnoreCaseAndDeletedAtIsNull(key: String): Boolean
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByIdAndDeletedAtIsNull(id: UUID): PropertyDefinition?
}
interface PagePropertyValueRepository : JpaRepository<PagePropertyValue, PagePropertyValueId> {
    fun findAllByPageId(pageId: UUID): List<PagePropertyValue>
    fun deleteAllByPageId(pageId: UUID)
}
interface SavedViewRepository : JpaRepository<SavedView, UUID> {
    fun findAllByUserIdOrderByUpdatedAtDesc(userId: UUID): List<SavedView>
    fun findByIdAndUserId(id: UUID, userId: UUID): SavedView?
    fun existsByUserIdAndNameIgnoreCase(userId: UUID, name: String): Boolean
}
