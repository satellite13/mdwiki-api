package com.mdwiki.service

import com.mdwiki.model.Page
import com.mdwiki.rag.RagService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

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
class DeferredPageIndexer(private val ragService: RagService) {

    private val log = LoggerFactory.getLogger(DeferredPageIndexer::class.java)

    fun indexAfterCommit(page: Page) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            ragService.indexPage(page)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                runCatching { ragService.indexPage(page) }
                    .onFailure { e ->
                        log.error("Post-commit indexing failed for page '{}': {}", page.slug, e.message)
                    }
            }
        })
    }
}
