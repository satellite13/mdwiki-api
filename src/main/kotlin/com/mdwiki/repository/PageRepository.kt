package com.mdwiki.repository

import com.mdwiki.model.Page
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface PageRepository : JpaRepository<Page, UUID> {
    fun findBySlug(slug: String): Page?
    fun existsBySlug(slug: String): Boolean
    fun findByFolderId(folderId: UUID?): List<Page>
    fun findAllBySlugIn(slugs: Collection<String>): List<Page>

    @Query(
        value = """
            SELECT p.* FROM pages p
            WHERE p.content_tsv @@ plainto_tsquery('russian', :query)
            ORDER BY ts_rank(p.content_tsv, plainto_tsquery('russian', :query)) DESC
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun fullTextSearch(query: String, limit: Int = 20): List<Page>
}
