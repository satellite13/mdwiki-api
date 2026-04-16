package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.rag.RagService
import com.mdwiki.repository.PageRepository
import com.mdwiki.service.usecase.WikiSyncEngine
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File

@Service
class SyncService(
    private val pageRepository: PageRepository,
    private val pageMetadataService: PageMetadataService,
    private val wikiProperties: WikiProperties,
    private val ragService: RagService,
    private val treeEventsService: TreeEventsService
) {
    private val wikiSyncEngine = WikiSyncEngine(
        pageRepository = pageRepository,
        pageMetadataService = pageMetadataService,
        wikiProperties = wikiProperties,
        ragService = ragService
    )

    /** Serializes disk↔DB sync so the watcher and fullSync (or concurrent API calls) cannot interleave inserts. */
    private val wikiSyncLock = Any()

    data class SyncResult(val added: Int, val updated: Int, val removed: Int)

    @Transactional
    fun fullSync(): SyncResult = synchronized(wikiSyncLock) {
        val result = wikiSyncEngine.fullSync()
        if (result.added > 0 || result.updated > 0 || result.removed > 0) {
            treeEventsService.publishTreeUpdated()
        }
        result
    }

    @Transactional
    fun syncSingleFile(file: File) = synchronized(wikiSyncLock) {
        wikiSyncEngine.syncSingleFile(file)
        treeEventsService.publishTreeUpdated()
    }

    @Transactional
    fun removePage(slug: String) = synchronized(wikiSyncLock) {
        wikiSyncEngine.removePage(slug)
        treeEventsService.publishTreeUpdated()
    }
}
