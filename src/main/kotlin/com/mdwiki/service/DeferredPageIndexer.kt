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
import java.util.concurrent.CompletableFuture
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
        try {
            executor.execute {
                runCatching { executeIndex(page) }
                    .onFailure { error ->
                        log.error("Post-commit indexing failed for page '{}'", page.slug, error)
                    }
            }
        } catch (error: RejectedExecutionException) {
            log.error("Post-commit indexing queue rejected page '{}'", page.slug, error)
        }
    }

    private fun executeIndex(page: Page) {
        synchronized(indexLock) {
            transactionTemplate.execute { ragService.indexPage(page) }
        }
    }

    /** Waits for all tasks submitted before this call. */
    fun awaitIdle(timeout: Duration): Boolean {
        val barrier = CompletableFuture<Unit>()
        try {
            executor.execute {
                synchronized(indexLock) { barrier.complete(Unit) }
            }
        } catch (_: RejectedExecutionException) {
            return executor.isTerminated
        }
        return try {
            barrier.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
            true
        } catch (_: Exception) {
            false
        }
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdown()
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }
    }
}
