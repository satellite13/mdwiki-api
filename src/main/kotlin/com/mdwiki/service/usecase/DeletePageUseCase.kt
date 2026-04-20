package com.mdwiki.service.usecase

import com.mdwiki.error.NotFoundException
import com.mdwiki.repository.PageRepository
import com.mdwiki.rag.RagService
import com.mdwiki.service.PageMetadataService
import com.mdwiki.service.WikiFileService
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class DeletePageUseCase(
    private val pageRepository: PageRepository,
    private val pageMetadataService: PageMetadataService,
    private val ragService: RagService,
    private val wikiFileService: WikiFileService
) {
    enum class DeleteMode {
        SOFT,
        HARD
    }

    fun execute(slug: String, mode: DeleteMode = DeleteMode.SOFT) {
        val page = pageRepository.findBySlug(slug)
        if (page != null) {
            if (mode == DeleteMode.SOFT) {
                if (page.deletedAt == null) {
                    page.deletedAt = Instant.now()
                    pageRepository.save(page)
                }
                return
            }
            hardDelete(slug, page)
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

    private fun hardDelete(slug: String, page: com.mdwiki.model.Page) {
        pageMetadataService.deleteSourceLinks(page)
        // Отвязываем входящие ссылки, иначе FK fk_links_target ломает hard-delete.
        pageMetadataService.detachIncomingLinks(page)
        page.id?.let { ragService.deletePageChunks(it) }
        wikiFileService.deletePageFile(page)
        val orphanOnly = wikiFileService.findMarkdownFileForSlug(slug)
        if (orphanOnly != null) {
            wikiFileService.deleteOrphanMarkdownIfExists(orphanOnly)
        }
        pageRepository.delete(page)
        pageMetadataService.cleanupOrphanedTags()
    }
}
