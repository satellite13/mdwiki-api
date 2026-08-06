package com.mdwiki.service

import com.mdwiki.dto.*
import com.mdwiki.error.NotFoundException
import com.mdwiki.mapper.displayTitle
import com.mdwiki.mapper.toListItem
import com.mdwiki.mapper.toResponse
import com.mdwiki.repository.PageRepository
import com.mdwiki.service.usecase.CreatePageUseCase
import com.mdwiki.service.usecase.DeletePageUseCase
import com.mdwiki.service.usecase.ImportMdPagesUseCase
import com.mdwiki.service.usecase.UpdatePageUseCase
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PageService(
    private val pageRepository: PageRepository,
    private val pageMetadataService: PageMetadataService,
    private val treeEventsService: TreeEventsService,
    private val folderService: FolderService,
    private val wikiFileService: WikiFileService,
    private val syncService: SyncService,
    private val createPageUseCase: CreatePageUseCase,
    private val updatePageUseCase: UpdatePageUseCase,
    private val deletePageUseCase: DeletePageUseCase,
    private val importMdPagesUseCase: ImportMdPagesUseCase
) {
    @Transactional(readOnly = true)
    fun findAll(page: Int = 0, size: Int = 50): Page<PageListItem> {
        val pageable = PageRequest.of(page, size)
        return pageRepository.findAllByDeletedAtIsNull(pageable).map { it.toListItem() }
    }

    /**
     * Подтягивает страницу с диска в БД при первом GET, если есть `$slug.md` под [WikiProperties.contentDir],
     * а активной строки в БД ещё нет (например файл положили вручную или БД откатили).
     */
    @Transactional
    fun findBySlug(slug: String): PageResponse {
        pageRepository.findBySlugAndDeletedAtIsNull(slug)?.let {
            return it.toResponse()
        }
        pageRepository.findFirstByNormalizedTitle(slug)?.takeIf { it.deletedAt == null }?.let {
            return it.toResponse()
        }
        val onDisk = wikiFileService.findMarkdownFileForSlug(slug)
        if (onDisk != null) {
            syncService.syncSingleFile(onDisk)
            pageRepository.findBySlugAndDeletedAtIsNull(slug)?.let {
                return it.toResponse()
            }
        }
        throw NotFoundException("Page not found: $slug")
    }

    @Transactional(readOnly = true)
    fun getBacklinks(slug: String): List<BacklinkResponse> {
        return pageMetadataService.findBacklinks(slug).map {
            BacklinkResponse(slug = it.sourcePage.slug, title = it.sourcePage.displayTitle())
        }
    }

    @Transactional
    fun create(request: CreatePageRequest, username: String): PageResponse {
        val created = createPageUseCase.execute(request, username)
        folderService.invalidateCache()
        treeEventsService.publishTreeUpdated()
        return created
    }

    @Transactional
    fun importMd(
        files: List<ImportMdFileInput>,
        folderId: UUID?,
        overwrite: Boolean,
        username: String
    ): ImportMdPagesResponse {
        val result = importMdPagesUseCase.execute(files, folderId, overwrite, username)
        if (result.created > 0 || result.updated > 0) {
            folderService.invalidateCache()
            treeEventsService.publishTreeUpdated()
        }
        return result
    }

    @Transactional
    fun update(slug: String, request: UpdatePageRequest, username: String): PageResponse {
        val updated = updatePageUseCase.execute(slug, request, username)
        folderService.invalidateCache()
        treeEventsService.publishTreeUpdated()
        return updated
    }

    @Transactional
    fun delete(slug: String, mode: DeletePageUseCase.DeleteMode = DeletePageUseCase.DeleteMode.SOFT) {
        deletePageUseCase.execute(slug, mode)
        folderService.invalidateCache()
        treeEventsService.publishTreeUpdated()
    }

    @Transactional
    fun restore(slug: String): PageResponse {
        val page = pageRepository.findBySlug(slug)
            ?: throw NotFoundException("Page not found: $slug")
        if (page.deletedAt == null) {
            throw IllegalStateException("Page is not deleted: $slug")
        }
        page.deletedAt = null
        // Возвращаем файл из корзины на место (если он там был).
        wikiFileService.restorePageFileFromTrash(page)
        val saved = pageRepository.save(page)
        folderService.invalidateCache()
        treeEventsService.publishTreeUpdated()
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun findDeleted(): List<PageListItem> {
        return pageRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc().map { it.toListItem() }
    }
}
