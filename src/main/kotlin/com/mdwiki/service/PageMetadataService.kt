package com.mdwiki.service

import com.mdwiki.util.MarkdownFrontmatter
import com.mdwiki.model.Link
import com.mdwiki.model.Page
import com.mdwiki.repository.LinkRepository
import com.mdwiki.repository.PageRepository
import org.springframework.stereotype.Service

@Service
class PageMetadataService(
    private val pageRepository: PageRepository,
    private val linkRepository: LinkRepository,
    private val wikilinkService: WikilinkService,
    private val tagService: TagService
) {
    fun findBacklinks(slug: String): List<Link> {
        val page = pageRepository.findBySlug(slug)
        val normalizedTitle = page?.let { wikilinkService.normalizePageSlug(it.title) }?.takeIf { it.isNotEmpty() }
        val slugsToMatch = buildSet {
            add(slug)
            if (normalizedTitle != null && normalizedTitle != slug) add(normalizedTitle)
        }
        return slugsToMatch.flatMap { linkRepository.findByTargetSlug(it) }.distinctBy { it.id }
    }

    fun deleteSourceLinks(page: Page) {
        linkRepository.deleteBySourcePage(page)
    }

    fun cleanupOrphanedTags() {
        tagService.cleanupOrphanedTags()
    }

    fun syncLinksAndTags(page: Page, content: String, cleanupOrphanedTags: Boolean = false) {
        linkRepository.deleteBySourcePage(page)

        val body = MarkdownFrontmatter.strip(content)
        val wikilinks = wikilinkService.extractWikilinks(body)
        val targetPagesBySlug = findTargetPagesBySlug(wikilinks.map { it.slug }.toSet())
        wikilinks.forEach { wikilink ->
            linkRepository.save(
                Link(
                    sourcePage = page,
                    targetPage = targetPagesBySlug[wikilink.slug],
                    targetSlug = wikilink.slug
                )
            )
        }

        val tagNames = wikilinkService.extractTags(body)
        val tags = tagService.getOrCreateTags(tagNames)
        page.tags.clear()
        page.tags.addAll(tags)
        pageRepository.save(page)

        if (cleanupOrphanedTags) {
            tagService.cleanupOrphanedTags()
        }
    }

    fun resolveIncomingLinks(page: Page) {
        val normalizedTitle = wikilinkService.normalizePageSlug(page.title)
        val targetSlugs = buildSet {
            add(page.slug)
            if (normalizedTitle.isNotEmpty()) add(normalizedTitle)
        }
        val danglingLinks = targetSlugs.flatMap { linkRepository.findByTargetSlug(it) }
            .filter { it.targetPage == null }
            .distinctBy { it.id }
        danglingLinks.forEach { link ->
            link.targetPage = page
            linkRepository.save(link)
        }
    }

    private fun findTargetPagesBySlug(slugs: Set<String>): Map<String, Page> {
        if (slugs.isEmpty()) {
            return emptyMap()
        }
        val bySlug = pageRepository.findAllBySlugIn(slugs).associateBy { it.slug }
        val missing = slugs - bySlug.keys
        val byTitle = missing.mapNotNull { requestedSlug ->
            pageRepository.findByNormalizedTitle(requestedSlug)?.let { requestedSlug to it }
        }.toMap()
        return bySlug + byTitle
    }
}
