package com.mdwiki.service.usecase

import com.mdwiki.dto.CreatePageRequest
import com.mdwiki.error.ConflictException
import com.mdwiki.error.NotFoundException
import com.mdwiki.mapper.toResponse
import com.mdwiki.model.Page
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.UserRepository
import com.mdwiki.rag.RagService
import com.mdwiki.service.FrontmatterMetaService
import com.mdwiki.service.PageMetadataService
import com.mdwiki.service.SectionIndexService
import com.mdwiki.service.WikiFileService
import com.mdwiki.service.WikilinkService
import org.springframework.stereotype.Component

@Component
class CreatePageUseCase(
    private val pageRepository: PageRepository,
    private val userRepository: UserRepository,
    private val folderRepository: FolderRepository,
    private val pageMetadataService: PageMetadataService,
    private val ragService: RagService,
    private val wikiFileService: WikiFileService,
    private val frontmatterMetaService: FrontmatterMetaService,
    private val wikilinkService: WikilinkService,
    private val sectionIndexService: SectionIndexService
) {
    fun execute(request: CreatePageRequest, username: String) = run {
        // Явно заданный `slug` имеет приоритет (после нормализации); fallback — slug из title.
        // Раньше порядок был обратным и `request.slug` по факту игнорировался, если title не пустой.
        val normalizedRequestSlug = wikilinkService.normalizePageSlug(request.slug)
        val slug = normalizedRequestSlug.ifBlank { wikilinkService.normalizePageSlug(request.title) }

        if (pageRepository.existsBySlug(slug)) {
            throw ConflictException("Page with slug '$slug' already exists")
        }

        val user = userRepository.findByUsername(username)
            ?: throw NotFoundException("User not found: $username")
        val folder = request.folderId?.let { folderId ->
            folderRepository.findById(folderId).orElseThrow {
                NotFoundException("Folder not found: $folderId")
            }
        }

        val page = Page(
            slug = slug,
            title = request.title,
            contentMd = request.contentMd,
            createdBy = user,
            updatedBy = user,
            folder = folder
        )
        frontmatterMetaService.refreshFromContent(page, request.contentMd)
        wikiFileService.createOrRewritePageFile(page, request.contentMd)
        val saved = pageRepository.save(page)

        pageMetadataService.syncLinksAndTags(saved, request.contentMd, cleanupOrphanedTags = true)
        pageMetadataService.resolveIncomingLinks(saved)
        ragService.indexPage(saved)
        sectionIndexService.rebuild(saved, request.contentMd)

        saved.toResponse()
    }

}
