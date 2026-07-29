package com.mdwiki.repository

import com.mdwiki.model.Link
import com.mdwiki.model.Page
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface LinkRepository : JpaRepository<Link, UUID> {
    @EntityGraph(attributePaths = ["targetPage"])
    fun findBySourcePage(sourcePage: Page): List<Link>

    // fetch join sourcePage: иначе каждый доступ к sourcePage (slug/deletedAt) — отдельный запрос (N+1).
    @Query(
        """
        SELECT l FROM Link l
        JOIN FETCH l.sourcePage
        LEFT JOIN FETCH l.targetPage
        WHERE l.targetSlug = :targetSlug
        """
    )
    fun findByTargetSlug(@Param("targetSlug") targetSlug: String): List<Link>

    fun findByTargetPage(targetPage: Page): List<Link>
    fun deleteBySourcePage(sourcePage: Page)

    /** Полный граф: страницы по обе стороны ссылки подгружаются одним запросом. */
    @Query("SELECT l FROM Link l JOIN FETCH l.sourcePage LEFT JOIN FETCH l.targetPage")
    fun findAllWithPages(): List<Link>

    /** `clearAutomatically = false`: иначе после UPDATE сбрасывается PC и ломаются ссылки на [Page] в той же транзакции. */
    @Modifying(clearAutomatically = false, flushAutomatically = true)
    @Query("UPDATE Link l SET l.targetSlug = :newSlug WHERE l.targetSlug = :oldSlug")
    fun updateAllTargetSlugs(@Param("oldSlug") oldSlug: String, @Param("newSlug") newSlug: String): Int

    @Query(
        """
        SELECT l FROM Link l
        JOIN FETCH l.sourcePage s
        WHERE l.targetPage IS NULL AND s.deletedAt IS NULL
        ORDER BY l.targetSlug, s.title
        """
    )
    fun findAllDangling(): List<Link>
}
