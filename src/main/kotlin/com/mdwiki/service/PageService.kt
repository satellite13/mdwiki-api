package com.mdwiki.service

import com.mdwiki.dto.*
import com.mdwiki.error.NotFoundException
import com.mdwiki.mapper.toListItem
import com.mdwiki.mapper.toResponse
import com.mdwiki.repository.PageRepository
import com.mdwiki.service.usecase.CreatePageUseCase
import com.mdwiki.service.usecase.DeletePageUseCase
import com.mdwiki.service.usecase.UpdatePageUseCase
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PageService(
    private val pageRepository: PageRepository,
    private val pageMetadataService: PageMetadataService,
    private val treeEventsService: TreeEventsService,
    private val createPageUseCase: CreatePageUseCase,
    private val updatePageUseCase: UpdatePageUseCase,
    private val deletePageUseCase: DeletePageUseCase
) {
    @Transactional(readOnly = true)
    fun findAll(page: Int = 0, size: Int = 50): Page<PageListItem> {
        val pageable = PageRequest.of(page, size)
        return pageRepository.findAllByDeletedAtIsNull(pageable).map { it.toListItem() }
    }

    @Transactional(readOnly = true)
    fun findBySlug(slug: String): PageResponse {
        val page = pageRepository.findBySlugAndDeletedAtIsNull(slug)
            ?: pageRepository.findByNormalizedTitle(slug)?.takeIf { it.deletedAt == null }
            ?: throw NotFoundException("Page not found: $slug")
        return page.toResponse()
    }

    @Transactional(readOnly = true)
    fun getBacklinks(slug: String): List<BacklinkResponse> {
        return pageMetadataService.findBacklinks(slug).map {
            BacklinkResponse(slug = it.sourcePage.slug, title = it.sourcePage.title)
        }
    }

    @Transactional
    fun create(request: CreatePageRequest, username: String): PageResponse {
        val created = createPageUseCase.execute(request, username)
        treeEventsService.publishTreeUpdated()
        return created
    }

    @Transactional
    fun update(slug: String, request: UpdatePageRequest, username: String): PageResponse {
        val updated = updatePageUseCase.execute(slug, request, username)
        treeEventsService.publishTreeUpdated()
        return updated
    }

    @Transactional
    fun delete(slug: String) {
        deletePageUseCase.execute(slug)
        treeEventsService.publishTreeUpdated()
    }

    @Transactional
    fun restore(slug: String): PageResponse {
        val page = pageRepository.findBySlug(slug)
            ?: throw NotFoundException("Page not found: $slug")
        if (page.deletedAt == null) {
            throw IllegalStateException("Page is not deleted: $slug")
        }
        page.deletedAt = null
        val saved = pageRepository.save(page)
        treeEventsService.publishTreeUpdated()
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun findDeleted(): List<PageListItem> {
        return pageRepository.findByDeletedAtIsNotNull().map { it.toListItem() }
    }
}
