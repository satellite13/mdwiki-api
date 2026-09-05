package com.mdwiki.service

import com.mdwiki.dto.*
import com.mdwiki.error.BadRequestException
import com.mdwiki.error.ConflictException
import com.mdwiki.error.NotFoundException
import com.mdwiki.mapper.toResponse
import com.mdwiki.model.RevisionOperation
import com.mdwiki.model.SectionAnchor
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.SectionAnchorRepository
import com.mdwiki.service.usecase.UpdatePageUseCase
import com.mdwiki.util.MarkdownSectionParser
import com.mdwiki.util.PersistentInstant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class StableSectionLinkService(
    private val pages: PageRepository,
    private val anchors: SectionAnchorRepository,
    private val updatePage: UpdatePageUseCase,
    private val access: FolderAccessPolicy
) {
    @Transactional
    fun materialize(slug: String, request: StableLinkRequest, username: String): StableLinkResponse {
        val page = pages.findActiveBySlugForUpdate(slug) ?: throw NotFoundException("Page not found: $slug")
        page.folder?.let { access.requireAccess(it, username) } ?: access.actor(username)
        if (!PersistentInstant.same(page.updatedAt, request.expectedUpdatedAt)) {
            throw ConflictException("Page '$slug' has changed")
        }
        val section = MarkdownSectionParser.parse(page.contentMd ?: "")
            .find { it.stableKey == request.sectionKey }
            ?: throw NotFoundException("Section '${request.sectionKey}' not found")
        if (section.heading == null || section.headingLevel == 0) {
            throw BadRequestException("Preamble and frontmatter cannot have stable links")
        }
        val stableId = section.explicitId ?: newStableId()
        anchors.findByStableId(stableId)?.takeIf { it.page.id != page.id }?.let {
            throw ConflictException("Stable section id already exists")
        }
        if (section.explicitId != null) {
            persist(page, stableId, section.stableKey, section.headingPath)
            return response(stableId, section.stableKey, page.slug, page.updatedAt, page.toResponse())
        }

        val content = page.contentMd ?: ""
        val lineEnd = content.indexOf('\n', section.startOffset).let { if (it < 0) content.length else it }
        val insertAt = if (lineEnd > 0 && content[lineEnd - 1] == '\r') lineEnd - 1 else lineEnd
        val changed = content.substring(0, insertAt) + " {#$stableId}" + content.substring(insertAt)
        val saved = RevisionMutationContext.with(RevisionMutation(RevisionOperation.PATCH)) {
            updatePage.execute(
                slug,
                UpdatePageRequest(contentMd = changed, expectedUpdatedAt = page.updatedAt),
                username
            )
        }
        val current = pages.findBySlugAndDeletedAtIsNull(saved.slug) ?: throw NotFoundException("Page not found")
        val materialized = MarkdownSectionParser.parse(saved.contentMd ?: "").first { it.explicitId == stableId }
        persist(current, stableId, materialized.stableKey, materialized.headingPath)
        return response(stableId, materialized.stableKey, saved.slug, saved.updatedAt, saved)
    }

    @Transactional(readOnly = true)
    fun resolve(stableId: String): StableLinkResolution {
        val anchor = anchors.findByStableId(stableId)
            ?.takeIf { it.retiredAt == null && it.page.deletedAt == null }
            ?: throw NotFoundException("Stable section link not found")
        return StableLinkResolution(
            stableId, anchor.page.slug, anchor.lastSectionKey,
            "/page/${anchor.page.slug}?section=$stableId"
        )
    }

    private fun persist(page: com.mdwiki.model.Page, stableId: String, key: String, path: String) {
        val anchor = anchors.findByPageIdAndStableId(requireNotNull(page.id), stableId)
            ?: SectionAnchor(page = page, stableId = stableId, lastSectionKey = key, lastHeadingPath = path)
        anchor.lastSectionKey = key
        anchor.lastHeadingPath = path
        anchor.retiredAt = null
        anchor.updatedAt = PersistentInstant.now()
        anchors.save(anchor)
    }

    private fun newStableId(): String {
        var id: String
        do id = "sec_" + UUID.randomUUID().toString().replace("-", "") while (anchors.findByStableId(id) != null)
        return id
    }

    private fun response(id: String, key: String, slug: String, updatedAt: java.time.Instant, page: PageResponse) =
        StableLinkResponse(id, key, slug, updatedAt, "/page/$slug?section=$id", page)
}
