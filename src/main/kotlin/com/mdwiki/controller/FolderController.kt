package com.mdwiki.controller

import com.mdwiki.dto.*
import com.mdwiki.service.FolderService
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/folders")
class FolderController(private val folderService: FolderService) {

    @GetMapping("/tree")
    fun getTree(auth: Authentication): List<FolderTreeNode> = folderService.getTree(auth.name)

    @PostMapping
    fun create(@Valid @RequestBody request: CreateFolderRequest, auth: Authentication): FolderResponse {
        return folderService.create(request, auth.name)
    }

    @PutMapping("/{id}")
    fun rename(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateFolderRequest,
        auth: Authentication
    ): FolderResponse {
        return folderService.rename(id, request, auth.name)
    }

    @PutMapping("/{id}/move")
    fun move(
        @PathVariable id: UUID,
        @Valid @RequestBody request: MoveFolderRequest,
        auth: Authentication
    ): FolderResponse {
        return folderService.move(id, request, auth.name)
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "DELETE") pageAction: FolderDeletePageAction,
        auth: Authentication
    ) = folderService.delete(id, auth.name, pageAction)
}
