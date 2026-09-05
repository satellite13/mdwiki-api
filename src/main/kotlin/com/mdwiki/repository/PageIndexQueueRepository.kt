package com.mdwiki.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class PageIndexQueueEntry(
    val pageId: UUID,
    val attempts: Int,
    val requestVersion: Long,
)

@Repository
class PageIndexQueueRepository(private val jdbc: JdbcTemplate) {
    fun enqueue(pageId: UUID) {
        jdbc.update(
            """
            INSERT INTO page_index_queue(page_id) VALUES (?)
            ON CONFLICT (page_id) DO UPDATE SET
              requested_at = now(), attempts = 0, last_error = NULL, next_attempt_at = now(),
              request_version = page_index_queue.request_version + 1
            """.trimIndent(),
            pageId
        )
    }

    fun claimDue(): PageIndexQueueEntry? = jdbc.query(
        """
        SELECT page_id, attempts, request_version FROM page_index_queue
        WHERE next_attempt_at <= now()
        ORDER BY next_attempt_at, requested_at
        LIMIT 1 FOR UPDATE SKIP LOCKED
        """.trimIndent(),
        { rs, _ -> PageIndexQueueEntry(
            rs.getObject("page_id", UUID::class.java),
            rs.getInt("attempts"),
            rs.getLong("request_version")
        ) }
    ).firstOrNull()

    fun lease(entry: PageIndexQueueEntry, attempts: Int, until: Instant): Boolean =
        jdbc.update(
            """
            UPDATE page_index_queue SET attempts = ?, next_attempt_at = ?
            WHERE page_id = ? AND request_version = ?
            """.trimIndent(),
            attempts, Timestamp.from(until), entry.pageId, entry.requestVersion
        ) == 1

    fun delete(entry: PageIndexQueueEntry) {
        jdbc.update(
            "DELETE FROM page_index_queue WHERE page_id = ? AND request_version = ?",
            entry.pageId, entry.requestVersion
        )
    }

    fun markFailure(entry: PageIndexQueueEntry, attempts: Int, error: String, nextAttemptAt: Instant) {
        jdbc.update(
            """
            UPDATE page_index_queue
            SET attempts = ?, last_error = ?, next_attempt_at = ?
            WHERE page_id = ? AND request_version = ?
            """.trimIndent(),
            attempts, error.take(4000), Timestamp.from(nextAttemptAt), entry.pageId, entry.requestVersion
        )
    }

    fun hasDue(): Boolean =
        jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM page_index_queue WHERE next_attempt_at <= now())", Boolean::class.java)
            ?: false
}
