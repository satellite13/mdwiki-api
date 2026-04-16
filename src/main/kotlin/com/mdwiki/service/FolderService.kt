package com.mdwiki.service

import com.mdwiki.dto.*
import com.mdwiki.model.Folder
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FolderService(
    private val folderRepository: FolderRepository,
    private val pageRepository: PageRepository,
    private val userRepository: UserRepository
) {

    fun getTree(): List<FolderTreeNode> {
        val allFolders = folderRepository.findAll()
        val allPages = pageRepository.findAll()

        val foldersByParent = allFolders.groupBy { it.parent?.id }
        val pagesByFolder = allPages.groupBy { it.folder?.id }

        fun buildChildren(parentId: UUID?): List<FolderTreeNode> {
            val folderNodes = (foldersByParent[parentId] ?: emptyList())
                .sortedBy { it.sortOrder }
                .map { folder ->
                    FolderTreeNode(
                        id = "folder-${folder.id}",
                        name = folder.name,
                        type = "folder",
                        children = buildChildren(folder.id)
                    )
                }

            val pageNodes = (pagesByFolder[parentId] ?: emptyList())
                .sortedBy { it.title }
                .map { page ->
                    FolderTreeNode(
                        id = page.id.toString(),
                        name = page.title,
                        type = "page",
                        slug = page.slug
                    )
                }

            return folderNodes + pageNodes
        }

        return buildChildren(null)
    }

    @Transactional
    fun create(request: CreateFolderRequest, username: String): FolderResponse {
        require(!folderRepository.existsByParentIdAndName(request.parentId, request.name)) {
            "Folder with name '${request.name}' already exists in this location"
        }

        val user = userRepository.findByUsername(username)
        val parent = request.parentId?.let {
            folderRepository.findById(it).orElseThrow { NoSuchElementException("Parent folder not found: $it") }
        }

        val folder = Folder(
            name = request.name,
            parent = parent,
            createdBy = user
        )
        val saved = folderRepository.save(folder)
        return saved.toResponse()
    }

    @Transactional
    fun rename(id: UUID, request: UpdateFolderRequest): FolderResponse {
        val folder = folderRepository.findById(id)
            .orElseThrow { NoSuchElementException("Folder not found: $id") }

        require(!folderRepository.existsByParentIdAndName(folder.parent?.id, request.name)) {
            "Folder with name '${request.name}' already exists in this location"
        }

        folder.name = request.name
        val saved = folderRepository.save(folder)
        return saved.toResponse()
    }

    @Transactional
    fun move(id: UUID, request: MoveFolderRequest): FolderResponse {
        val folder = folderRepository.findById(id)
            .orElseThrow { NoSuchElementException("Folder not found: $id") }

        if (request.parentId != null) {
            require(request.parentId != id) { "Cannot move folder into itself" }
            val targetParent = folderRepository.findById(request.parentId)
                .orElseThrow { NoSuchElementException("Target parent folder not found: ${request.parentId}") }

            // Check for circular reference: walk up from targetParent to root
            var current: Folder? = targetParent
            while (current != null) {
                require(current.id != id) { "Cannot move folder into its own subtree" }
                current = current.parent
            }

            folder.parent = targetParent
        } else {
            folder.parent = null
        }

        val saved = folderRepository.save(folder)
        return saved.toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        val folder = folderRepository.findById(id)
            .orElseThrow { NoSuchElementException("Folder not found: $id") }

        // Set folder_id = null for pages in this folder (and subfolders via cascade)
        val pages = pageRepository.findByFolderId(id)
        for (page in pages) {
            page.folder = null
            pageRepository.save(page)
        }

        folderRepository.delete(folder)
    }

    private fun Folder.toResponse() = FolderResponse(
        id = id!!,
        name = name,
        parentId = parent?.id,
        sortOrder = sortOrder,
        createdAt = createdAt
    )
}
