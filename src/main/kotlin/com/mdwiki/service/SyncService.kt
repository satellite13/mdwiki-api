package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.model.Folder
import com.mdwiki.rag.RagService
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.service.usecase.WikiSyncEngine
import org.slf4j.LoggerFactory
import jakarta.annotation.PreDestroy
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Service
class SyncService(
    private val pageRepository: PageRepository,
    private val folderRepository: FolderRepository,
    private val wikiProperties: WikiProperties,
    private val treeEventsService: TreeEventsService,
    @param:Lazy private val folderService: FolderService,
    private val wikiFileService: WikiFileService,
    private val wikiSyncEngine: WikiSyncEngine,
    private val ragService: RagService,
    private val attachmentService: AttachmentService,
    transactionManager: PlatformTransactionManager
) {
    private val log = LoggerFactory.getLogger(SyncService::class.java)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /** Serializes disk↔DB sync so the watcher and fullSync (or concurrent API calls) cannot interleave inserts. */
    private val wikiSyncLock = Any()

    /** Coalesces bursts of filesystem deletes (e.g. rm -rf) into one reconcile pass. */
    private val reconcileScheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "wiki-disk-reconcile").apply { isDaemon = true }
        }
    private val reconcileScheduleLock = Any()
    private var reconcileFuture: ScheduledFuture<*>? = null

    data class SyncResult(val added: Int, val updated: Int, val removed: Int, val attachmentsAdded: Int = 0)

    data class ReindexResult(val total: Int, val reindexed: Int, val failed: Int)

    /**
     * Перегоняет все не-удалённые страницы через RAG-индексацию заново: удаляет старые чанки,
     * пересоздаёт их и эмбеддинги. Полезно после смены модели/размерности эмбеддингов или
     * после миграции, которая очистила `page_chunks`.
     *
     * Каждая страница индексируется в своей транзакции (через RagService.indexPage @Transactional),
     * поэтому падение на одной странице не откатит остальные.
     */
    fun reindexAll(): ReindexResult = synchronized(wikiSyncLock) {
        val pages = pageRepository.findAllByDeletedAtIsNull()
        var reindexed = 0
        var failed = 0
        log.info("Reindex: starting for {} pages", pages.size)
        for (page in pages) {
            try {
                ragService.indexPage(page)
                reindexed++
            } catch (e: Exception) {
                failed++
                log.error("Reindex: failed for page '{}': {}", page.slug, e.message)
            }
        }
        log.info("Reindex: done. total={}, reindexed={}, failed={}", pages.size, reindexed, failed)
        ReindexResult(total = pages.size, reindexed = reindexed, failed = failed)
    }

    @Transactional
    fun fullSync(): SyncResult = synchronized(wikiSyncLock) {
        MultiPageMutationLock.acquire(pageRepository)
        val result = wikiSyncEngine.fullSync()
        val attachmentsAdded = attachmentService.syncFromDisk().added
        val merged = SyncResult(result.added, result.updated, result.removed, attachmentsAdded)
        if (merged.added > 0 || merged.updated > 0 || merged.removed > 0 || merged.attachmentsAdded > 0) {
            folderService.invalidateCache()
            treeEventsService.publishTreeUpdated()
        }
        merged
    }

    @Transactional
    fun syncSingleFile(file: File) = synchronized(wikiSyncLock) {
        wikiSyncEngine.syncSingleFile(file)
        folderService.invalidateCache()
        treeEventsService.publishTreeUpdated()
    }

    @Transactional
    fun removePage(slug: String) = synchronized(wikiSyncLock) {
        wikiSyncEngine.removePage(slug)
        folderService.invalidateCache()
        treeEventsService.publishTreeUpdated()
    }

    /**
     * Full disk↔DB reconcile: removes pages whose files disappeared, then removes folder rows
     * whose directories no longer exist under [WikiProperties.contentDir].
     * Used when the watcher sees non-.md deletes (typically removed directories).
     */
    @Transactional
    fun reconcileFromDisk(): SyncResult = synchronized(wikiSyncLock) {
        reconcileFromDiskBody()
    }

    fun scheduleReconcileFromDisk() {
        synchronized(reconcileScheduleLock) {
            reconcileFuture?.cancel(false)
            reconcileFuture = reconcileScheduler.schedule(
                {
                    transactionTemplate.execute {
                        synchronized(wikiSyncLock) {
                            reconcileFromDiskBody()
                        }
                        null
                    }
                },
                400,
                TimeUnit.MILLISECONDS
            )
        }
    }

    private fun reconcileFromDiskBody(): SyncResult {
        MultiPageMutationLock.acquire(pageRepository)
        val result = wikiSyncEngine.fullSync()
        val attachmentsAdded = attachmentService.syncFromDisk().added
        val merged = SyncResult(result.added, result.updated, result.removed, attachmentsAdded)
        val prunedFolders = pruneMissingFolders()
        if (merged.added > 0 || merged.updated > 0 || merged.removed > 0 || merged.attachmentsAdded > 0 || prunedFolders > 0) {
            folderService.invalidateCache()
            treeEventsService.publishTreeUpdated()
        }
        return merged
    }

    @PreDestroy
    fun shutdownReconcileScheduler() {
        reconcileScheduler.shutdown()
    }

    private fun pruneMissingFolders(): Int {
        val root = File(wikiProperties.contentDir).canonicalFile
        if (!root.exists()) return 0

        val allFolders = folderRepository.findAll()
        fun depth(f: Folder): Int {
            var d = 0
            var p: Folder? = f.parent
            while (p != null) {
                d++
                p = p.parent
            }
            return d
        }

        val missing = allFolders
            .filter { it.id != null && !resolveFolderDirectoryOnDisk(it, root).exists() }
            .sortedByDescending { depth(it) }

        var pruned = 0
        for (folder in missing) {
            if (!resolveFolderDirectoryOnDisk(folder, root).exists()) {
                val pages = pageRepository.findByFolderId(folder.id!!)
                for (p in pages) {
                    p.folder = null
                }
                if (pages.isNotEmpty()) {
                    pageRepository.saveAll(pages)
                }
                folderRepository.delete(folder)
                pruned++
            }
        }
        return pruned
    }

    private fun resolveFolderDirectoryOnDisk(folder: Folder, contentRoot: File): File {
        // Единая точка построения пути папки — WikiFileService (параметр contentRoot
        // оставлен в сигнатуре: корень контента всегда один и тот же).
        return wikiFileService.resolveFolderDirectory(folder)
    }
}
