package com.mdwiki.service

import com.mdwiki.error.NotFoundException
import com.mdwiki.model.Page
import com.mdwiki.model.PageRevision
import com.mdwiki.model.RevisionOperation
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.PageRevisionRepository
import com.mdwiki.repository.UserRepository
import com.mdwiki.repository.SectionAnchorRepository
import com.mdwiki.util.MarkdownSectionParser
import com.mdwiki.util.PersistentInstant
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

data class RevisionSummary(
    val revisionNo: Long,
    val contentHash: String,
    val title: String,
    val slug: String,
    val folderId: UUID?,
    val operation: RevisionOperation,
    val createdByName: String?,
    val createdAt: Instant,
    val restoredFromRevisionNo: Long?
)

data class RevisionSnapshot(
    val id: UUID,
    val revisionNo: Long,
    val contentMd: String,
    val contentHash: String,
    val title: String,
    val slug: String,
    val folderId: UUID?,
    val operation: RevisionOperation,
    val createdByName: String?,
    val createdAt: Instant,
    val restoredFromRevisionNo: Long?
)

@Service
class PageRevisionService(
    private val revisions: PageRevisionRepository,
    private val pages: PageRepository,
    private val users: UserRepository,
    private val anchors: SectionAnchorRepository? = null
) {
    @Transactional
    fun record(
        page: Page,
        username: String?,
        operation: RevisionOperation,
        restoredFrom: PageRevision? = null
    ): PageRevision {
        val pageId = requireNotNull(page.id)
        pages.findActiveByIdForUpdate(pageId)
            ?: pages.findById(pageId).orElseThrow { NotFoundException("Page not found: $pageId") }
        val actor = username?.let(users::findByUsername)
        val content = page.contentMd ?: ""
        val revision = revisions.saveAndFlush(
            PageRevision(
                pageId = pageId,
                revisionNo = revisions.maxRevisionNo(pageId) + 1,
                contentMd = content,
                contentHash = sha256(content),
                titleSnapshot = page.title,
                slugSnapshot = page.slug,
                folderIdSnapshot = page.folder?.id,
                operation = operation,
                createdByUserId = actor?.id,
                createdByName = username,
                restoredFrom = restoredFrom
            )
        )
        reconcileAnchors(page)
        return revision
    }

    @Transactional(readOnly = true)
    fun list(page: Page, limit: Int, before: Long?): List<RevisionSummary> =
        revisions.list(requireNotNull(page.id), before, PageRequest.of(0, limit.coerceIn(1, 100)))
            .map(::summary)

    @Transactional(readOnly = true)
    fun get(page: Page, revisionNo: Long): RevisionSnapshot =
        entity(page, revisionNo).let(::snapshot)

    @Transactional(readOnly = true)
    fun entity(page: Page, revisionNo: Long): PageRevision =
        revisions.findByPageIdAndRevisionNo(requireNotNull(page.id), revisionNo)
            ?: throw NotFoundException("Revision $revisionNo not found")

    private fun summary(r: PageRevision) = RevisionSummary(
        r.revisionNo, r.contentHash, r.titleSnapshot, r.slugSnapshot, r.folderIdSnapshot,
        r.operation, r.createdByName, r.createdAt, r.restoredFrom?.revisionNo
    )

    private fun snapshot(r: PageRevision) = RevisionSnapshot(
        requireNotNull(r.id), r.revisionNo, r.contentMd, r.contentHash, r.titleSnapshot,
        r.slugSnapshot, r.folderIdSnapshot, r.operation, r.createdByName, r.createdAt,
        r.restoredFrom?.revisionNo
    )

    private fun sha256(value: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))

    private fun reconcileAnchors(page: Page) {
        val repository = anchors ?: return
        val explicit = MarkdownSectionParser.parse(page.contentMd ?: "")
            .filter { it.explicitId != null }
            .associateBy { it.explicitId }
        repository.findAllByPageId(requireNotNull(page.id)).forEach { anchor ->
            val section = explicit[anchor.stableId]
            if (section == null) {
                if (anchor.retiredAt == null) anchor.retiredAt = PersistentInstant.now()
            } else {
                anchor.lastSectionKey = section.stableKey
                anchor.lastHeadingPath = section.headingPath
                anchor.retiredAt = null
            }
            anchor.updatedAt = PersistentInstant.now()
        }
    }
}
