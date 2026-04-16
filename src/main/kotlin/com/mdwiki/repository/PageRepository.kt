package com.mdwiki.repository

import com.mdwiki.model.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PageRepository : JpaRepository<Page, UUID> {
    fun findBySlug(slug: String): Page?
    fun findBySlugAndDeletedAtIsNull(slug: String): Page?
    fun findByDeletedAtIsNotNull(): List<Page>
    fun findAllByDeletedAtIsNull(): List<Page>
    fun findAllByDeletedAtIsNull(pageable: Pageable): org.springframework.data.domain.Page<Page>
    fun existsBySlug(slug: String): Boolean
    fun findByFolderId(folderId: UUID?): List<Page>
    fun findAllBySlugIn(slugs: Collection<String>): List<Page>

    @Query(
        value = """
            SELECT * FROM pages
            WHERE trim(both '-' from regexp_replace(lower(trim(title)), '[^a-z0-9а-яё]+', '-', 'g')) = :slug
            LIMIT 1
        """,
        nativeQuery = true
    )
    fun findByNormalizedTitle(@Param("slug") slug: String): Page?

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

    @Query(
        value = """
            SELECT
                p.id AS id,
                p.slug AS slug,
                p.title AS title,
                ts_headline(
                    'russian',
                    coalesce(p.title, '') || E'\n\n' || coalesce(p.content_md, ''),
                    plainto_tsquery('russian', :query),
                    'StartSel=【, StopSel=】, MaxWords=55, MinWords=18, MaxFragments=2, ShortWord=3, FragmentDelimiter= … '
                ) AS headline
            FROM pages p
            WHERE p.content_tsv @@ plainto_tsquery('russian', :query)
            ORDER BY ts_rank(p.content_tsv, plainto_tsquery('russian', :query)) DESC
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun searchWithHeadline(@Param("query") query: String, @Param("limit") limit: Int): List<PageSearchHit>
}
