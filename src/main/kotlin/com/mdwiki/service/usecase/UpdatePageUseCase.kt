package com.mdwiki.service.usecase

import com.mdwiki.dto.UpdatePageRequest
import com.mdwiki.dto.PageSlugConstraints
import com.mdwiki.error.BadRequestException
import com.mdwiki.error.ConflictException
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
import com.mdwiki.service.SectionIndexService
import com.mdwiki.service.SyncService
import com.mdwiki.service.WikiFileService
import com.mdwiki.service.WikilinkService
import com.mdwiki.util.PersistentInstant
import org.springframework.stereotype.Component
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
    private val syncService: SyncService,
    private val sectionIndexService: SectionIndexService
) {
    fun execute(slug: String, request: UpdatePageRequest, username: String) = run {
        request.slug?.let { explicitSlug ->
            if (!Regex(PageSlugConstraints.PATTERN).matches(explicitSlug)) {
                throw BadRequestException(PageSlugConstraints.MESSAGE)
            }
        }
        val lockedRenamePages: List<Page>
        val page = if (request.slug != null) {
            val sourceId = pageRepository.findActiveIdBySlug(slug)
                ?: throw NotFoundException("Page not found: $slug")
            val lockIds = (pageRepository.findAllActiveIds() + sourceId)
                .distinct()
                .sortedBy(UUID::toString)
            lockedRenamePages = pageRepository.findAllActiveByIdInForUpdate(lockIds)
                .sortedBy { it.id.toString() }
            lockedRenamePages.find { it.id == sourceId }
                ?: throw NotFoundException("Page not found: $slug")
        } else {
            lockedRenamePages = emptyList()
            pageRepository.findActiveBySlugForUpdate(slug)
                ?: throw NotFoundException("Page not found: $slug")
        }
        val user = userRepository.findByUsername(username)
            ?: throw NotFoundException("User not found: $username")

        if (frontmatterMetaService.isLocked(page)) {
            // Разрешаем только снятие лока: новый contentMd без locked: true.
            // Иначе залоченную страницу нельзя разблокировать через тот же PUT.
            val unlocking = request.contentMd != null &&
                !frontmatterMetaService.isLockedContent(request.contentMd)
            if (!unlocking) {
                throw com.mdwiki.error.ForbiddenException("Page '$slug' is locked and cannot be edited")
            }
        }
        if (request.expectedUpdatedAt != null && !PersistentInstant.same(page.updatedAt, request.expectedUpdatedAt)) {
            throw ConflictException("Page '$slug' has changed; refresh and retry with current updatedAt")
        }

        val oldSlug = page.slug

        request.title?.let { page.title = it }

        val previousFolder = page.folder
        val previousFolderId = previousFolder?.id
        val previousFilePath = page.filePath
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
        val desiredSlug = request.slug ?: oldSlug
        val newSlug = if (desiredSlug == oldSlug) {
            oldSlug
        } else {
            val existing = pageRepository.findBySlug(desiredSlug)
            if (existing != null && existing.id != page.id) {
                throw ConflictException("Page slug '$desiredSlug' already exists")
            }
            desiredSlug
        }
        val slugChanged = newSlug != oldSlug
        val folderChanged = previousFolderId != page.folder?.id

        // Сохраняем нормализованный title ДО изменения title страницы
        val oldNormalizedTitle = wikilinkService.normalizePageSlug(page.title)

        if (slugChanged) {
            contentForSave = wikilinkService.rewriteWikilinksReferencingNormalizedSlug(
                contentForSave, oldSlug, newSlug, oldNormalizedTitle
            )
        }
        page.slug = newSlug

        val fileUpdateNeeded = request.contentMd != null || slugChanged || folderChanged
        if (fileUpdateNeeded) {
            page.contentMd = contentForSave
            frontmatterMetaService.refreshFromContent(page, contentForSave)
            wikiFileService.schedulePageFileUpdate(
                page = page,
                previousSlug = oldSlug,
                previousFolder = previousFolder,
                previousFilePath = previousFilePath,
                content = contentForSave
            )
        }

        if (slugChanged) {
            pageRepository.saveAndFlush(page)
            linkRepository.updateAllTargetSlugs(oldSlug, newSlug)
            for (other in lockedRenamePages) {
                if (other.id == page.id) continue
                val md = other.contentMd ?: ""
                val rewritten = wikilinkService.rewriteWikilinksReferencingNormalizedSlug(
                    md, oldSlug, newSlug, oldNormalizedTitle
                )
                if (rewritten != md) {
                    other.contentMd = rewritten
                    other.updatedAt = PersistentInstant.now()
                    frontmatterMetaService.refreshFromContent(other, rewritten)
                    wikiFileService.createOrRewritePageFile(other, rewritten)
                    pageRepository.save(other)
                    pageMetadataService.syncLinksAndTags(other, rewritten, cleanupOrphanedTags = false)
                    pageIndexer.indexAfterCommit(other)
                    sectionIndexService.rebuild(other, rewritten)
                }
            }
        }

        page.updatedBy = user
        page.updatedAt = PersistentInstant.now()

        val saved = pageRepository.save(page)

        if (request.contentMd != null || slugChanged) {
            pageMetadataService.syncLinksAndTags(saved, saved.contentMd ?: "", cleanupOrphanedTags = true)
            pageIndexer.indexAfterCommit(saved)
            sectionIndexService.rebuild(saved, saved.contentMd ?: "")
        }

        // Синхронизируем БД с ФС после операций переименования/перемещения
        if (slugChanged || folderChanged) {
            syncService.scheduleReconcileFromDisk()
        }

        saved.toResponse()
    }

}
