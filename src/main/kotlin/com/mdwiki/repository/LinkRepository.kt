package com.mdwiki.repository

import com.mdwiki.model.Link
import com.mdwiki.model.Page
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LinkRepository : JpaRepository<Link, UUID> {
    fun findBySourcePage(sourcePage: Page): List<Link>
    fun findByTargetSlug(targetSlug: String): List<Link>
    fun findByTargetPage(targetPage: Page): List<Link>
    fun deleteBySourcePage(sourcePage: Page)
}
