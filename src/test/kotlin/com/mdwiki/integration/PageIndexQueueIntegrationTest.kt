package com.mdwiki.integration

import com.mdwiki.model.Page
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.PageIndexQueueRepository
import com.mdwiki.rag.EmbeddingProvider
import com.mdwiki.rag.RagService
import com.mdwiki.service.DeferredPageIndexer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.util.UUID

@SpringBootTest
class PageIndexQueueIntegrationTest {
    @Autowired lateinit var pages: PageRepository
    @Autowired lateinit var indexer: DeferredPageIndexer
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var transactionManager: PlatformTransactionManager
    @Autowired lateinit var ragService: RagService
    @Autowired lateinit var queue: PageIndexQueueRepository
    @MockitoBean lateinit var embeddingProvider: EmbeddingProvider

    @BeforeEach
    fun clearQueue() {
        jdbc.update("delete from page_index_queue")
    }

    @Test
    fun `rolled back page mutation leaves no durable indexing request`() {
        val slug = "queue-rollback-${UUID.randomUUID()}"

        assertThrows(IllegalStateException::class.java) {
            TransactionTemplate(transactionManager).executeWithoutResult {
                val page = pages.saveAndFlush(Page(slug = slug, title = "Rollback", contentMd = "body"))
                indexer.indexAfterCommit(page)
                throw IllegalStateException("rollback")
            }
        }

        assertThat(pages.findBySlug(slug)).isNull()
        assertThat(jdbc.queryForObject("select count(*) from page_index_queue", Long::class.java)).isZero()
    }

    @Test
    fun `failed indexing remains durable then succeeds without duplicate chunks`() {
        whenever(embeddingProvider.embed(any<List<String>>())).thenThrow(IllegalStateException("temporary"))
        val page = saveAndQueue("queue-retry-${UUID.randomUUID()}", "retry body")
        indexer.processDueNow()

        await { queueCount(page.id!!) == 1L && queueAttempts(page.id!!) > 0 }
        assertThat(indexer.awaitIdle(Duration.ofSeconds(2))).isTrue()
        whenever(embeddingProvider.embed(any<List<String>>())).thenReturn(listOf(embedding()))
        jdbc.update("update page_index_queue set next_attempt_at = now() where page_id = ?", page.id)
        indexer.processDueNow()

        await { queueCount(page.id!!) == 0L }
        assertThat(jdbc.queryForObject(
            "select count(*) from page_chunks where page_id = ?", Long::class.java, page.id
        )).isEqualTo(1L)
    }

    @Test
    fun `repeated requests coalesce and index latest page content once`() {
        whenever(embeddingProvider.embed(any<List<String>>())).thenReturn(listOf(embedding()))
        val page = TransactionTemplate(transactionManager).execute {
            val saved = pages.saveAndFlush(Page(
                slug = "queue-latest-${UUID.randomUUID()}", title = "Latest", contentMd = "old body"
            ))
            indexer.indexAfterCommit(saved)
            saved.contentMd = "latest body"
            pages.saveAndFlush(saved)
            indexer.indexAfterCommit(saved)
            assertThat(queueCount(saved.id!!)).isEqualTo(1L)
            saved
        }

        await { queueCount(page.id!!) == 0L }
        assertThat(jdbc.queryForList(
            "select chunk_text from page_chunks where page_id = ? order by chunk_index", String::class.java, page.id
        )).containsExactly("latest body")
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `worker restart picks request committed while previous worker is shut down`() {
        indexer.shutdown()
        whenever(embeddingProvider.embed(any<List<String>>())).thenReturn(listOf(embedding()))
        val page = saveAndQueue("queue-restart-${UUID.randomUUID()}", "restart body")
        assertThat(queueCount(page.id!!)).isEqualTo(1L)

        val restarted = DeferredPageIndexer(ragService, queue, pages, transactionManager)
        try {
            restarted.start()
            restarted.processDueNow()
            await { queueCount(page.id!!) == 0L }
        } finally {
            restarted.shutdown()
        }
    }

    private fun saveAndQueue(slug: String, content: String): Page =
        TransactionTemplate(transactionManager).execute {
            val page = pages.saveAndFlush(Page(slug = slug, title = slug, contentMd = content))
            indexer.indexAfterCommit(page)
            page
        }

    private fun queueCount(pageId: UUID): Long =
        jdbc.queryForObject("select count(*) from page_index_queue where page_id = ?", Long::class.java, pageId)!!

    private fun queueAttempts(pageId: UUID): Int =
        jdbc.queryForObject("select attempts from page_index_queue where page_id = ?", Int::class.java, pageId) ?: 0

    private fun embedding() = FloatArray(embeddingDimension()) { 0.01f }

    private fun embeddingDimension(): Int = jdbc.queryForObject(
        """
        SELECT a.atttypmod
        FROM pg_attribute a
        JOIN pg_class c ON c.oid = a.attrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = current_schema()
          AND c.relname = 'page_chunks'
          AND a.attname = 'embedding'
        """.trimIndent(),
        Int::class.java,
    )!!

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + java.time.Duration.ofSeconds(12).toNanos()
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertThat(condition()).isTrue()
    }
}
