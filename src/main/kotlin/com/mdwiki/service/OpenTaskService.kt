package com.mdwiki.service

import com.mdwiki.dto.OpenTaskResponse
import com.mdwiki.mapper.displayTitle
import com.mdwiki.repository.PageRepository
import com.mdwiki.util.MarkdownTaskScanner
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OpenTaskService(
    private val pageRepository: PageRepository,
    private val frontmatterMetaService: FrontmatterMetaService
) {
    @Transactional(readOnly = true)
    fun listOpenTasks(requestingUsername: String): List<OpenTaskResponse> =
        // Shared-wiki reads intentionally remain global.
        pageRepository.findAllByDeletedAtIsNull().flatMap { page ->
            val pageId = page.id ?: return@flatMap emptyList()
            val locked = frontmatterMetaService.isLocked(page)
            MarkdownTaskScanner.scan(page.contentMd ?: "").map { task ->
                OpenTaskResponse(
                    documentId = pageId,
                    slug = page.slug,
                    documentTitle = page.displayTitle(),
                    text = task.taskText,
                    sourceOffset = task.sourceOffset,
                    sourceLine = task.sourceLine,
                    updatedAt = page.updatedAt,
                    locked = locked
                )
            }
        }
}
