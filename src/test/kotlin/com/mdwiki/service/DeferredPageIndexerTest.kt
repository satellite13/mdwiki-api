package com.mdwiki.service

import com.mdwiki.model.Page
import com.mdwiki.rag.RagService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTimeout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DeferredPageIndexerTest {
    private val rag = mock<RagService>()
    private val transactionManager = mock<PlatformTransactionManager>().also {
        whenever(it.getTransaction(anyOrNull())).thenReturn(SimpleTransactionStatus())
    }
    private val indexer = DeferredPageIndexer(rag, transactionManager)

    @AfterEach
    fun close() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
        indexer.shutdown()
    }

    @Test
    fun `afterCommit callback is nonblocking and awaitIdle is deterministic`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val page = Page(slug = "blocked", title = "Blocked", contentMd = "")
        doAnswer {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            null
        }.whenever(rag).indexPage(page)

        val callback = register(page)
        assertTimeout(Duration.ofMillis(250)) { callback.afterCommit() }
        assertTrue(started.await(2, TimeUnit.SECONDS))
        release.countDown()
        assertTrue(indexer.awaitIdle(Duration.ofSeconds(2)))
        verify(rag).indexPage(page)
    }

    @Test
    fun `failed task does not stop subsequent indexing`() {
        val failed = Page(slug = "failed", title = "Failed", contentMd = "")
        val healthy = Page(slug = "healthy", title = "Healthy", contentMd = "")
        doThrow(IllegalStateException("provider down")).whenever(rag).indexPage(failed)

        register(failed).afterCommit()
        register(healthy).afterCommit()

        assertTrue(indexer.awaitIdle(Duration.ofSeconds(2)))
        verify(rag, atLeastOnce()).indexPage(failed)
        verify(rag).indexPage(healthy)
        assertTrue(indexer.needsReindexKeys().contains("failed"))
    }

    @Test
    fun `queue saturation retries rejected pages until indexed`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = Page(slug = "first", title = "First", contentMd = "")
        doAnswer {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            null
        }.whenever(rag).indexPage(first)
        register(first).afterCommit()
        assertTrue(started.await(2, TimeUnit.SECONDS))

        val overflow = (1..270).map { Page(slug = "queued-$it", title = "Queued", contentMd = "") }
        overflow.forEach { register(it).afterCommit() }
        release.countDown()

        assertTrue(indexer.awaitIdle(Duration.ofSeconds(8)))
        verify(rag).indexPage(overflow.last())
        assertTrue(indexer.needsReindexKeys().isEmpty())
    }

    @Test
    fun `shutdown marks newly rejected work for explicit reindex`() {
        indexer.shutdown()
        val page = Page(slug = "after-shutdown", title = "After shutdown", contentMd = "")
        register(page).afterCommit()
        assertTrue(indexer.needsReindexKeys().contains("after-shutdown"))
    }

    private fun register(page: Page): org.springframework.transaction.support.TransactionSynchronization {
        TransactionSynchronizationManager.initSynchronization()
        indexer.indexAfterCommit(page)
        val callback = TransactionSynchronizationManager.getSynchronizations().single()
        TransactionSynchronizationManager.clearSynchronization()
        return callback
    }
}
