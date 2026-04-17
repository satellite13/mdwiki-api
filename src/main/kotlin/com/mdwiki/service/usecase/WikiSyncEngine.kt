package com.mdwiki.service.usecase

import com.mdwiki.config.WikiProperties
import com.mdwiki.model.Folder
import com.mdwiki.model.Page
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.rag.RagService
import com.mdwiki.service.FrontmatterMetaService
import com.mdwiki.service.PageMetadataService
import com.mdwiki.service.SyncService
import com.mdwiki.service.WikiFileService
import org.slf4j.LoggerFactory
import java.io.File
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class WikiSyncEngine(
    private val pageRepository: PageRepository,
    private val pageMetadataService: PageMetadataService,
    private val wikiProperties: WikiProperties,
    private val ragService: RagService,
    private val frontmatterMetaService: FrontmatterMetaService,
    private val folderRepository: FolderRepository,
    private val wikiFileService: WikiFileService
) {
    private val log = LoggerFactory.getLogger(WikiSyncEngine::class.java)
    private companion object {
        private const val FULL_SYNC_PAGE_BATCH_SIZE = 500
        private const val REPLACEMENT_CHAR = '\uFFFD'
    }

    fun fullSync(): SyncService.SyncResult {
        val startedAt = System.nanoTime()
        val contentDir = File(wikiProperties.contentDir)
        if (!contentDir.exists()) {
            contentDir.mkdirs()
            return SyncService.SyncResult(0, 0, 0)
        }

        val mdFiles = contentDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .toList()
        val existingBySlug = loadExistingBySlugForFullSync()
        val filesBySlug = linkedMapOf<String, File>()
        for (file in mdFiles) {
            val slug = file.nameWithoutExtension
            if (isSuspiciousFilesystemSlug(slug)) {
                log.warn("Sync: skipping suspicious slug '{}' from file '{}'", slug, file.absolutePath)
                continue
            }
            val existing = filesBySlug.putIfAbsent(slug, file)
            if (existing != null && existing.absolutePath != file.absolutePath) {
                log.warn(
                    "Sync: duplicate slug '{}' detected; keeping '{}' and ignoring '{}'",
                    slug,
                    existing.absolutePath,
                    file.absolutePath
                )
            }
        }

        var added = 0
        var updated = 0
        var removed = 0

        for ((slug, file) in filesBySlug) {
            val content = file.readText()
            val folder = resolveOrCreateFolderChain(contentDir, file)
            val existing = existingBySlug[slug]
            if (existing == null) {
                val title = extractTitle(content, slug)
                val page = Page(slug = slug, title = title, contentMd = content, filePath = file.absolutePath, folder = folder)
                frontmatterMetaService.refreshFromContent(page, content)
                val saved = pageRepository.save(page)
                pageMetadataService.syncLinksAndTags(saved, content)
                pageMetadataService.resolveIncomingLinks(saved)
                ragService.indexPage(saved)
                added++
                log.info("Sync: added page '$slug'")
            } else {
                val wasDeleted = existing.deletedAt != null
                if (wasDeleted) {
                    existing.deletedAt = null
                }
                if (wasDeleted || existing.contentMd != content) {
                    existing.contentMd = content
                    existing.title = extractTitle(content, slug)
                    existing.filePath = file.absolutePath
                    existing.folder = folder
                    existing.updatedAt = Instant.now()
                    frontmatterMetaService.refreshFromContent(existing, content)
                    val saved = pageRepository.save(existing)
                    pageMetadataService.syncLinksAndTags(saved, content)
                    if (wasDeleted) {
                        pageMetadataService.resolveIncomingLinks(saved)
                    }
                    ragService.indexPage(saved)
                    updated++
                    log.info(
                        if (wasDeleted) "Sync: restored soft-deleted page '$slug' from disk"
                        else "Sync: updated page '$slug'"
                    )
                } else if (existing.filePath != file.absolutePath || existing.folder?.id != folder?.id) {
                    existing.filePath = file.absolutePath
                    existing.folder = folder
                    pageRepository.save(existing)
                }
            }
        }

        for ((slug, page) in existingBySlug) {
            if (slug !in filesBySlug) {
                pageMetadataService.deleteSourceLinks(page)
                // Без detach входящих ссылок FK fk_links_target блокирует удаление.
                pageMetadataService.detachIncomingLinks(page)
                ragService.deletePageChunks(page.id!!)
                pageRepository.delete(page)
                removed++
                log.info("Sync: removed page '$slug'")
            }
        }

        if (removed > 0) {
            pageMetadataService.cleanupOrphanedTags()
        }

        val result = SyncService.SyncResult(added, updated, removed)
        log.info(
            "Sync completed in {} ms: added={}, updated={}, removed={}",
            elapsedMs(startedAt),
            result.added,
            result.updated,
            result.removed
        )
        return result
    }

    fun syncSingleFile(file: File) {
        val slug = file.nameWithoutExtension
        if (isSuspiciousFilesystemSlug(slug)) {
            log.warn("Watcher: skipping suspicious slug '{}' from file '{}'", slug, file.absolutePath)
            return
        }
        val content = file.readText()
        val contentDir = File(wikiProperties.contentDir)
        val folder = resolveOrCreateFolderChain(contentDir, file)
        val existing = pageRepository.findBySlug(slug)

        if (existing != null) {
            val wasDeleted = existing.deletedAt != null
            if (wasDeleted) {
                existing.deletedAt = null
            }
            if (wasDeleted || existing.contentMd != content) {
                existing.contentMd = content
                existing.title = extractTitle(content, slug)
                existing.filePath = file.absolutePath
                existing.folder = folder
                existing.updatedAt = Instant.now()
                frontmatterMetaService.refreshFromContent(existing, content)
                val saved = pageRepository.save(existing)
                pageMetadataService.syncLinksAndTags(saved, content)
                if (wasDeleted) {
                    pageMetadataService.resolveIncomingLinks(saved)
                }
                ragService.indexPage(saved)
                log.info(
                    if (wasDeleted) "Watcher: restored soft-deleted page '$slug' from disk"
                    else "Watcher: updated page '$slug'"
                )
            } else if (existing.filePath != file.absolutePath || existing.folder?.id != folder?.id) {
                existing.filePath = file.absolutePath
                existing.folder = folder
                pageRepository.save(existing)
            }
        } else {
            val title = extractTitle(content, slug)
            val page = Page(slug = slug, title = title, contentMd = content, filePath = file.absolutePath, folder = folder)
            frontmatterMetaService.refreshFromContent(page, content)
            val saved = pageRepository.save(page)
            pageMetadataService.syncLinksAndTags(saved, content)
            pageMetadataService.resolveIncomingLinks(saved)
            ragService.indexPage(saved)
            log.info("Watcher: added page '$slug'")
        }
    }

    fun removePage(slug: String) {
        if (isSuspiciousFilesystemSlug(slug)) {
            log.warn("Watcher: ignore delete for suspicious slug '{}'", slug)
            return
        }
        val page = pageRepository.findBySlug(slug) ?: return
        pageMetadataService.deleteSourceLinks(page)
        // Без detach входящих ссылок FK fk_links_target блокирует удаление.
        pageMetadataService.detachIncomingLinks(page)
        ragService.deletePageChunks(page.id!!)
        pageRepository.delete(page)
        runCatching { pageMetadataService.cleanupOrphanedTags() }
            .onFailure { e ->
                log.warn("Watcher: cleanupOrphanedTags failed for slug '{}': {}", slug, e.message)
            }
        log.info("Watcher: removed page '$slug'")
    }

    private fun extractTitle(content: String, fallbackSlug: String): String {
        val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() } ?: return fallbackSlug
        return if (firstLine.startsWith("# ")) firstLine.removePrefix("# ").trim() else fallbackSlug
    }

    /** Как при создании папок в UI: безопасное имя сегмента пути. */
    private fun sanitizeFolderSegment(segment: String): String {
        val cleaned = segment.trim().replace('/', '-').replace('\\', '-')
        return if (cleaned.isBlank()) "folder" else cleaned
    }

    /**
     * По пути `wiki-content/.../page.md` создаёт в БД цепочку папок и возвращает родителя страницы (лист цепочки).
     * Корень контента → null.
     */
    private fun resolveOrCreateFolderChain(contentRoot: File, mdFile: File): Folder? {
        val root = contentRoot.canonicalFile
        val parent = mdFile.parentFile?.canonicalFile ?: return null
        if (parent == root) return null
        if (!parent.toPath().startsWith(root.toPath())) return null
        val rel = root.toPath().relativize(parent.toPath())
        if (rel.nameCount == 0) return null

        var parentFolder: Folder? = null
        for (i in 0 until rel.nameCount) {
            val name = sanitizeFolderSegment(rel.getName(i).toString())
            val siblings = folderRepository.findByParentId(parentFolder?.id)
            val found = siblings.firstOrNull { it.name == name }
            val folder = found ?: run {
                val created = Folder(name = name, parent = parentFolder, createdBy = null)
                val saved = folderRepository.save(created)
                wikiFileService.ensureFolderDirectory(saved)
                saved
            }
            parentFolder = folder
        }
        return parentFolder
    }

    /**
     * Avoid one giant `findAll()` query: read pages in batches for large wikis.
     */
    private fun loadExistingBySlugForFullSync(): MutableMap<String, Page> {
        val bySlug = linkedMapOf<String, Page>()
        var pageIndex = 0
        while (true) {
            val batch = pageRepository.findAll(PageRequest.of(pageIndex, FULL_SYNC_PAGE_BATCH_SIZE))
            batch.content.forEach { page -> bySlug.putIfAbsent(page.slug, page) }
            if (!batch.hasNext()) break
            pageIndex++
        }
        return bySlug
    }

    private fun elapsedMs(startedAtNanos: Long): Long {
        return (System.nanoTime() - startedAtNanos) / 1_000_000
    }

    private fun isSuspiciousFilesystemSlug(slug: String): Boolean {
        return slug.contains('?') || slug.contains(REPLACEMENT_CHAR)
    }
}
