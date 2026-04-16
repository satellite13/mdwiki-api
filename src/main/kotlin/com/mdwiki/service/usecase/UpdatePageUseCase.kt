package com.mdwiki.service.usecase

import com.mdwiki.dto.UpdatePageRequest
import com.mdwiki.error.NotFoundException
import com.mdwiki.mapper.toResponse
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.UserRepository
import com.mdwiki.rag.RagService
import com.mdwiki.service.PageMetadataService
import java.io.File
import java.time.Instant

class UpdatePageUseCase(
    private val pageRepository: PageRepository,
    private val userRepository: UserRepository,
    private val folderRepository: FolderRepository,
    private val pageMetadataService: PageMetadataService,
    private val ragService: RagService
) {
    fun execute(slug: String, request: UpdatePageRequest, username: String) = run {
        val page = pageRepository.findBySlug(slug)
            ?: throw NotFoundException("Page not found: $slug")
        val user = userRepository.findByUsername(username)
            ?: throw NotFoundException("User not found: $username")

        request.title?.let { page.title = it }
        request.folderId?.let { folderId ->
            page.folder = folderRepository.findById(folderId)
                .orElseThrow { NotFoundException("Folder not found: $folderId") }
        }
        request.contentMd?.let { newContent ->
            page.contentMd = newContent
            page.filePath?.let { path -> File(path).writeText(newContent) }
        }
        page.updatedBy = user
        page.updatedAt = Instant.now()

        val saved = pageRepository.save(page)

        if (request.contentMd != null) {
            pageMetadataService.syncLinksAndTags(saved, request.contentMd, cleanupOrphanedTags = true)
            ragService.indexPage(saved)
        }

        saved.toResponse()
    }
}
