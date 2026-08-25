package com.mdwiki.service.usecase

import com.mdwiki.dto.CompleteOpenTaskRequest
import com.mdwiki.error.ConflictException
import com.mdwiki.error.ForbiddenException
import com.mdwiki.error.NotFoundException
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.DeferredPageIndexer
import com.mdwiki.service.FrontmatterMetaService
import com.mdwiki.service.PageMetadataService
import com.mdwiki.service.SectionIndexService
import com.mdwiki.service.WikiFileService
import com.mdwiki.util.MarkdownTaskScanner
import com.mdwiki.util.PersistentInstant
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CompleteOpenTaskUseCase(
    private val pageRepository: PageRepository,
    private val userRepository: UserRepository,
    private val frontmatterMetaService: FrontmatterMetaService,
    private val wikiFileService: WikiFileService,
    private val pageMetadataService: PageMetadataService,
    private val pageIndexer: DeferredPageIndexer,
    private val sectionIndexService: SectionIndexService
) {
    @Transactional
    fun execute(request: CompleteOpenTaskRequest, username: String) {
        val page = pageRepository.findActiveByIdForUpdate(request.documentId)
            ?: throw NotFoundException("Page not found: ${request.documentId}")
        if (frontmatterMetaService.isLocked(page)) {
            throw ForbiddenException("Page '${page.slug}' is locked and cannot be edited")
        }
        if (!PersistentInstant.same(page.updatedAt, request.updatedAt)) {
            throw ConflictException("Page '${page.slug}' has changed; refresh open tasks")
        }

        val content = page.contentMd ?: ""
        if (sourceLineAt(content, request.sourceOffset) != request.sourceLine) {
            throw ConflictException("Open task no longer matches its snapshot")
        }
        val task = MarkdownTaskScanner.scan(content).firstOrNull {
            it.sourceOffset == request.sourceOffset && it.sourceLine == request.sourceLine
        } ?: throw ConflictException("Open task no longer matches its snapshot")

        val updatedContent = completeTask(content, task.sourceOffset, request.summary)
        val user = userRepository.findByUsername(username)
            ?: throw NotFoundException("User not found: $username")

        page.contentMd = updatedContent
        frontmatterMetaService.refreshFromContent(page, updatedContent)
        wikiFileService.createOrRewritePageFile(page, updatedContent)
        page.updatedBy = user
        page.updatedAt = PersistentInstant.now()

        val saved = pageRepository.save(page)
        pageMetadataService.syncLinksAndTags(saved, updatedContent, cleanupOrphanedTags = true)
        pageIndexer.indexAfterCommit(saved)
        sectionIndexService.rebuild(saved, updatedContent)
    }

    private fun sourceLineAt(content: String, sourceOffset: Int): String? {
        if (sourceOffset !in content.indices) return null
        val lineStart = content.lastIndexOf('\n', sourceOffset - 1) + 1
        val lineEnd = content.indexOf('\n', sourceOffset).let { if (it == -1) content.length else it }
        return content.substring(lineStart, lineEnd).removeSuffix("\r")
    }

    private fun completeTask(content: String, sourceOffset: Int, summary: String?): String {
        val completed = content.replaceRange(sourceOffset + 3, sourceOffset + 4, "x")
        val summaryLines = summary
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.lines()
            ?: return completed
        val lineEnd = completed.indexOf('\n', sourceOffset)
        val lineBreak = if (lineEnd > 0 && completed[lineEnd - 1] == '\r') "\r\n" else "\n"
        val quote = summaryLines.joinToString(lineBreak) { "> $it" }

        return if (lineEnd == -1) {
            "$completed$lineBreak$quote"
        } else {
            completed.substring(0, lineEnd + 1) + quote + lineBreak + completed.substring(lineEnd + 1)
        }
    }
}
