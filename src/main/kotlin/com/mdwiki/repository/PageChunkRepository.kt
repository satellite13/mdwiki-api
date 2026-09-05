package com.mdwiki.repository

import com.mdwiki.model.PageChunk
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface PageChunkRepository : JpaRepository<PageChunk, UUID> {

    fun findByPageIdIn(pageIds: List<UUID>): List<PageChunk>

    @Modifying
    @Transactional
    @Query("DELETE FROM PageChunk pc WHERE pc.page.id = :pageId")
    fun deleteByPageId(pageId: UUID)

    @Modifying
    @Transactional
    @Query(
        value = "UPDATE page_chunks SET embedding = cast(:embedding AS vector) WHERE id = :chunkId",
        nativeQuery = true
    )
    fun updateEmbedding(chunkId: UUID, embedding: String)

    @Query(
        value = """
            SELECT pc.id, pc.page_id, pc.chunk_index, pc.chunk_text, pc.section_heading,
                   1 - (pc.embedding <=> cast(:queryEmbedding AS vector)) AS score
            FROM page_chunks pc
            JOIN pages p ON p.id = pc.page_id
            WHERE pc.embedding IS NOT NULL AND p.deleted_at IS NULL
            ORDER BY pc.embedding <=> cast(:queryEmbedding AS vector)
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findByVectorSimilarity(queryEmbedding: String, limit: Int): List<Array<Any>>
}
