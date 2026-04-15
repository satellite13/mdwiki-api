package com.mdwiki.controller

import com.mdwiki.dto.*
import com.mdwiki.service.PageService
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/pages")
class PageController(private val pageService: PageService) {

    @GetMapping
    fun list(): List<PageListItem> = pageService.findAll()

    @GetMapping("/{slug}")
    fun getBySlug(@PathVariable slug: String): PageResponse = pageService.findBySlug(slug)

    @GetMapping("/{slug}/backlinks")
    fun getBacklinks(@PathVariable slug: String): List<BacklinkResponse> = pageService.getBacklinks(slug)

    @PostMapping
    fun create(@Valid @RequestBody request: CreatePageRequest, auth: Authentication): PageResponse {
        return pageService.create(request, auth.name)
    }

    @PutMapping("/{slug}")
    fun update(
        @PathVariable slug: String,
        @Valid @RequestBody request: UpdatePageRequest,
        auth: Authentication
    ): PageResponse {
        return pageService.update(slug, request, auth.name)
    }

    @DeleteMapping("/{slug}")
    fun delete(@PathVariable slug: String) = pageService.delete(slug)
}
