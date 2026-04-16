package com.mdwiki.service.usecase

import com.mdwiki.error.NotFoundException
import com.mdwiki.repository.PageRepository
import com.mdwiki.rag.RagService
import com.mdwiki.service.PageMetadataService
import java.io.File

class DeletePageUseCase(
    private val pageRepository: PageRepository,
    private val pageMetadataService: PageMetadataService,
    private val ragService: RagService
) {
    fun execute(slug: String) {
        val page = pageRepository.findBySlug(slug)
            ?: throw NotFoundException("Page not found: $slug")
        page.filePath?.let { File(it).delete() }
        pageMetadataService.deleteSourceLinks(page)
        ragService.deletePageChunks(page.id!!)
        pageRepository.delete(page)
        pageMetadataService.cleanupOrphanedTags()
    }
}
