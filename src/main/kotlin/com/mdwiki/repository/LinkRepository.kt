package com.mdwiki.repository

import com.mdwiki.model.Link
import com.mdwiki.model.Page
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface LinkRepository : JpaRepository<Link, UUID> {
    fun findBySourcePage(sourcePage: Page): List<Link>
    fun findByTargetSlug(targetSlug: String): List<Link>
    fun findByTargetPage(targetPage: Page): List<Link>
    fun deleteBySourcePage(sourcePage: Page)

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
