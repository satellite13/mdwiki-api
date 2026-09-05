package com.mdwiki.service

import com.mdwiki.model.Page
import com.mdwiki.rag.RagService
import com.mdwiki.repository.PageIndexQueueEntry
import com.mdwiki.repository.PageIndexQueueRepository
import com.mdwiki.repository.PageRepository
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Откладывает RAG-индексацию страницы до afterCommit текущей транзакции.
 *
 * Callback только ставит задачу в bounded single-worker queue. Worker открывает
 * отдельную транзакцию после возврата callback, когда исходный connection уже
 * освобождён. Вне транзакции сохраняется прежнее синхронное поведение.
 */
@Component
class DeferredPageIndexer(
    private val ragService: RagService,
    private val queueRepository: PageIndexQueueRepository,
    private val pageRepository: PageRepository,
    transactionManager: PlatformTransactionManager
) {
    private val log = LoggerFactory.getLogger(DeferredPageIndexer::class.java)
    private val transactionTemplate = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }
    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(256),
        { task -> Thread(task, "deferred-page-indexer").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )
    private val retryExecutor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "deferred-page-indexer-retry").apply { isDaemon = true }
    }
    private val drainScheduled = AtomicBoolean(false)
    @Value("\${mdwiki.indexing.startup-enabled:true}")
    private var startupEnabled: Boolean = true
    @Volatile private var shuttingDown = false

    // Сериализует DELETE+INSERT чанков с синхронным file-watcher path.
    private val indexLock = Any()

    @Transactional
    fun indexAfterCommit(page: Page) {
        val pageId = requireNotNull(page.id) { "Cannot queue an unsaved page for indexing" }
        queueRepository.enqueue(pageId)
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                scheduleDrain()
            }
        })
    }

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        if (shuttingDown || !startupEnabled) return
        retryExecutor.scheduleWithFixedDelay({ scheduleDrain() }, 0, 250, TimeUnit.MILLISECONDS)
    }

    fun processDueNow() = scheduleDrain()

    private fun scheduleDrain() {
        if (shuttingDown || !drainScheduled.compareAndSet(false, true)) return
        try {
            executor.execute { drain() }
        } catch (_: RejectedExecutionException) {
            drainScheduled.set(false)
            if (!shuttingDown) {
                try {
                    retryExecutor.schedule({ scheduleDrain() }, 25, TimeUnit.MILLISECONDS)
                } catch (_: RejectedExecutionException) {
                    // The durable row remains for the next process/startup.
                }
            }
        }
    }

    private fun drain() {
        try {
            while (!shuttingDown && processOne()) {
                // Drain all currently due durable requests serially.
            }
        } finally {
            drainScheduled.set(false)
            if (!shuttingDown && queueRepository.hasDue()) scheduleDrain()
        }
    }

    private fun processOne(): Boolean {
        val claimed = transactionTemplate.execute {
            val entry = queueRepository.claimDue() ?: return@execute null
            val attempts = entry.attempts + 1
            if (!queueRepository.lease(entry, attempts, Instant.now().plusSeconds(300))) null
            else entry.copy(attempts = attempts)
        } ?: return false
        try {
            transactionTemplate.executeWithoutResult {
                val page = pageRepository.findById(claimed.pageId).orElse(null)
                if (page == null) {
                    return@executeWithoutResult
                }
                executeIndex(page)
            }
            transactionTemplate.executeWithoutResult { queueRepository.delete(claimed) }
        } catch (error: Exception) {
            val delaySeconds = (1L shl claimed.attempts.coerceAtMost(8)).coerceAtMost(300)
            transactionTemplate.executeWithoutResult {
                queueRepository.markFailure(
                    claimed,
                    claimed.attempts,
                    error.message ?: error.javaClass.simpleName,
                    Instant.now().plusSeconds(delaySeconds)
                )
            }
            log.error(
                "Deferred indexing failed for page '{}'; durable retry {} scheduled",
                claimed.pageId, claimed.attempts, error
            )
        }
        return true
    }

    private fun executeIndex(page: Page) {
        synchronized(indexLock) {
            ragService.indexPage(page)
        }
    }

    /** Waits for all tasks submitted before this call. */
    fun awaitIdle(timeout: Duration): Boolean {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (!drainScheduled.get() && executor.activeCount == 0 && executor.queue.isEmpty()
            ) return true
            Thread.sleep(10)
        }
        return false
    }

    @PreDestroy
    fun shutdown() {
        shuttingDown = true
        retryExecutor.shutdown()
        executor.shutdown()
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }
        retryExecutor.shutdownNow()
    }
}
