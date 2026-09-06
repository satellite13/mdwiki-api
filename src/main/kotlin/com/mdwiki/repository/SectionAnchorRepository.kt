package com.mdwiki.repository

import com.mdwiki.model.SectionAnchor
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SectionAnchorRepository : JpaRepository<SectionAnchor, UUID> {
    fun findAllByPageId(pageId: UUID): List<SectionAnchor>
    fun findByStableId(stableId: String): SectionAnchor?
    fun findByPageIdAndStableId(pageId: UUID, stableId: String): SectionAnchor?
}
