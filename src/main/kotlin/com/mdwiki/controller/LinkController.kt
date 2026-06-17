package com.mdwiki.controller

import com.mdwiki.dto.BrokenLinkResponse
import com.mdwiki.dto.RewriteBrokenLinksRequest
import com.mdwiki.dto.RewriteBrokenLinksResponse
import com.mdwiki.service.BrokenLinkService
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/links")
class LinkController(
    private val brokenLinkService: BrokenLinkService,
) {
    @GetMapping("/broken")
    fun listBroken(): List<BrokenLinkResponse> = brokenLinkService.listBroken()

    @PostMapping("/rewrite")
    fun rewriteBrokenLinks(
        @Valid @RequestBody request: RewriteBrokenLinksRequest,
        auth: Authentication,
    ): RewriteBrokenLinksResponse = brokenLinkService.rewriteBrokenLinks(
        fromTarget = request.fromTarget,
        toSlug = request.toSlug,
        sourceSlug = request.sourceSlug,
        username = auth.name,
    )
}
