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
        val page = pageRepository.findBySlugAndDeletedAtIsNull(slug)
            ?: throw NotFoundException("Page not found: $slug")
        page.deletedAt = Instant.now()
        pageRepository.save(page)
    }
}
