package com.mdwiki.service

import com.mdwiki.dto.BrokenLinkKind
import com.mdwiki.dto.BrokenLinkResponse
import com.mdwiki.dto.RewriteBrokenLinksResponse
import com.mdwiki.error.NotFoundException
import com.mdwiki.mapper.displayTitle
import com.mdwiki.model.Page
import com.mdwiki.repository.LinkRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.SectionIndexService
import com.mdwiki.util.MarkdownFrontmatter
import com.mdwiki.util.PersistentInstant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BrokenLinkService(
    private val pageRepository: PageRepository,
    private val linkRepository: LinkRepository,
    private val wikilinkService: WikilinkService,
    private val pageMetadataService: PageMetadataService,
    private val frontmatterMetaService: FrontmatterMetaService,
    private val wikiFileService: WikiFileService,
    private val pageIndexer: DeferredPageIndexer,
    private val userRepository: UserRepository,
    private val sectionIndexService: SectionIndexService,
) {
    @Transactional(readOnly = true)
    fun listBroken(): List<BrokenLinkResponse> {
        val pages = pageRepository.findAllByDeletedAtIsNull()
        val result = mutableListOf<BrokenLinkResponse>()

        linkRepository.findAllDangling().forEach { link ->
            val source = link.sourcePage
            val body = MarkdownFrontmatter.strip(source.contentMd ?: "")
            val activeTargets = wikilinkService.extractWikilinks(body).map { it.slug }.toSet()
            if (link.targetSlug !in activeTargets) return@forEach
            if (wikilinkService.resolvesToPage(link.targetSlug, pages)) return@forEach
            result += BrokenLinkResponse(
                id = link.id,
                brokenTarget = link.targetSlug,
                kind = BrokenLinkKind.WIKILINK,
                sourceSlug = source.slug,
                sourceTitle = source.displayTitle(),
            )
        }

        for (page in pages) {
            val body = MarkdownFrontmatter.strip(page.contentMd ?: "")
            wikilinkService.extractInternalPageLinks(body).forEach { internal ->
                if (!wikilinkService.resolvesToPage(internal.slugRaw, pages)) {
                    result += BrokenLinkResponse(
                        id = null,
                        brokenTarget = internal.slugRaw,
                        kind = BrokenLinkKind.MARKDOWN,
                        sourceSlug = page.slug,
                        sourceTitle = page.displayTitle(),
                        displayText = internal.label.ifBlank { null },
                    )
                }
            }
        }

        return result.sortedWith(
            compareBy<BrokenLinkResponse> { it.brokenTarget.lowercase() }
                .thenBy { it.sourceTitle.lowercase() }
        )
    }

    @Transactional
    fun rewriteBrokenLinks(
        fromTarget: String,
        toSlug: String,
        sourceSlug: String?,
        username: String,
    ): RewriteBrokenLinksResponse {
        val targetPage = pageRepository.findBySlugAndDeletedAtIsNull(toSlug)
            ?: throw NotFoundException("Page not found: $toSlug")
        userRepository.findByUsername(username)
            ?: throw NotFoundException("User not found: $username")

        val normalizedFrom = wikilinkService.normalizePageSlug(fromTarget)
        if (normalizedFrom.isEmpty()) {
            throw NotFoundException("Invalid broken target: $fromTarget")
        }

        val pagesToUpdate = when (sourceSlug) {
            null -> selectPagesForBulkRewrite(normalizedFrom, fromTarget)
            else -> listOf(
                pageRepository.findBySlugAndDeletedAtIsNull(sourceSlug)
                    ?: throw NotFoundException("Page not found: $sourceSlug")
            )
        }

        var pagesUpdated = 0
        val skippedLocked = mutableListOf<String>()

        for (page in pagesToUpdate) {
            if (frontmatterMetaService.isLocked(page)) {
                skippedLocked += page.slug
                continue
            }
            if (rewritePageReferences(page, normalizedFrom, toSlug)) {
                pagesUpdated++
            }
        }

        if (pagesUpdated > 0) {
            linkRepository.updateAllTargetSlugs(normalizedFrom, toSlug)
            pageMetadataService.resolveIncomingLinks(targetPage)
        }

        return RewriteBrokenLinksResponse(
            pagesUpdated = pagesUpdated,
            skippedLocked = skippedLocked,
        )
    }

    private fun selectPagesForBulkRewrite(normalizedFrom: String, rawFrom: String): List<Page> {
        val fromDangling = linkRepository.findByTargetSlug(normalizedFrom)
            .map { it.sourcePage }
            .filter { it.deletedAt == null }
        val fromRaw = if (rawFrom != normalizedFrom) {
            linkRepository.findByTargetSlug(rawFrom).map { it.sourcePage }.filter { it.deletedAt == null }
        } else {
            emptyList()
        }

        val allPages = pageRepository.findAllByDeletedAtIsNull()
        val fromContent = allPages.filter { page ->
            val body = MarkdownFrontmatter.strip(page.contentMd ?: "")
            containsBrokenReference(body, normalizedFrom, rawFrom)
        }

        return (fromDangling + fromRaw + fromContent)
            .distinctBy { it.id }
    }

    private fun containsBrokenReference(body: String, normalizedFrom: String, rawFrom: String): Boolean {
        val wikilinks = wikilinkService.extractWikilinks(body)
        if (wikilinks.any { it.slug == normalizedFrom }) return true
        return wikilinkService.extractInternalPageLinks(body).any { link ->
            val normalized = wikilinkService.normalizePageSlug(link.slugRaw)
            normalized == normalizedFrom || link.slugRaw.trim() == rawFrom.trim()
        }
    }

    private fun rewritePageReferences(page: Page, normalizedFrom: String, toSlug: String): Boolean {
        val md = page.contentMd ?: ""
        val oldNormalizedTitle = wikilinkService.normalizePageSlug(page.title)
        var rewritten = wikilinkService.rewriteWikilinksReferencingNormalizedSlug(
            md,
            normalizedFrom,
            toSlug,
            oldNormalizedTitle = null,
        )
        rewritten = wikilinkService.rewriteInternalPageLinks(
            rewritten,
            normalizedFrom,
            toSlug,
            oldNormalizedTitle = null,
        )
        if (rewritten == md) return false

        page.contentMd = rewritten
        page.updatedAt = PersistentInstant.now()
        frontmatterMetaService.refreshFromContent(page, rewritten)
        wikiFileService.createOrRewritePageFile(page, rewritten)
        val saved = pageRepository.save(page)
        pageMetadataService.syncLinksAndTags(saved, rewritten, cleanupOrphanedTags = false)
        pageIndexer.indexAfterCommit(saved)
        sectionIndexService.rebuild(saved, rewritten)
        return true
    }
}
