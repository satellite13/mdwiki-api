package com.mdwiki.controller

import com.mdwiki.dto.*
import com.mdwiki.service.GraphService
import com.mdwiki.service.PageService
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/pages")
class PageController(
    private val pageService: PageService,
    private val graphService: GraphService
) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        response: HttpServletResponse
    ): List<PageListItem> {
        val result = pageService.findAll(page, size)
        response.setHeader("X-Total-Count", result.totalElements.toString())
        return result.content
    }

    @GetMapping("/{slug}")
    fun getBySlug(@PathVariable slug: String): PageResponse = pageService.findBySlug(slug)

    @GetMapping("/{slug}/graph")
    fun getGraph(
        @PathVariable slug: String,
        @RequestParam(defaultValue = "1") depth: Int
    ): GraphResponse {
        return graphService.getGraph(slug, depth)
    }

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

    @PostMapping("/{slug}/restore")
    fun restore(@PathVariable slug: String): PageResponse = pageService.restore(slug)

    @GetMapping("/deleted")
    fun listDeleted(): List<PageListItem> = pageService.findDeleted()
}
