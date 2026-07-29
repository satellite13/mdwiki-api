package com.mdwiki.service.usecase

import com.mdwiki.dto.UpdatePageRequest
import com.mdwiki.error.NotFoundException
import com.mdwiki.mapper.toResponse
import com.mdwiki.model.Page
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.LinkRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.DeferredPageIndexer
import com.mdwiki.service.FrontmatterMetaService
import com.mdwiki.service.PageMetadataService
import com.mdwiki.service.SyncService
import com.mdwiki.service.WikiFileService
import com.mdwiki.service.WikilinkService
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class UpdatePageUseCase(
    private val pageRepository: PageRepository,
    private val userRepository: UserRepository,
    private val folderRepository: FolderRepository,
    private val pageMetadataService: PageMetadataService,
    private val pageIndexer: DeferredPageIndexer,
    private val wikiFileService: WikiFileService,
    private val frontmatterMetaService: FrontmatterMetaService,
    private val wikilinkService: WikilinkService,
    private val linkRepository: LinkRepository,
    private val syncService: SyncService
) {
    fun execute(slug: String, request: UpdatePageRequest, username: String) = run {
        val page = pageRepository.findBySlugAndDeletedAtIsNull(slug)
            ?: throw NotFoundException("Page not found: $slug")
        val user = userRepository.findByUsername(username)
            ?: throw NotFoundException("User not found: $username")

        if (frontmatterMetaService.isLocked(page)) {
            throw com.mdwiki.error.ForbiddenException("Page '$slug' is locked and cannot be edited")
        }

        val oldSlug = page.slug

        request.title?.let { page.title = it }

        val previousFolderId = page.folder?.id
        if (request.clearFolder == true) {
            page.folder = null
        } else {
            request.folderId?.let { folderId ->
                page.folder = folderRepository.findById(folderId)
                    .orElseThrow { NotFoundException("Folder not found: $folderId") }
            }
        }

        val mergedContent = request.contentMd ?: page.contentMd
        var contentForSave = mergedContent ?: ""

        // Slug is immutable unless explicitly requested to change
        val desiredSlug = request.slug?.let { wikilinkService.normalizePageSlug(it) } ?: oldSlug
        val newSlug = if (desiredSlug == oldSlug) {
            oldSlug
        } else {
            allocateUniqueSlug(desiredSlug, page.id)
        }
        val slugChanged = newSlug != oldSlug

        // Сохраняем нормализованный title ДО изменения title страницы
        val oldNormalizedTitle = wikilinkService.normalizePageSlug(page.title)

        if (slugChanged) {
            contentForSave = wikilinkService.rewriteWikilinksReferencingNormalizedSlug(
                contentForSave, oldSlug, newSlug, oldNormalizedTitle
            )
            wikiFileService.renamePageFileToSlug(page, newSlug)
            page.contentMd = contentForSave
            frontmatterMetaService.refreshFromContent(page, contentForSave)
            pageRepository.saveAndFlush(page)
            linkRepository.updateAllTargetSlugs(oldSlug, newSlug)
            pageRepository.findAllByDeletedAtIsNull()
                .asSequence()
                .filter { it.id != page.id }
                .forEach { other ->
                    val md = other.contentMd ?: ""
                    val rewritten = wikilinkService.rewriteWikilinksReferencingNormalizedSlug(
                        md, oldSlug, newSlug, oldNormalizedTitle
                    )
                    if (rewritten != md) {
                        other.contentMd = rewritten
                        other.updatedAt = Instant.now()
                        frontmatterMetaService.refreshFromContent(other, rewritten)
                        wikiFileService.createOrRewritePageFile(other, rewritten)
                        pageRepository.save(other)
                        pageMetadataService.syncLinksAndTags(other, rewritten, cleanupOrphanedTags = false)
                        pageIndexer.indexAfterCommit(other)
                    }
                }
        }

        if (previousFolderId != page.folder?.id) {
            wikiFileService.relocatePageFile(page, page.folder)
        }

        if (request.contentMd != null || slugChanged) {
            page.contentMd = contentForSave
            frontmatterMetaService.refreshFromContent(page, contentForSave)
            wikiFileService.createOrRewritePageFile(page, contentForSave)
        }

        page.updatedBy = user
        page.updatedAt = Instant.now()

        val saved = pageRepository.save(page)

        if (request.contentMd != null || slugChanged) {
            pageMetadataService.syncLinksAndTags(saved, saved.contentMd ?: "", cleanupOrphanedTags = true)
            pageIndexer.indexAfterCommit(saved)
        }

        // Синхронизируем БД с ФС после операций переименования/перемещения
        if (slugChanged || previousFolderId != page.folder?.id) {
            syncService.scheduleReconcileFromDisk()
        }

        saved.toResponse()
    }

    private fun allocateUniqueSlug(base: String, pageId: UUID?): String {
        var candidate = base.ifBlank { "page" }
        var counter = 2
        while (true) {
            val existing = pageRepository.findBySlug(candidate)
            if (existing == null || (pageId != null && existing.id == pageId)) {
                return candidate
            }
            val root = base.ifBlank { "page" }
            candidate = "$root-$counter"
            counter++
        }
    }
}
