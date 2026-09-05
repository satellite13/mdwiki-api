package com.mdwiki.service.usecase

import com.mdwiki.dto.PatchSectionMode
import com.mdwiki.dto.PatchSectionRequest
import com.mdwiki.dto.PatchSectionResponse
import com.mdwiki.dto.UpdatePageRequest
import com.mdwiki.error.ConflictException
import com.mdwiki.error.ForbiddenException
import com.mdwiki.error.NotFoundException
import com.mdwiki.repository.PageRepository
import com.mdwiki.model.RevisionOperation
import com.mdwiki.service.RevisionMutation
import com.mdwiki.service.RevisionMutationContext
import com.mdwiki.service.FrontmatterMetaService
import com.mdwiki.service.SectionIndexService
import com.mdwiki.util.MarkdownSectionParser
import com.mdwiki.util.PersistentInstant
import org.springframework.stereotype.Component

@Component
class PatchSectionUseCase(
    private val pageRepository: PageRepository,
    private val frontmatterMetaService: FrontmatterMetaService,
    private val updatePageUseCase: UpdatePageUseCase
) {
    fun execute(slug: String, request: PatchSectionRequest, username: String): PatchSectionResponse {
        if (request.content.length > PatchPageUseCase.MAX_NEW_TEXT_CHARS) {
            throw IllegalArgumentException("content exceeds ${PatchPageUseCase.MAX_NEW_TEXT_CHARS} characters")
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
        val parsed = MarkdownSectionParser.parse(content)
        val section = parsed.find { it.stableKey == request.sectionKey }
            ?: throw NotFoundException("Section '${request.sectionKey}' not found on page '$slug'")
        val currentHash = SectionIndexService.hashOf(content, section.startOffset, section.endOffset)
        if (request.expectedHash != null && request.expectedHash != currentHash) {
            throw ConflictException("Section '${request.sectionKey}' has changed; refresh wiki_map")
        }
        val from = if (request.mode == PatchSectionMode.BODY) section.bodyStartOffset else section.startOffset
        val suffix = content.substring(section.endOffset)
        val keysAfter = parsed.filter { it.startOffset >= section.endOffset }.map { it.stableKey }
        val incoming = protectFollowingHeading(request.content, suffix)
        val spliced = content.substring(0, from) + incoming + suffix
        val surviving = MarkdownSectionParser.parse(spliced).map { it.stableKey }.toSet()
        val swallowed = keysAfter.filter { it !in surviving }
        if (swallowed.isNotEmpty()) {
            throw IllegalArgumentException(
                "Patch would swallow following section(s): ${swallowed.joinToString()}. End content with a newline and close fences so the next heading stays on its own line."
            )
        }
        val saved = RevisionMutationContext.with(RevisionMutation(RevisionOperation.PATCH)) {
            updatePageUseCase.execute(
                slug,
                UpdatePageRequest(contentMd = spliced, expectedUpdatedAt = request.expectedUpdatedAt),
                username
            )
        }
        val newHash = MarkdownSectionParser.parse(saved.contentMd ?: "")
            .find { it.stableKey == request.sectionKey }
            ?.let { SectionIndexService.hashOf(saved.contentMd ?: "", it.startOffset, it.endOffset) }
            ?: SectionIndexService.hashOf(request.content, 0, request.content.length)
        return PatchSectionResponse(
            slug = saved.slug,
            title = saved.title,
            sectionKey = request.sectionKey,
            contentMd = saved.contentMd,
            replacements = 1,
            previousUpdatedAt = request.expectedUpdatedAt,
            updatedAt = saved.updatedAt,
            contentHash = newHash
        )
    }

    private fun protectFollowingHeading(incoming: String, suffix: String): String {
        if (suffix.isEmpty() || !suffix.startsWith('#') || incoming.endsWith('\n')) {
            return incoming
        }
        return incoming + "\n"
    }
}
