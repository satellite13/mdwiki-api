package com.mdwiki.controller

import com.mdwiki.dto.StableLinkRequest
import com.mdwiki.service.StableSectionLinkService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
class StableSectionLinkController(private val service: StableSectionLinkService) {
    @PostMapping("/api/pages/{slug}/sections/stable-link")
    fun materialize(
        @PathVariable slug: String,
        @RequestBody request: StableLinkRequest,
        auth: Authentication
    ) = service.materialize(slug, request, auth.name)

    @GetMapping("/api/section-links/{stableId}")
    fun resolve(@PathVariable stableId: String) = service.resolve(stableId)
}
