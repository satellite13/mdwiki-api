package com.mdwiki.service

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
    fun findBacklinks(slug: String): List<Link> = linkRepository.findByTargetSlug(slug)

    fun deleteSourceLinks(page: Page) {
        linkRepository.deleteBySourcePage(page)
    }

    fun cleanupOrphanedTags() {
        tagService.cleanupOrphanedTags()
    }

    fun syncLinksAndTags(page: Page, content: String, cleanupOrphanedTags: Boolean = false) {
        linkRepository.deleteBySourcePage(page)

        val wikilinks = wikilinkService.extractWikilinks(content)
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

        val tagNames = wikilinkService.extractTags(content)
        val tags = tagService.getOrCreateTags(tagNames)
        page.tags.clear()
        page.tags.addAll(tags)
        pageRepository.save(page)

        if (cleanupOrphanedTags) {
            tagService.cleanupOrphanedTags()
        }
    }

    fun resolveIncomingLinks(page: Page) {
        val danglingLinks = linkRepository.findByTargetSlug(page.slug)
            .filter { it.targetPage == null }
        danglingLinks.forEach { link ->
            link.targetPage = page
            linkRepository.save(link)
        }
    }

    private fun findTargetPagesBySlug(slugs: Set<String>): Map<String, Page> {
        if (slugs.isEmpty()) {
            return emptyMap()
        }
        return pageRepository.findAllBySlugIn(slugs).associateBy { it.slug }
    }
}
