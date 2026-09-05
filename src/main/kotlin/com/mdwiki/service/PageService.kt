package com.mdwiki.service

import com.mdwiki.dto.*
import com.mdwiki.error.NotFoundException
import com.mdwiki.mapper.displayTitle
import com.mdwiki.mapper.toListItem
import com.mdwiki.mapper.toResponse
import com.mdwiki.repository.PageRepository
import com.mdwiki.model.RevisionOperation
import com.mdwiki.util.MarkdownSectionParser
import com.mdwiki.service.usecase.CreatePageUseCase
import com.mdwiki.service.usecase.DeletePageUseCase
import com.mdwiki.service.usecase.ImportMdPagesUseCase
import com.mdwiki.service.usecase.PatchPageUseCase
import com.mdwiki.service.usecase.PatchSectionUseCase
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
    private val importMdPagesUseCase: ImportMdPagesUseCase,
    private val patchPageUseCase: PatchPageUseCase,
    private val patchSectionUseCase: PatchSectionUseCase,
    private val sectionIndexService: SectionIndexService,
    private val pageRevisionService: PageRevisionService? = null,
    private val revisionDiffService: RevisionDiffService? = null
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
    fun mapSections(slug: String): PageSectionMapResponse {
        findBySlug(slug)
        val page = pageRepository.findBySlugAndDeletedAtIsNull(slug)
            ?: throw NotFoundException("Page not found: $slug")
        val content = page.contentMd ?: ""
        val sections = sectionIndexService.listOrRebuild(page)
        val explicitIds = MarkdownSectionParser.parse(content).associate { it.stableKey to it.explicitId }
        return PageSectionMapResponse(
            slug = page.slug,
            updatedAt = page.updatedAt,
            sections = sections.map { section ->
                PageSectionMapItem(
                    key = section.stableKey,
                    heading = section.heading,
                    headingPath = section.headingPath,
                    level = section.headingLevel,
                    length = (section.endOffset - section.startOffset).coerceAtLeast(0),
                    hash = section.contentHash.ifBlank {
                        SectionIndexService.hashOf(content, section.startOffset, section.endOffset)
                    },
                    includesChildren = sections.any { other ->
                        other !== section &&
                            other.startOffset > section.startOffset &&
                            other.startOffset < section.endOffset
                    },
                    stableId = explicitIds[section.stableKey]
                )
            }
        )
    }

    @Transactional
    fun patchSection(slug: String, request: PatchSectionRequest, username: String): PatchSectionResponse {
        val patched = patchSectionUseCase.execute(slug, request, username)
        folderService.invalidateCache()
        treeEventsService.publishTreeUpdated()
        return patched
    }

    @Transactional
    fun patch(slug: String, request: PatchPageRequest, username: String): PatchPageResponse {
        val patched = patchPageUseCase.execute(slug, request, username)
        folderService.invalidateCache()
        treeEventsService.publishTreeUpdated()
        return patched
    }

    @Transactional(readOnly = true)
    fun listRevisions(slug: String, limit: Int, before: Long?) =
        requireNotNull(pageRevisionService).list(activePage(slug), limit, before)

    @Transactional(readOnly = true)
    fun getRevision(slug: String, revisionNo: Long) =
        requireNotNull(pageRevisionService).get(activePage(slug), revisionNo)

    @Transactional(readOnly = true)
    fun diffRevisions(slug: String, from: Long, to: Long): RevisionDiffResponse {
        val page = activePage(slug)
        val before = requireNotNull(pageRevisionService).get(page, from)
        val after = pageRevisionService.get(page, to)
        val diff = requireNotNull(revisionDiffService).diff(before.contentMd, after.contentMd)
        return RevisionDiffResponse(before, after, diff.rows, diff.truncated)
    }

    @Transactional
    fun restoreRevision(slug: String, request: RestoreRevisionRequest, username: String): PageResponse {
        val page = activePage(slug)
        val revision = requireNotNull(pageRevisionService).entity(page, request.revisionNo)
        return RevisionMutationContext.with(RevisionMutation(RevisionOperation.RESTORE, revision)) {
            updatePageUseCase.execute(slug, UpdatePageRequest(
                contentMd = revision.contentMd,
                title = revision.titleSnapshot.takeIf { request.restoreTitle },
                expectedUpdatedAt = request.expectedUpdatedAt
            ), username)
        }
    }

    private fun activePage(slug: String) =
        pageRepository.findBySlugAndDeletedAtIsNull(slug)
            ?: throw NotFoundException("Page not found: $slug")

    @Transactional
    fun delete(slug: String, mode: DeletePageUseCase.DeleteMode, username: String) {
        deletePageUseCase.execute(slug, mode, username)
        publishDelete()
    }

    @Transactional
    internal fun deletePreAuthorized(slug: String, mode: DeletePageUseCase.DeleteMode) {
        deletePageUseCase.executePreAuthorized(slug, mode)
        publishDelete()
    }

    private fun publishDelete() {
        folderService.invalidateCache()
        treeEventsService.publishTreeUpdated()
    }

    @Transactional
    fun restore(slug: String, username: String? = null): PageResponse {
        val page = pageRepository.findBySlugForUpdate(slug)
            ?: throw NotFoundException("Page not found: $slug")
        if (page.deletedAt == null) {
            throw IllegalStateException("Page is not deleted: $slug")
        }
        page.deletedAt = null
        // Возвращаем файл из корзины на место (если он там был).
        wikiFileService.restorePageFileFromTrash(page)
        val saved = pageRepository.save(page)
        pageRepository.flush()
        pageRevisionService?.record(saved, username, RevisionOperation.RESTORE_TRASH)
        sectionIndexService.rebuild(saved)
        folderService.invalidateCache()
        treeEventsService.publishTreeUpdated()
        return saved.toResponse()
    }

    @Transactional
    internal fun restorePreAuthorized(slug: String, username: String? = null): PageResponse =
        restore(slug, username)

    @Transactional(readOnly = true)
    fun findDeleted(): List<PageListItem> {
        return pageRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc().map { it.toListItem() }
    }
}
