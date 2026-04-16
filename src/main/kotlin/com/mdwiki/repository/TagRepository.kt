package com.mdwiki.repository

import com.mdwiki.model.Tag
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface TagRepository : JpaRepository<Tag, UUID> {
    fun findByName(name: String): Tag?

    @Query("SELECT t FROM Tag t WHERE t.name IN :names")
    fun findByNameIn(names: Collection<String>): List<Tag>

    @Query(
        value = """
            SELECT t.* FROM tags t
            WHERE NOT EXISTS (SELECT 1 FROM page_tags pt WHERE pt.tag_id = t.id)
        """,
        nativeQuery = true
    )
    fun findOrphanedTags(): List<Tag>

    @Query(
        value = """
            SELECT t.id AS id, t.name AS name, COUNT(pt.page_id) AS pageCount
            FROM tags t
            LEFT JOIN page_tags pt ON pt.tag_id = t.id
            GROUP BY t.id, t.name
            ORDER BY t.name ASC
        """,
        nativeQuery = true
    )
    fun findAllWithPageCount(): List<TagWithPageCountView>
}
