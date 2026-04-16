package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.dto.*
import com.mdwiki.model.Link
import com.mdwiki.model.Page
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.LinkRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.rag.RagService
import com.mdwiki.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.time.Instant

@Service
class PageService(
    private val pageRepository: PageRepository,
    private val userRepository: UserRepository,
    private val linkRepository: LinkRepository,
    private val folderRepository: FolderRepository,
    private val wikilinkService: WikilinkService,
    private val tagService: TagService,
    private val wikiProperties: WikiProperties,
    private val ragService: RagService
) {

    private val contentDir: File get() = File(wikiProperties.contentDir).also { it.mkdirs() }

    @Transactional(readOnly = true)
    fun findAll(): List<PageListItem> {
        return pageRepository.findAll().map { it.toListItem() }
    }

    @Transactional(readOnly = true)
    fun findBySlug(slug: String): PageResponse {
        val page = pageRepository.findBySlug(slug)
            ?: throw NoSuchElementException("Page not found: $slug")
        return page.toResponse()
    }

    @Transactional(readOnly = true)
    fun getBacklinks(slug: String): List<BacklinkResponse> {
        return linkRepository.findByTargetSlug(slug).map {
            BacklinkResponse(slug = it.sourcePage.slug, title = it.sourcePage.title)
        }
    }

    @Transactional
    fun create(request: CreatePageRequest, username: String): PageResponse {
        require(!pageRepository.existsBySlug(request.slug)) { "Page with slug '${request.slug}' already exists" }

        val user = userRepository.findByUsername(username)
        val file = File(contentDir, "${request.slug}.md")
        file.writeText(request.contentMd)

        val folder = request.folderId?.let {
            folderRepository.findById(it).orElseThrow { NoSuchElementException("Folder not found: $it") }
        }

        val page = Page(
            slug = request.slug,
            title = request.title,
            contentMd = request.contentMd,
            filePath = file.absolutePath,
            createdBy = user,
            updatedBy = user,
            folder = folder
        )
        val saved = pageRepository.save(page)

        processLinksAndTags(saved, request.contentMd)
        resolveIncomingLinks(saved)
        ragService.indexPage(saved)

        return saved.toResponse()
    }

    @Transactional
    fun update(slug: String, request: UpdatePageRequest, username: String): PageResponse {
        val page = pageRepository.findBySlug(slug)
            ?: throw NoSuchElementException("Page not found: $slug")
        val user = userRepository.findByUsername(username)

        request.title?.let { page.title = it }
        request.folderId?.let { folderId ->
            page.folder = folderRepository.findById(folderId)
                .orElseThrow { NoSuchElementException("Folder not found: $folderId") }
        }
        request.contentMd?.let { newContent ->
            page.contentMd = newContent
            page.filePath?.let { path -> File(path).writeText(newContent) }
        }
        page.updatedBy = user
        page.updatedAt = Instant.now()

        val saved = pageRepository.save(page)

        if (request.contentMd != null) {
            processLinksAndTags(saved, request.contentMd)
            ragService.indexPage(saved)
        }

        return saved.toResponse()
    }

    @Transactional
    fun delete(slug: String) {
        val page = pageRepository.findBySlug(slug)
            ?: throw NoSuchElementException("Page not found: $slug")
        page.filePath?.let { File(it).delete() }
        linkRepository.deleteBySourcePage(page)
        ragService.deletePageChunks(page.id!!)
        pageRepository.delete(page)
        tagService.cleanupOrphanedTags()
    }

    private fun processLinksAndTags(page: Page, content: String) {
        linkRepository.deleteBySourcePage(page)
        val wikilinks = wikilinkService.extractWikilinks(content)
        for (wl in wikilinks) {
            val targetPage = pageRepository.findBySlug(wl.slug)
            linkRepository.save(Link(sourcePage = page, targetPage = targetPage, targetSlug = wl.slug))
        }

        val tagNames = wikilinkService.extractTags(content)
        val tags = tagService.getOrCreateTags(tagNames)
        page.tags.clear()
        page.tags.addAll(tags)
        pageRepository.save(page)
        tagService.cleanupOrphanedTags()
    }

    private fun resolveIncomingLinks(page: Page) {
        val danglingLinks = linkRepository.findByTargetSlug(page.slug)
            .filter { it.targetPage == null }
        for (link in danglingLinks) {
            link.targetPage = page
            linkRepository.save(link)
        }
    }

    private fun Page.toResponse() = PageResponse(
        id = id!!,
        slug = slug,
        title = title,
        contentMd = contentMd,
        contentHtml = contentHtml,
        tags = tags.map { it.name },
        createdBy = createdBy?.username,
        updatedBy = updatedBy?.username,
        folderId = folder?.id,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun Page.toListItem() = PageListItem(
        id = id!!,
        slug = slug,
        title = title,
        tags = tags.map { it.name },
        folderId = folder?.id,
        updatedAt = updatedAt
    )
}
