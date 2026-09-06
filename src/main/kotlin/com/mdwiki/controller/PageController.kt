package com.mdwiki.controller

import com.mdwiki.dto.*
import com.mdwiki.service.GraphService
import com.mdwiki.service.PageService
import com.mdwiki.service.usecase.DeletePageUseCase
import com.mdwiki.error.ForbiddenException
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets
import java.util.UUID

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

    @GetMapping("/{slug}/sections")
    fun getSections(@PathVariable slug: String): PageSectionMapResponse = pageService.mapSections(slug)

    @GetMapping("/{slug}/revisions")
    fun revisions(
        @PathVariable slug: String,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) before: Long?
    ) = pageService.listRevisions(slug, limit, before)

    @GetMapping("/{slug}/revisions/{revisionNo}")
    fun revision(@PathVariable slug: String, @PathVariable revisionNo: Long) =
        pageService.getRevision(slug, revisionNo)

    @GetMapping("/{slug}/diff")
    fun diff(
        @PathVariable slug: String,
        @RequestParam from: Long,
        @RequestParam to: Long
    ) = pageService.diffRevisions(slug, from, to)

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

    @PostMapping("/import")
    fun importMd(
        @RequestParam("files") files: List<MultipartFile>,
        @RequestParam(required = false) folderId: UUID?,
        @RequestParam(defaultValue = "false") overwrite: Boolean,
        auth: Authentication
    ): ImportMdPagesResponse {
        val inputs = files.map { file ->
            ImportMdFileInput(
                filename = file.originalFilename?.takeIf { it.isNotBlank() } ?: "untitled.md",
                contentMd = file.bytes.toString(StandardCharsets.UTF_8)
            )
        }
        return pageService.importMd(inputs, folderId, overwrite, auth.name)
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
    fun delete(
        @PathVariable slug: String,
        @RequestParam(defaultValue = "SOFT") mode: DeletePageUseCase.DeleteMode,
        auth: Authentication
    ) = pageService.delete(slug, mode, auth.name)

    @PostMapping("/{slug}/restore")
    fun restore(
        @PathVariable slug: String,
        @RequestBody(required = false) request: RestoreRevisionRequest?,
        auth: Authentication
    ): PageResponse = if (request == null) {
        if (auth.authorities.none { it.authority == "ROLE_ADMIN" }) {
            throw ForbiddenException("Only administrators can restore deleted pages")
        }
        pageService.restore(slug, auth.name)
    } else pageService.restoreRevision(slug, request, auth.name)

    @GetMapping("/deleted")
    fun listDeleted(): List<PageListItem> = pageService.findDeleted()
}
