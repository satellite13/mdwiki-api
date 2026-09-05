package com.mdwiki.service

import com.mdwiki.model.Page
import com.mdwiki.rag.RagService
import com.mdwiki.repository.PageIndexQueueEntry
import com.mdwiki.repository.PageIndexQueueRepository
import com.mdwiki.repository.PageRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTimeout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.timeout
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Duration
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DeferredPageIndexerTest {
    private val rag = mock<RagService>()
    private val queue = mock<PageIndexQueueRepository>()
    private val pages = mock<PageRepository>()
    private val transactionManager = mock<PlatformTransactionManager>().also {
        whenever(it.getTransaction(anyOrNull())).thenReturn(SimpleTransactionStatus())
    }
    private val indexer = DeferredPageIndexer(rag, queue, pages, transactionManager)

    @AfterEach
    fun close() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
        indexer.shutdown()
    }

    @Test
    fun `afterCommit callback is nonblocking and durable work is drained`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val page = page("blocked")
        val entry = PageIndexQueueEntry(page.id!!, 0, 1)
        whenever(queue.claimDue()).thenReturn(entry, null)
        whenever(queue.lease(any(), any(), any())).thenReturn(true)
        whenever(queue.hasDue()).thenReturn(false)
        whenever(pages.findById(page.id!!)).thenReturn(Optional.of(page))
        doAnswer {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            null
        }.whenever(rag).indexPage(page)

        val callback = register(page)
        verify(queue).enqueue(page.id!!)
        assertTimeout(Duration.ofMillis(250)) { callback.afterCommit() }
        assertTrue(started.await(2, TimeUnit.SECONDS))
        release.countDown()
        verify(queue, timeout(2_000)).delete(entry.copy(attempts = 1))
    }

    @Test
    fun `failed indexing stays durable with bounded retry time`() {
        val page = page("failed")
        whenever(queue.claimDue()).thenReturn(PageIndexQueueEntry(page.id!!, 2, 1), null)
        whenever(queue.lease(any(), any(), any())).thenReturn(true)
        whenever(queue.hasDue()).thenReturn(false)
        whenever(pages.findById(page.id!!)).thenReturn(Optional.of(page))
        doThrow(IllegalStateException("provider down")).whenever(rag).indexPage(page)

        register(page).afterCommit()

        assertTrue(indexer.awaitIdle(Duration.ofSeconds(2)))
        verify(queue).markFailure(any(), any(), any(), any())
        verify(queue, never()).delete(any())
    }

    @Test
    fun `shutdown leaves newly committed request durable without starting work`() {
        val page = page("after-shutdown")
        indexer.shutdown()

        register(page).afterCommit()

        verify(queue).enqueue(page.id!!)
        verify(rag, never()).indexPage(any())
    }

    @Test
    fun `application start drains a durable request from a previous process`() {
        val page = page("from-previous-process")
        val entry = PageIndexQueueEntry(page.id!!, 0, 7)
        whenever(queue.claimDue()).thenReturn(entry, null)
        whenever(queue.lease(any(), any(), any())).thenReturn(true)
        whenever(queue.hasDue()).thenReturn(false)
        whenever(pages.findById(page.id!!)).thenReturn(Optional.of(page))

        indexer.start()

        verify(queue, timeout(2_000)).delete(entry.copy(attempts = 1))
    }

    private fun page(slug: String) =
        Page(id = UUID.randomUUID(), slug = slug, title = slug, contentMd = "body")

    private fun register(page: Page): org.springframework.transaction.support.TransactionSynchronization {
        TransactionSynchronizationManager.initSynchronization()
        indexer.indexAfterCommit(page)
        val callback = TransactionSynchronizationManager.getSynchronizations().single()
        TransactionSynchronizationManager.clearSynchronization()
        return callback
    }
}
