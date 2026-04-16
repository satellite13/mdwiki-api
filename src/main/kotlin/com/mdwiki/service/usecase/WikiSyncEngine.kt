package com.mdwiki.service.usecase

import com.mdwiki.config.WikiProperties
import com.mdwiki.model.Page
import com.mdwiki.repository.PageRepository
import com.mdwiki.rag.RagService
import com.mdwiki.service.PageMetadataService
import com.mdwiki.service.SyncService
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant

class WikiSyncEngine(
    private val pageRepository: PageRepository,
    private val pageMetadataService: PageMetadataService,
    private val wikiProperties: WikiProperties,
    private val ragService: RagService
) {
    private val log = LoggerFactory.getLogger(WikiSyncEngine::class.java)

    fun fullSync(): SyncService.SyncResult {
        val contentDir = File(wikiProperties.contentDir)
        if (!contentDir.exists()) {
            contentDir.mkdirs()
            return SyncService.SyncResult(0, 0, 0)
        }

        val mdFiles = contentDir.listFiles { f -> f.extension == "md" }?.toList() ?: emptyList()
        val existingPages = pageRepository.findAll()
        val existingBySlug = existingPages.associateBy { it.slug }
        val filesBySlug = mdFiles.associate { it.nameWithoutExtension to it }

        var added = 0
        var updated = 0
        var removed = 0

        for ((slug, file) in filesBySlug) {
            val content = file.readText()
            val existing = existingBySlug[slug]
            if (existing == null) {
                val title = extractTitle(content, slug)
                val page = pageRepository.save(
                    Page(slug = slug, title = title, contentMd = content, filePath = file.absolutePath)
                )
                pageMetadataService.syncLinksAndTags(page, content)
                pageMetadataService.resolveIncomingLinks(page)
                ragService.indexPage(page)
                added++
                log.info("Sync: added page '$slug'")
            } else if (existing.contentMd != content) {
                existing.contentMd = content
                existing.title = extractTitle(content, slug)
                existing.updatedAt = Instant.now()
                val saved = pageRepository.save(existing)
                pageMetadataService.syncLinksAndTags(saved, content)
                ragService.indexPage(saved)
                updated++
                log.info("Sync: updated page '$slug'")
            }
        }

        for ((slug, page) in existingBySlug) {
            if (slug !in filesBySlug) {
                pageMetadataService.deleteSourceLinks(page)
                ragService.deletePageChunks(page.id!!)
                pageRepository.delete(page)
                removed++
                log.info("Sync: removed page '$slug'")
            }
        }

        if (removed > 0) {
            pageMetadataService.cleanupOrphanedTags()
        }

        return SyncService.SyncResult(added, updated, removed)
    }

    fun syncSingleFile(file: File) {
        val slug = file.nameWithoutExtension
        val content = file.readText()
        val existing = pageRepository.findBySlug(slug)

        if (existing != null) {
            if (existing.contentMd != content) {
                existing.contentMd = content
                existing.title = extractTitle(content, slug)
                existing.updatedAt = Instant.now()
                val saved = pageRepository.save(existing)
                pageMetadataService.syncLinksAndTags(saved, content)
                ragService.indexPage(saved)
                log.info("Watcher: updated page '$slug'")
            }
        } else {
            val title = extractTitle(content, slug)
            val page = pageRepository.save(
                Page(slug = slug, title = title, contentMd = content, filePath = file.absolutePath)
            )
            pageMetadataService.syncLinksAndTags(page, content)
            pageMetadataService.resolveIncomingLinks(page)
            ragService.indexPage(page)
            log.info("Watcher: added page '$slug'")
        }
    }

    fun removePage(slug: String) {
        val page = pageRepository.findBySlug(slug) ?: return
        pageMetadataService.deleteSourceLinks(page)
        ragService.deletePageChunks(page.id!!)
        pageRepository.delete(page)
        pageMetadataService.cleanupOrphanedTags()
        log.info("Watcher: removed page '$slug'")
    }

    private fun extractTitle(content: String, fallbackSlug: String): String {
        val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() } ?: return fallbackSlug
        return if (firstLine.startsWith("# ")) firstLine.removePrefix("# ").trim() else fallbackSlug
    }
}
