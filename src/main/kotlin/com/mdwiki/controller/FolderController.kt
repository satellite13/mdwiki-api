package com.mdwiki.controller

import com.mdwiki.dto.*
import com.mdwiki.service.FolderService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/folders")
class FolderController(private val folderService: FolderService) {

    @GetMapping("/tree")
    fun getTree(): List<FolderTreeNode> = folderService.getTree()

    @PostMapping
    fun create(@RequestBody request: CreateFolderRequest, auth: Authentication): FolderResponse {
        return folderService.create(request, auth.name)
    }

    @PutMapping("/{id}")
    fun rename(@PathVariable id: UUID, @RequestBody request: UpdateFolderRequest): FolderResponse {
        return folderService.rename(id, request)
    }

    @PutMapping("/{id}/move")
    fun move(@PathVariable id: UUID, @RequestBody request: MoveFolderRequest): FolderResponse {
        return folderService.move(id, request)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID) = folderService.delete(id)
}
