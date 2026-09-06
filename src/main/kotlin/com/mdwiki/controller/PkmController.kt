package com.mdwiki.controller

import com.mdwiki.dto.*
import com.mdwiki.service.PkmService
import com.mdwiki.service.SavedSearchService
import com.mdwiki.service.SavedViewService
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.validation.annotation.Validated
import java.time.LocalDate
import java.util.UUID

@RestController
@Validated
class PkmController(
    private val service: PkmService,
    private val savedSearches: SavedSearchService,
    private val savedViews: SavedViewService
) {
    @PostMapping("/api/captures/text")
    @ResponseStatus(HttpStatus.CREATED)
    fun captureText(@Valid @RequestBody request: TextCaptureRequest, auth: Authentication) =
        service.captureText(request, auth.name)

    @PostMapping("/api/captures/url")
    @ResponseStatus(HttpStatus.CREATED)
    fun captureUrl(@Valid @RequestBody request: UrlCaptureRequest, auth: Authentication) =
        service.captureUrl(request, auth.name)

    @PostMapping("/api/captures/image")
    @ResponseStatus(HttpStatus.CREATED)
    fun captureImage(
        @RequestPart file: MultipartFile,
        @RequestParam(required = false) @Size(max = 2000) caption: String?,
        @RequestParam(required = false) @Size(max = 500) title: String?,
        auth: Authentication
    ) = service.captureImage(file, caption, title, auth.name)

    @GetMapping("/api/me/daily-notes/{date}")
    fun getDaily(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        auth: Authentication
    ) = service.getDaily(date, auth.name)

    @PutMapping("/api/me/daily-notes/{date}")
    fun putDaily(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        auth: Authentication
    ) = service.putDaily(date, auth.name)

    @PutMapping("/api/me/recent-pages/{pageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun touchRecent(@PathVariable pageId: UUID, auth: Authentication) =
        service.touchRecent(pageId, auth.name)

    @GetMapping("/api/me/recent-pages")
    fun recent(@RequestParam(defaultValue = "20") limit: Int, auth: Authentication) =
        service.listRecent(limit, auth.name)

    @PutMapping("/api/me/favorites/{pageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun addFavorite(@PathVariable pageId: UUID, auth: Authentication) =
        service.addFavorite(pageId, auth.name)

    @DeleteMapping("/api/me/favorites/{pageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeFavorite(@PathVariable pageId: UUID, auth: Authentication) =
        service.removeFavorite(pageId, auth.name)

    @GetMapping("/api/me/favorites")
    fun favorites(auth: Authentication) = service.listFavorites(auth.name)

    @PutMapping("/api/me/favorite-searches/{savedSearchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun addFavoriteSearch(@PathVariable savedSearchId: UUID, auth: Authentication) =
        savedSearches.addFavorite(savedSearchId, auth.name)

    @DeleteMapping("/api/me/favorite-searches/{savedSearchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeFavoriteSearch(@PathVariable savedSearchId: UUID, auth: Authentication) =
        savedSearches.removeFavorite(savedSearchId, auth.name)

    @GetMapping("/api/me/favorite-searches")
    fun listFavoriteSearches(auth: Authentication) = savedSearches.listFavorites(auth.name)

    @PutMapping("/api/me/favorite-views/{viewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun addFavoriteView(@PathVariable viewId: UUID, auth: Authentication) =
        savedViews.addFavorite(viewId, auth.name)

    @DeleteMapping("/api/me/favorite-views/{viewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeFavoriteView(@PathVariable viewId: UUID, auth: Authentication) =
        savedViews.removeFavorite(viewId, auth.name)

    @GetMapping("/api/me/favorite-views")
    fun listFavoriteViews(auth: Authentication) = savedViews.listFavorites(auth.name)

    @GetMapping("/api/pages/{slug}/unlinked-mentions")
    fun mentions(@PathVariable slug: String) = service.mentions(slug)

    @PostMapping("/api/pages/{slug}/unlinked-mentions/link")
    fun linkMention(
        @PathVariable slug: String,
        @Valid @RequestBody request: LinkUnlinkedMentionRequest,
        auth: Authentication
    ) = service.linkMention(slug, request, auth.name)

    @GetMapping("/api/pages/orphans")
    fun orphans(
        @RequestParam(defaultValue = "NO_INCOMING") definition: OrphanDefinition
    ) = service.orphans(definition)
}
