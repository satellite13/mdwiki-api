package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.model.Folder
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.service.usecase.WikiSyncEngine
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
    @Lazy private val folderService: FolderService,
    private val wikiSyncEngine: WikiSyncEngine,
    transactionManager: PlatformTransactionManager
) {
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

    data class SyncResult(val added: Int, val updated: Int, val removed: Int)

    @Transactional
    fun fullSync(): SyncResult = synchronized(wikiSyncLock) {
        val result = wikiSyncEngine.fullSync()
        if (result.added > 0 || result.updated > 0 || result.removed > 0) {
            folderService.invalidateCache()
            treeEventsService.publishTreeUpdated()
        }
        result
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
        val result = wikiSyncEngine.fullSync()
        val prunedFolders = pruneMissingFolders()
        if (result.added > 0 || result.updated > 0 || result.removed > 0 || prunedFolders > 0) {
            folderService.invalidateCache()
            treeEventsService.publishTreeUpdated()
        }
        return result
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
        val segments = mutableListOf<String>()
        var current: Folder? = folder
        while (current != null) {
            segments.add(sanitizePathSegment(current.name))
            current = current.parent
        }
        var dir = contentRoot
        for (segment in segments.reversed()) {
            dir = File(dir, segment)
        }
        return dir
    }

    private fun sanitizePathSegment(input: String): String {
        val cleaned = input
            .trim()
            .replace('/', '-')
            .replace('\\', '-')
        return if (cleaned.isBlank()) "folder" else cleaned
    }
}
