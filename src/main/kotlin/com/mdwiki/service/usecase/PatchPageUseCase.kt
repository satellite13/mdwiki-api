package com.mdwiki.service.usecase

import com.mdwiki.dto.PatchPageRequest
import com.mdwiki.dto.PatchPageResponse
import com.mdwiki.dto.UpdatePageRequest
import com.mdwiki.error.ConflictException
import com.mdwiki.error.ForbiddenException
import com.mdwiki.error.NotFoundException
import com.mdwiki.repository.PageRepository
import com.mdwiki.service.FrontmatterMetaService
import com.mdwiki.util.MarkdownContentOps
import com.mdwiki.util.MarkdownSectionParser
import com.mdwiki.util.PersistentInstant
import org.springframework.stereotype.Component

@Component
class PatchPageUseCase(
    private val pageRepository: PageRepository,
    private val frontmatterMetaService: FrontmatterMetaService,
    private val updatePageUseCase: UpdatePageUseCase
) {
    fun execute(slug: String, request: PatchPageRequest, username: String): PatchPageResponse {
        if (request.newText.length > MAX_NEW_TEXT_CHARS) {
            throw IllegalArgumentException("newText exceeds $MAX_NEW_TEXT_CHARS characters")
        }
        val page = pageRepository.findBySlugAndDeletedAtIsNull(slug)
            ?: throw NotFoundException("Page not found: $slug")
        if (frontmatterMetaService.isLocked(page)) {
            throw ForbiddenException("Page '$slug' is locked and cannot be edited")
        }
        if (!PersistentInstant.same(page.updatedAt, request.expectedUpdatedAt)) {
            throw ConflictException("Page '$slug' has changed; refresh and retry with current updatedAt")
        }
        val content = page.contentMd ?: ""
        val (searchIn, prefix, suffix) = scopedSlice(content, request.sectionKey, slug)
        val replaced = MarkdownContentOps.replaceExact(
            content = searchIn,
            oldText = request.oldText,
            newText = request.newText,
            replaceAll = request.replaceAll
        )
        val saved = updatePageUseCase.execute(
            slug,
            UpdatePageRequest(
                contentMd = prefix + replaced.content + suffix,
                expectedUpdatedAt = request.expectedUpdatedAt
            ),
            username
        )
        return PatchPageResponse(
            slug = saved.slug,
            title = saved.title,
            contentMd = saved.contentMd,
            replacements = replaced.replacements,
            previousUpdatedAt = request.expectedUpdatedAt,
            updatedAt = saved.updatedAt
        )
    }

    private fun scopedSlice(content: String, sectionKey: String?, slug: String): Triple<String, String, String> {
        if (sectionKey.isNullOrBlank()) {
            return Triple(content, "", "")
        }
        val section = MarkdownSectionParser.parse(content).find { it.stableKey == sectionKey }
            ?: throw NotFoundException("Section '$sectionKey' not found on page '$slug'")
        return Triple(
            content.substring(section.startOffset, section.endOffset),
            content.substring(0, section.startOffset),
            content.substring(section.endOffset)
        )
    }

    companion object {
        const val MAX_NEW_TEXT_CHARS = 500_000
    }
}
