package com.mdwiki.service

import com.mdwiki.model.Page
import com.mdwiki.rag.RagService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Откладывает RAG-индексацию страницы до afterCommit текущей транзакции.
 *
 * Индексация — это HTTP-вызовы к embedding-провайдеру; выполняя их внутри
 * транзакции, мы держим коннекшн и локи БД на время сетевых запросов
 * (полный sync большой вики — минуты). После коммита [RagService.indexPage]
 * работает в собственной короткой транзакции на страницу.
 *
 * Вне транзакции индексирует синхронно (прежнее поведение).
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

    // Сериализует DELETE+INSERT чанков: update-путь (afterCommit) и file-watcher
    // (syncSingleFile) могут индексировать одну страницу параллельно, создавая дубли.
    private val indexLock = Any()

    fun indexAfterCommit(page: Page) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            synchronized(indexLock) {
                transactionTemplate.execute { ragService.indexPage(page) }
            }
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                runCatching {
                    synchronized(indexLock) {
                        transactionTemplate.execute { ragService.indexPage(page) }
                    }
                }.onFailure { e ->
                    log.error("Post-commit indexing failed for page '{}': {}", page.slug, e.message)
                }
            }
        })
    }
}
