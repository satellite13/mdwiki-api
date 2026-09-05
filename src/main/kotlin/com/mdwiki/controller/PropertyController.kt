package com.mdwiki.controller

import com.mdwiki.dto.*
import com.mdwiki.error.NotFoundException
import com.mdwiki.repository.PageRepository
import com.mdwiki.service.PropertyService
import com.mdwiki.service.usecase.UpdatePageUseCase
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api")
class PropertyController(
    private val properties: PropertyService,
    private val pages: PageRepository,
    private val updatePage: UpdatePageUseCase
) {
    @GetMapping("/property-definitions")
    fun list() = properties.listDefinitions()

    @PostMapping("/property-definitions")
    @PreAuthorize("hasRole('ADMIN')")
    fun create(@RequestBody request: PropertyDefinitionWriteRequest, auth: Authentication) = properties.create(request, auth.name)

    @PatchMapping("/property-definitions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun update(@PathVariable id: UUID, @RequestBody request: PropertyDefinitionWriteRequest) = properties.update(id, request)

    @DeleteMapping("/property-definitions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun delete(@PathVariable id: UUID) = properties.delete(id)

    @PostMapping("/admin/properties/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    fun reindex() = properties.reprojectAll()

    @GetMapping("/pages/{slug}/properties")
    fun getPage(@PathVariable slug: String, auth: Authentication) = properties.pageProperties(slug, auth.name)

    @PatchMapping("/pages/{slug}/properties")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    fun patchPage(@PathVariable slug: String, @RequestBody request: PatchPagePropertiesRequest, auth: Authentication): PageResponse {
        val page = pages.findBySlugAndDeletedAtIsNull(slug) ?: throw NotFoundException("Page not found: $slug")
        val markdown = properties.patchPage(page, request, auth.name)
        return updatePage.execute(slug, UpdatePageRequest(contentMd = markdown, expectedUpdatedAt = request.expectedUpdatedAt), auth.name)
    }
}
