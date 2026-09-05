package com.mdwiki.service

import com.mdwiki.model.Page
import com.mdwiki.rag.RagService
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

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
    private val pending = ConcurrentHashMap<String, Page>()
    private val scheduled = ConcurrentHashMap.newKeySet<String>()
    private val needsReindex = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var shuttingDown = false

    // Сериализует DELETE+INSERT чанков с синхронным file-watcher path.
    private val indexLock = Any()

    fun indexAfterCommit(page: Page) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            executeIndex(page)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                submit(page)
            }
        })
    }

    private fun submit(page: Page) {
        val key = page.id?.toString() ?: page.slug
        pending[key] = page
        if (!scheduled.add(key)) return
        dispatch(key, 0)
    }

    private fun dispatch(key: String, attempt: Int) {
        if (shuttingDown) {
            markNeedsReindex(key)
            return
        }
        try {
            executor.execute {
                val page = pending.remove(key)
                if (page == null) {
                    scheduled.remove(key)
                    return@execute
                }
                runCatching { executeIndex(page) }
                    .onSuccess { completeOrResubmit(key) }
                    .onFailure { error -> retry(key, page, attempt, error) }
            }
        } catch (error: RejectedExecutionException) {
            retry(key, pending[key], attempt, error)
        }
    }

    private fun completeOrResubmit(key: String) {
        if (pending.containsKey(key)) {
            dispatch(key, 0)
            return
        }
        scheduled.remove(key)
        if (pending.containsKey(key) && scheduled.add(key)) dispatch(key, 0)
    }

    private fun retry(key: String, page: Page?, attempt: Int, error: Throwable) {
        if (page != null) pending.putIfAbsent(key, page)
        if (attempt >= 6 || shuttingDown) {
            log.error("Deferred indexing requires reindex for page '{}'", page?.slug ?: key, error)
            markNeedsReindex(key)
            return
        }
        val delayMs = (10L shl attempt.coerceAtMost(7)).coerceAtMost(1000L)
        try {
            retryExecutor.schedule({ dispatch(key, attempt + 1) }, delayMs, TimeUnit.MILLISECONDS)
        } catch (_: RejectedExecutionException) {
            markNeedsReindex(key)
        }
    }

    private fun markNeedsReindex(key: String) {
        pending.remove(key)
        scheduled.remove(key)
        needsReindex.add(key)
    }

    private fun executeIndex(page: Page) {
        synchronized(indexLock) {
            transactionTemplate.execute { ragService.indexPage(page) }
        }
    }

    /** Waits for all tasks submitted before this call. */
    fun awaitIdle(timeout: Duration): Boolean {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (pending.isEmpty() && scheduled.isEmpty() &&
                executor.activeCount == 0 && executor.queue.isEmpty()
            ) return true
            Thread.sleep(10)
        }
        return false
    }

    fun needsReindexKeys(): Set<String> = needsReindex.toSet()

    @PreDestroy
    fun shutdown() {
        shuttingDown = true
        retryExecutor.shutdown()
        executor.shutdown()
        if (!executor.awaitTermination(10, TimeUnit.SECONDS) || pending.isNotEmpty()) {
            pending.keys.toList().forEach(::markNeedsReindex)
            executor.shutdownNow()
        }
        retryExecutor.shutdownNow()
    }
}
