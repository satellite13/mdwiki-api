package com.mdwiki.service.usecase

import com.mdwiki.error.NotFoundException
import com.mdwiki.repository.PageRepository
import com.mdwiki.rag.RagService
import com.mdwiki.service.AttachmentService
import com.mdwiki.service.PageMetadataService
import com.mdwiki.service.SyncService
import com.mdwiki.service.WikiFileService
import com.mdwiki.service.FolderAccessPolicy
import com.mdwiki.service.PageRevisionService
import com.mdwiki.model.RevisionOperation
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class DeletePageUseCase(
    private val pageRepository: PageRepository,
    private val pageMetadataService: PageMetadataService,
    private val ragService: RagService,
    private val wikiFileService: WikiFileService,
    private val syncService: SyncService,
    private val frontmatterMetaService: com.mdwiki.service.FrontmatterMetaService,
    private val folderAccessPolicy: FolderAccessPolicy,
    private val attachmentService: AttachmentService,
    private val pageRevisionService: PageRevisionService? = null
) {
    enum class DeleteMode {
        SOFT,
        HARD
    }

    fun execute(
        slug: String,
        mode: DeleteMode,
        username: String
    ) = executeInternal(slug, mode, scheduleReconcile = true, ignoreLocked = false, username = username) { page ->
        page.folder?.let { folderAccessPolicy.requireAccess(it, username) }
    }

    internal fun executePreAuthorized(
        slug: String,
        mode: DeleteMode,
        scheduleReconcile: Boolean = true,
        ignoreLocked: Boolean = false
    ) = executeInternal(slug, mode, scheduleReconcile, ignoreLocked, null) { }

    private fun executeInternal(
        slug: String,
        mode: DeleteMode,
        scheduleReconcile: Boolean,
        ignoreLocked: Boolean,
        username: String?,
        authorize: (com.mdwiki.model.Page) -> Unit
    ) {
        val page = pageRepository.findBySlugForUpdate(slug)
        if (page != null) {
            if (!ignoreLocked && frontmatterMetaService.isLocked(page)) {
                throw com.mdwiki.error.ForbiddenException("Page '$slug' is locked and cannot be deleted")
            }
            authorize(page)
            if (mode == DeleteMode.SOFT) {
                if (page.deletedAt == null) {
                    page.deletedAt = Instant.now()
                    // Файл уезжает в корзину — sync/watcher его не видят и страницу не воскрешают.
                    wikiFileService.movePageFileToTrash(page)
                    val saved = pageRepository.save(page)
                    pageRepository.flush()
                    pageRevisionService?.record(saved, username, RevisionOperation.DELETE)
                }
                return
            }
            hardDelete(slug, page, scheduleReconcile = scheduleReconcile)
            return
        }

        if (mode == DeleteMode.HARD) {
            val orphanOnly = wikiFileService.findMarkdownFileForSlug(slug)
            if (orphanOnly != null && wikiFileService.deleteOrphanMarkdownIfExists(orphanOnly)) {
                return
            }
        }

        throw NotFoundException("Page not found: $slug")
    }

    private fun hardDelete(slug: String, page: com.mdwiki.model.Page, scheduleReconcile: Boolean) {
        pageMetadataService.deleteSourceLinks(page)
        // Отвязываем входящие ссылки, иначе FK fk_links_target ломает hard-delete.
        pageMetadataService.detachIncomingLinks(page)
        // Вложения ссылаются на page_id без ON DELETE CASCADE — удаляем до строки pages.
        page.id?.let { attachmentService.deleteAllForPage(it) }
        page.id?.let { ragService.deletePageChunks(it) }
        wikiFileService.deletePageFile(page)
        val orphanOnly = wikiFileService.findMarkdownFileForSlug(slug)
        if (orphanOnly != null) {
            wikiFileService.deleteOrphanMarkdownIfExists(orphanOnly)
        }
        pageRepository.delete(page)
        pageMetadataService.cleanupOrphanedTags()
        // Синхронизируем БД с ФС: удаляем пустые папки и отсутствующие на диске сущности
        if (scheduleReconcile) {
            syncService.scheduleReconcileFromDisk()
        }
    }
}
