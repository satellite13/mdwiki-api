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
    fun execute(slug: String) {
        val active = pageRepository.findBySlugAndDeletedAtIsNull(slug)
        if (active != null) {
            active.deletedAt = Instant.now()
            pageRepository.save(active)
            return
        }

        val tombstone = pageRepository.findBySlug(slug)
        if (tombstone != null) {
            pageMetadataService.deleteSourceLinks(tombstone)
            // Отвязываем входящие ссылки, иначе FK fk_links_target ломает hard-delete.
            pageMetadataService.detachIncomingLinks(tombstone)
            tombstone.id?.let { ragService.deletePageChunks(it) }
            wikiFileService.deletePageFile(tombstone)
            wikiFileService.findMarkdownFileForSlug(slug)?.let { orphan ->
                wikiFileService.deleteOrphanMarkdownIfExists(orphan)
            }
            pageRepository.delete(tombstone)
            pageMetadataService.cleanupOrphanedTags()
            return
        }

        val orphanOnly = wikiFileService.findMarkdownFileForSlug(slug)
        if (orphanOnly != null && wikiFileService.deleteOrphanMarkdownIfExists(orphanOnly)) {
            return
        }

        throw NotFoundException("Page not found: $slug")
    }
}
