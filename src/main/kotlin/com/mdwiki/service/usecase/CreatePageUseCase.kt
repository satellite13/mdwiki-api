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
import com.mdwiki.service.WikiFileService
import org.springframework.stereotype.Component

@Component
class CreatePageUseCase(
    private val pageRepository: PageRepository,
    private val userRepository: UserRepository,
    private val folderRepository: FolderRepository,
    private val pageMetadataService: PageMetadataService,
    private val ragService: RagService,
    private val wikiFileService: WikiFileService,
    private val frontmatterMetaService: FrontmatterMetaService
) {
    fun execute(request: CreatePageRequest, username: String) = run {
        if (pageRepository.existsBySlug(request.slug)) {
            throw ConflictException("Page with slug '${request.slug}' already exists")
        }

        val user = userRepository.findByUsername(username)
            ?: throw NotFoundException("User not found: $username")
        val folder = request.folderId?.let { folderId ->
            folderRepository.findById(folderId).orElseThrow {
                NotFoundException("Folder not found: $folderId")
            }
        }

        val page = Page(
            slug = request.slug,
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

        saved.toResponse()
    }

}
