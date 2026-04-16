package com.mdwiki.service.usecase

import com.mdwiki.config.WikiProperties
import com.mdwiki.model.Page
import com.mdwiki.repository.PageRepository
import com.mdwiki.rag.RagService
import com.mdwiki.service.FrontmatterMetaService
import com.mdwiki.service.PageMetadataService
import com.mdwiki.service.SyncService
import org.slf4j.LoggerFactory
import java.io.File
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class WikiSyncEngine(
    private val pageRepository: PageRepository,
    private val pageMetadataService: PageMetadataService,
    private val wikiProperties: WikiProperties,
    private val ragService: RagService,
    private val frontmatterMetaService: FrontmatterMetaService
) {
    private val log = LoggerFactory.getLogger(WikiSyncEngine::class.java)

    fun fullSync(): SyncService.SyncResult {
        val contentDir = File(wikiProperties.contentDir)
        if (!contentDir.exists()) {
            contentDir.mkdirs()
            return SyncService.SyncResult(0, 0, 0)
        }

        val mdFiles = contentDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .toList()
        val existingPages = pageRepository.findAll()
        val existingBySlug = existingPages.associateBy { it.slug }
        val filesBySlug = linkedMapOf<String, File>()
        for (file in mdFiles) {
            val slug = file.nameWithoutExtension
            filesBySlug.putIfAbsent(slug, file)
        }

        var added = 0
        var updated = 0
        var removed = 0

        for ((slug, file) in filesBySlug) {
            val content = file.readText()
            val existing = existingBySlug[slug]
            if (existing == null) {
                val title = extractTitle(content, slug)
                val page = Page(slug = slug, title = title, contentMd = content, filePath = file.absolutePath)
                frontmatterMetaService.refreshFromContent(page, content)
                val saved = pageRepository.save(page)
                pageMetadataService.syncLinksAndTags(saved, content)
                pageMetadataService.resolveIncomingLinks(saved)
                ragService.indexPage(saved)
                added++
                log.info("Sync: added page '$slug'")
            } else if (existing.contentMd != content) {
                existing.contentMd = content
                existing.title = extractTitle(content, slug)
                existing.filePath = file.absolutePath
                existing.updatedAt = Instant.now()
                frontmatterMetaService.refreshFromContent(existing, content)
                val saved = pageRepository.save(existing)
                pageMetadataService.syncLinksAndTags(saved, content)
                ragService.indexPage(saved)
                updated++
                log.info("Sync: updated page '$slug'")
            } else if (existing.filePath != file.absolutePath) {
                existing.filePath = file.absolutePath
                pageRepository.save(existing)
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
                existing.filePath = file.absolutePath
                existing.updatedAt = Instant.now()
                frontmatterMetaService.refreshFromContent(existing, content)
                val saved = pageRepository.save(existing)
                pageMetadataService.syncLinksAndTags(saved, content)
                ragService.indexPage(saved)
                log.info("Watcher: updated page '$slug'")
            } else if (existing.filePath != file.absolutePath) {
                existing.filePath = file.absolutePath
                pageRepository.save(existing)
            }
        } else {
            val title = extractTitle(content, slug)
            val page = Page(slug = slug, title = title, contentMd = content, filePath = file.absolutePath)
            frontmatterMetaService.refreshFromContent(page, content)
            val saved = pageRepository.save(page)
            pageMetadataService.syncLinksAndTags(saved, content)
            pageMetadataService.resolveIncomingLinks(saved)
            ragService.indexPage(saved)
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
