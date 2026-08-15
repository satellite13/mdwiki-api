package com.mdwiki.repository

import com.mdwiki.model.PageSection
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PageSectionRepository : JpaRepository<PageSection, UUID> {
    fun findByPageIdOrderBySortOrder(pageId: UUID): List<PageSection>
    fun findByPageIdAndStableKey(pageId: UUID, stableKey: String): PageSection?
    fun countByPageId(pageId: UUID): Long
}
