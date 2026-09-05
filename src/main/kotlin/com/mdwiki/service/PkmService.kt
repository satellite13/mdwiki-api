package com.mdwiki.service

import com.mdwiki.dto.*
import com.mdwiki.error.ConflictException
import com.mdwiki.error.NotFoundException
import com.mdwiki.error.BadRequestException
import com.mdwiki.mapper.displayTitle
import com.mdwiki.mapper.toListItem
import com.mdwiki.mapper.toResponse
import com.mdwiki.model.*
import com.mdwiki.repository.*
import com.mdwiki.service.usecase.DeletePageUseCase
import com.mdwiki.util.UnlinkedMentionScanner
import com.mdwiki.util.RasterImageValidator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.net.URI
import java.time.LocalDate
import java.util.UUID

@Service
class PkmService(
    private val users: UserRepository,
    private val pages: PageRepository,
    private val settings: UserPkmSettingsRepository,
    private val dailyNotes: UserDailyNoteRepository,
    private val recent: UserRecentPageRepository,
    private val favorites: UserFavoritePageRepository,
    private val links: LinkRepository,
    private val sections: PageSectionRepository,
    private val folderService: FolderService,
    private val pageService: PageService,
    private val attachmentService: AttachmentService
) {
    private fun user(username: String) = users.findByUsername(username)
        ?: throw NotFoundException("User not found: $username")

    private fun slug(prefix: String) =
        "$prefix-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"

    private fun folder(username: String, inbox: Boolean): Folder {
        MultiPageMutationLock.acquire(pages)
        val owner = user(username)
        var prefs = settings.findById(owner.id!!).orElse(null)
            ?: settings.save(UserPkmSettings(owner.id!!, owner))
        val existing = if (inbox) prefs.inboxFolder else prefs.dailyFolder
        if (existing != null) return existing
        val name = if (inbox) "Inbox" else "Daily Notes"
        val entity = folderService.getOrCreateOwnedPkmFolder(name, username)
        if (inbox) prefs.inboxFolder = entity else prefs.dailyFolder = entity
        prefs.updatedAt = java.time.Instant.now()
        settings.save(prefs)
        return entity
    }

    @Transactional
    fun captureText(request: TextCaptureRequest, username: String): CaptureResponse {
        val title = request.title?.trim().takeUnless { it.isNullOrBlank() }
            ?: request.text.lineSequence().firstOrNull()?.trim()?.take(80).takeUnless { it.isNullOrBlank() }
            ?: "Capture"
        val page = pageService.create(
            CreatePageRequest(slug("capture"), title, request.text, folder(username, true).id),
            username
        )
        return CaptureResponse("text", page)
    }

    @Transactional
    fun captureUrl(request: UrlCaptureRequest, username: String): CaptureResponse {
        val uri = runCatching { URI(request.url) }.getOrNull()
        if (uri == null || !uri.isAbsolute || uri.scheme.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            throw IllegalArgumentException("URL must be an absolute http/https URL")
        }
        val title = request.title?.trim().takeUnless { it.isNullOrBlank() } ?: uri.host
        val content = buildString {
            append("[Source](").append(request.url).append(")")
            request.note?.trim()?.takeIf { it.isNotBlank() }?.let { append("\n\n").append(it) }
        }
        return CaptureResponse(
            "url",
            pageService.create(
                CreatePageRequest(slug("capture"), title, content, folder(username, true).id),
                username
            )
        )
    }

    @Transactional
    fun captureImage(file: MultipartFile, caption: String?, title: String?, username: String): CaptureResponse {
        require((caption?.length ?: 0) <= 2000) { "Caption exceeds 2000 characters" }
        require((title?.length ?: 0) <= 500) { "Title exceeds 500 characters" }
        RasterImageValidator.validate(file)
        val page = pageService.create(
            CreatePageRequest(
                slug("capture"),
                title?.trim().takeUnless { it.isNullOrBlank() } ?: file.originalFilename ?: "Image",
                "",
                folder(username, true).id
            ),
            username
        )
        var attachment: AttachmentResponse? = null
        try {
            attachment = attachmentService.upload(file, username, page.id)
            val alt = caption?.trim().takeUnless { it.isNullOrBlank() } ?: attachment.originalName
            val updated = pageService.update(
                page.slug,
                UpdatePageRequest(contentMd = "![$alt](${attachment.url})", expectedUpdatedAt = page.updatedAt),
                username
            )
            return CaptureResponse("image", updated, attachment)
        } catch (error: Exception) {
            attachment?.let { runCatching { attachmentService.delete(it.id) } }
            runCatching { pageService.delete(page.slug, DeletePageUseCase.DeleteMode.HARD) }
            throw error
        }
    }

    @Transactional(readOnly = true)
    fun getDaily(date: LocalDate, username: String): DailyNoteResponse {
        val note = dailyNotes.findByUserIdAndNoteDate(user(username).id!!, date)
            ?: throw NotFoundException("Daily note not found: $date")
        return DailyNoteResponse(date.toString(), note.page.toResponse(), false)
    }

    @Transactional
    fun putDaily(date: LocalDate, username: String): DailyNoteResponse {
        MultiPageMutationLock.acquire(pages)
        val owner = user(username)
        dailyNotes.findByUserIdAndNoteDate(owner.id!!, date)?.let {
            return DailyNoteResponse(date.toString(), it.page.toResponse(), false)
        }
        val normalizedUser = username.lowercase().replace(Regex("[^a-z0-9а-яё]+"), "-").trim('-')
        val page = pageService.create(
            CreatePageRequest("daily-$normalizedUser-$date", date.toString(), "# $date\n\n", folder(username, false).id),
            username
        )
        val entity = pages.findById(page.id).orElseThrow()
        dailyNotes.save(UserDailyNote(owner.id!!, date, entity))
        return DailyNoteResponse(date.toString(), page, true)
    }

    @Transactional
    fun touchRecent(pageId: UUID, username: String) {
        val page = pages.findById(pageId).orElseThrow { NotFoundException("Page not found: $pageId") }
        if (page.deletedAt != null) throw NotFoundException("Page not found: $pageId")
        recent.touch(user(username).id!!, pageId)
    }

    @Transactional(readOnly = true)
    fun listRecent(limit: Int, username: String) =
        recent.listActive(user(username).id!!).take(limit.coerceIn(1, 100))
            .map { RecentPageResponse(it.page.toListItem(), it.lastOpenedAt, it.openCount) }

    @Transactional
    fun addFavorite(pageId: UUID, username: String) {
        val page = pages.findById(pageId).orElseThrow { NotFoundException("Page not found: $pageId") }
        if (page.deletedAt != null) throw NotFoundException("Page not found: $pageId")
        favorites.add(user(username).id!!, pageId)
    }

    @Transactional
    fun removeFavorite(pageId: UUID, username: String) =
        favorites.deleteByUserIdAndPageId(user(username).id!!, pageId)

    @Transactional(readOnly = true)
    fun isFavorite(pageId: UUID, username: String) =
        favorites.existsByUserIdAndPageId(user(username).id!!, pageId)

    @Transactional(readOnly = true)
    fun listFavorites(username: String) =
        favorites.listActive(user(username).id!!)
            .map { FavoritePageResponse(it.page.toListItem(), it.createdAt) }

    @Transactional(readOnly = true)
    fun mentions(targetSlug: String): List<UnlinkedMentionResponse> {
        val target = pages.findBySlugAndDeletedAtIsNull(targetSlug)
            ?: throw NotFoundException("Page not found: $targetSlug")
        return pages.findAllByDeletedAtIsNull()
            .asSequence()
            .filter { it.id != target.id }
            .flatMap { source ->
                val content = source.contentMd.orEmpty()
                UnlinkedMentionScanner.scan(content, target.displayTitle()).asSequence().map { match ->
                    val sectionKey = sections.findByPageIdOrderBySortOrder(source.id!!)
                        .lastOrNull { it.startOffset <= match.startOffset }?.stableKey
                    val from = (match.startOffset - 60).coerceAtLeast(0)
                    val to = (match.endOffset + 60).coerceAtMost(content.length)
                    UnlinkedMentionResponse(
                        source.slug, source.displayTitle(), content.substring(from, to).replace('\n', ' '),
                        sectionKey, match.startOffset, match.endOffset, source.updatedAt
                    )
                }
            }
            .sortedWith(compareBy({ it.sourceTitle.lowercase() }, { it.sourceSlug }, { it.startOffset }))
            .take(100)
            .toList()
    }

    @Transactional
    fun linkMention(targetSlug: String, request: LinkUnlinkedMentionRequest, username: String): PageResponse {
        MultiPageMutationLock.acquire(pages)
        val target = pages.findBySlugAndDeletedAtIsNull(targetSlug)
            ?: throw NotFoundException("Page not found: $targetSlug")
        val source = pages.findActiveBySlugForUpdate(request.sourceSlug)
            ?: throw NotFoundException("Page not found: ${request.sourceSlug}")
        val content = source.contentMd.orEmpty()
        if (request.startOffset < 0 || request.endOffset <= request.startOffset || request.endOffset > content.length) {
            throw BadRequestException("Mention offsets are outside source content")
        }
        if (source.updatedAt != request.expectedUpdatedAt) throw ConflictException("Source page changed")
        val match = UnlinkedMentionScanner.scan(content, target.displayTitle())
            .firstOrNull { it.startOffset == request.startOffset && it.endOffset == request.endOffset }
            ?: throw ConflictException("Mention no longer exists at supplied offsets")
        val matched = content.substring(match.startOffset, match.endOffset)
        val updated = content.replaceRange(match.startOffset, match.endOffset, "[[$targetSlug|$matched]]")
        return pageService.update(
            source.slug,
            UpdatePageRequest(contentMd = updated, expectedUpdatedAt = source.updatedAt),
            username
        )
    }

    @Transactional(readOnly = true)
    fun orphans(definition: OrphanDefinition): List<OrphanPageResponse> {
        val active = pages.findAllByDeletedAtIsNull()
        val allLinks = links.findAllWithPages().filter { it.sourcePage.deletedAt == null }
        return active.mapNotNull { page ->
            val incoming = allLinks.count { it.targetPage?.deletedAt == null && it.targetPage?.id == page.id }.toLong()
            // Unresolved links are intentional outgoing links; links resolved to deleted pages are ignored.
            val outgoing = allLinks.count {
                it.sourcePage.id == page.id && (it.targetPage == null || it.targetPage?.deletedAt == null)
            }.toLong()
            val include = when (definition) {
                OrphanDefinition.NO_INCOMING -> incoming == 0L
                OrphanDefinition.NO_OUTGOING -> outgoing == 0L
                OrphanDefinition.NO_LINKS -> incoming == 0L && outgoing == 0L
            }
            if (include) OrphanPageResponse(page.toListItem(), incoming, outgoing) else null
        }.sortedWith(compareBy({ it.page.title.lowercase() }, { it.page.slug }))
    }
}
