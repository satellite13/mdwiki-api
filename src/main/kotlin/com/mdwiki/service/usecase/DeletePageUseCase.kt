package com.mdwiki.service.usecase

import com.mdwiki.error.NotFoundException
import com.mdwiki.repository.PageRepository
import com.mdwiki.rag.RagService
import com.mdwiki.service.PageMetadataService
import com.mdwiki.service.WikiFileService

class DeletePageUseCase(
    private val pageRepository: PageRepository,
    private val pageMetadataService: PageMetadataService,
    private val ragService: RagService,
    private val wikiFileService: WikiFileService
) {
    fun execute(slug: String) {
        val page = pageRepository.findBySlug(slug)
            ?: throw NotFoundException("Page not found: $slug")
        wikiFileService.deletePageFile(page)
        pageMetadataService.deleteSourceLinks(page)
        ragService.deletePageChunks(page.id!!)
        pageRepository.delete(page)
        pageMetadataService.cleanupOrphanedTags()
    }
}
