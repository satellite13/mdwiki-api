package com.mdwiki.repository

import com.mdwiki.model.Annotation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AnnotationRepository : JpaRepository<Annotation, UUID> {
    fun findByPageId(pageId: UUID): List<Annotation>
}
