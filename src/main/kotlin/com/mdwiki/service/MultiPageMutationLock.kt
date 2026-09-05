package com.mdwiki.service

import com.mdwiki.repository.PageRepository

/**
 * Единый transaction-scoped PostgreSQL lock для операций, которые меняют несколько страниц.
 * Все callers обязаны вызывать [acquire] до любых page row locks или writes.
 */
object MultiPageMutationLock {
    const val KEY: Long = 0x4D4457494B49L // "MDWIKI"

    fun acquire(pageRepository: PageRepository) {
        pageRepository.acquireTransactionAdvisoryLock(KEY)
    }
}
