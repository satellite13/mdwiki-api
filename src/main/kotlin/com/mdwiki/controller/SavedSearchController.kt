package com.mdwiki.controller

import com.mdwiki.dto.SavedSearchWriteRequest
import com.mdwiki.service.SavedSearchService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/me/saved-searches")
class SavedSearchController(private val service: SavedSearchService) {
    @GetMapping
    fun list(auth: Authentication) = service.list(auth.name)

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID, auth: Authentication) = service.get(auth.name, id)

    @PostMapping
    fun create(@RequestBody request: SavedSearchWriteRequest, auth: Authentication) =
        service.create(auth.name, request)

    @PutMapping("/{id}")
    fun put(@PathVariable id: UUID, @RequestBody request: SavedSearchWriteRequest, auth: Authentication) =
        service.update(auth.name, id, request)

    @PatchMapping("/{id}")
    fun patch(@PathVariable id: UUID, @RequestBody request: SavedSearchWriteRequest, auth: Authentication) =
        service.update(auth.name, id, request)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID, auth: Authentication) = service.delete(auth.name, id)
}
