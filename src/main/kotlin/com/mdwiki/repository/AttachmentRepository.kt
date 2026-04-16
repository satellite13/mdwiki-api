package com.mdwiki.repository

import com.mdwiki.model.Attachment
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AttachmentRepository : JpaRepository<Attachment, UUID> {
    fun findByStoredName(storedName: String): Attachment?
    fun findByPageId(pageId: UUID, pageable: Pageable): Page<Attachment>
    fun findAllBy(pageable: Pageable): Page<Attachment>
}
