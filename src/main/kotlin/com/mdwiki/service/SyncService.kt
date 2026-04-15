package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.model.Link
import com.mdwiki.model.Page
import com.mdwiki.repository.LinkRepository
import com.mdwiki.rag.RagService
import com.mdwiki.repository.PageRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.time.Instant

@Service
class SyncService(
    private val pageRepository: PageRepository,
    private val linkRepository: LinkRepository,
    private val wikilinkService: WikilinkService,
    private val tagService: TagService,
    private val wikiProperties: WikiProperties,
    private val ragService: RagService
) {

    private val log = LoggerFactory.getLogger(SyncService::class.java)

    data class SyncResult(val added: Int, val updated: Int, val removed: Int)

    @Transactional
    fun fullSync(): SyncResult {
        val contentDir = File(wikiProperties.contentDir)
        if (!contentDir.exists()) {
            contentDir.mkdirs()
            return SyncResult(0, 0, 0)
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
                processLinksAndTags(page, content)
                ragService.indexPage(page)
                added++
                log.info("Sync: added page '$slug'")
            } else if (existing.contentMd != content) {
                existing.contentMd = content
                existing.title = extractTitle(content, slug)
                existing.updatedAt = Instant.now()
                val saved = pageRepository.save(existing)
                processLinksAndTags(saved, content)
                ragService.indexPage(saved)
                updated++
                log.info("Sync: updated page '$slug'")
            }
        }

        for ((slug, page) in existingBySlug) {
            if (slug !in filesBySlug) {
                linkRepository.deleteBySourcePage(page)
                ragService.deletePageChunks(page.id!!)
                pageRepository.delete(page)
                removed++
                log.info("Sync: removed page '$slug'")
            }
        }

        if (removed > 0) {
            tagService.cleanupOrphanedTags()
        }

        return SyncResult(added, updated, removed)
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
                processLinksAndTags(saved, content)
                ragService.indexPage(saved)
                log.info("Watcher: updated page '$slug'")
            }
        } else {
            val title = extractTitle(content, slug)
            val page = pageRepository.save(
                Page(slug = slug, title = title, contentMd = content, filePath = file.absolutePath)
            )
            processLinksAndTags(page, content)
            ragService.indexPage(page)
            log.info("Watcher: added page '$slug'")
        }
    }

    fun removePage(slug: String) {
        val page = pageRepository.findBySlug(slug) ?: return
        linkRepository.deleteBySourcePage(page)
        ragService.deletePageChunks(page.id!!)
        pageRepository.delete(page)
        tagService.cleanupOrphanedTags()
        log.info("Watcher: removed page '$slug'")
    }

    private fun processLinksAndTags(page: Page, content: String) {
        linkRepository.deleteBySourcePage(page)
        val wikilinks = wikilinkService.extractWikilinks(content)
        for (wl in wikilinks) {
            val targetPage = pageRepository.findBySlug(wl.slug)
            linkRepository.save(Link(sourcePage = page, targetPage = targetPage, targetSlug = wl.slug))
        }

        val tagNames = wikilinkService.extractTags(content)
        val tags = tagService.getOrCreateTags(tagNames)
        page.tags.clear()
        page.tags.addAll(tags)
        pageRepository.save(page)
    }

    private fun extractTitle(content: String, fallbackSlug: String): String {
        val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() } ?: return fallbackSlug
        return if (firstLine.startsWith("# ")) firstLine.removePrefix("# ").trim() else fallbackSlug
    }
}
