package com.mdwiki.service

import com.mdwiki.dto.*
import com.mdwiki.error.NotFoundException
import com.mdwiki.mapper.toListItem
import com.mdwiki.mapper.toResponse
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.rag.RagService
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.usecase.CreatePageUseCase
import com.mdwiki.service.usecase.DeletePageUseCase
import com.mdwiki.service.usecase.UpdatePageUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PageService(
    private val pageRepository: PageRepository,
    private val userRepository: UserRepository,
    private val folderRepository: FolderRepository,
    private val pageMetadataService: PageMetadataService,
    private val wikiFileService: WikiFileService,
    private val ragService: RagService,
    private val treeEventsService: TreeEventsService
) {
    private val createPageUseCase = CreatePageUseCase(
        pageRepository = pageRepository,
        userRepository = userRepository,
        folderRepository = folderRepository,
        pageMetadataService = pageMetadataService,
        ragService = ragService,
        wikiFileService = wikiFileService
    )
    private val updatePageUseCase = UpdatePageUseCase(
        pageRepository = pageRepository,
        userRepository = userRepository,
        folderRepository = folderRepository,
        pageMetadataService = pageMetadataService,
        ragService = ragService,
        wikiFileService = wikiFileService
    )
    private val deletePageUseCase = DeletePageUseCase(
        pageRepository = pageRepository,
        pageMetadataService = pageMetadataService,
        ragService = ragService,
        wikiFileService = wikiFileService
    )

    @Transactional(readOnly = true)
    fun findAll(): List<PageListItem> {
        return pageRepository.findAll().map { it.toListItem() }
    }

    @Transactional(readOnly = true)
    fun findBySlug(slug: String): PageResponse {
        val page = pageRepository.findBySlug(slug)
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
}
