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
    fun getTree(): List<FolderTreeNode> = folderService.getTree()

    @PostMapping
    fun create(@Valid @RequestBody request: CreateFolderRequest, auth: Authentication): FolderResponse {
        return folderService.create(request, auth.name)
    }

    @PutMapping("/{id}")
    fun rename(@PathVariable id: UUID, @Valid @RequestBody request: UpdateFolderRequest): FolderResponse {
        return folderService.rename(id, request)
    }

    @PutMapping("/{id}/move")
    fun move(@PathVariable id: UUID, @Valid @RequestBody request: MoveFolderRequest): FolderResponse {
        return folderService.move(id, request)
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "DELETE") pageAction: FolderDeletePageAction
    ) = folderService.delete(id, pageAction)
}
