package com.mdwiki.controller

import com.mdwiki.dto.SavedViewWriteRequest
import com.mdwiki.service.SavedViewService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/me/views")
class SavedViewController(private val views: SavedViewService) {
    @GetMapping fun list(auth: Authentication) = views.list(auth.name)
    @PostMapping fun create(@RequestBody request: SavedViewWriteRequest, auth: Authentication) = views.create(request, auth.name)
    @GetMapping("/{id}") fun get(@PathVariable id: UUID, auth: Authentication) = views.get(id, auth.name)
    @PatchMapping("/{id}") fun update(@PathVariable id: UUID, @RequestBody request: SavedViewWriteRequest, auth: Authentication) = views.update(id, request, auth.name)
    @DeleteMapping("/{id}") fun delete(@PathVariable id: UUID, auth: Authentication) = views.delete(id, auth.name)
    @PostMapping("/{id}/run") fun run(@PathVariable id: UUID, @RequestParam(required = false) cursor: String?, @RequestParam(defaultValue = "50") limit: Int, auth: Authentication) =
        views.run(id, auth.name, cursor, limit)
}
