package com.mdwiki.repository

import com.mdwiki.model.Page
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PageRepository : JpaRepository<Page, UUID> {
    @Query(
        value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:lockKey)) AS acquired",
        nativeQuery = true
    )
    fun acquireTransactionAdvisoryLock(@Param("lockKey") lockKey: Long): Int

    fun findBySlug(slug: String): Page?
    fun findBySlugAndDeletedAtIsNull(slug: String): Page?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Page p where p.id = :id and p.deletedAt is null")
    fun findActiveByIdForUpdate(@Param("id") id: UUID): Page?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Page p where p.slug = :slug and p.deletedAt is null")
    fun findActiveBySlugForUpdate(@Param("slug") slug: String): Page?

    @Query("select p.id from Page p where p.slug = :slug and p.deletedAt is null")
    fun findActiveIdBySlug(@Param("slug") slug: String): UUID?

    @Query("select p.id from Page p where p.deletedAt is null")
    fun findAllActiveIds(): List<UUID>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Page p where p.slug = :slug")
    fun findBySlugForUpdate(@Param("slug") slug: String): Page?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Page p where p.id in :ids and p.deletedAt is null order by p.id")
    fun findAllActiveByIdInForUpdate(@Param("ids") ids: Collection<UUID>): List<Page>

    @EntityGraph(attributePaths = ["tags"])
    fun findByDeletedAtIsNotNullOrderByDeletedAtDesc(): List<Page>

    @EntityGraph(attributePaths = ["tags"])
    fun findAllByDeletedAtIsNull(): List<Page>

    // Без EntityGraph: join fetch коллекции сломал бы пагинацию; N+1 по tags закрыт @BatchSize на Page.tags.
    fun findAllByDeletedAtIsNull(pageable: Pageable): org.springframework.data.domain.Page<Page>
    fun existsBySlug(slug: String): Boolean
    fun findByFolderId(folderId: UUID?): List<Page>

    @EntityGraph(attributePaths = ["tags"])
    fun findAllBySlugIn(slugs: Collection<String>): List<Page>

    /**
     * Страница по нормализованному title (колонка normalized_title: @PrePersist/@PreUpdate в [Page]
     * + backfill миграцией 002). Soft-delete намеренно не фильтруется — как в прежнем regexp-запросе
     * с LIMIT 1; callers сами проверяют deletedAt, когда это нужно.
     */
    fun findFirstByNormalizedTitle(normalizedTitle: String): Page?

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
