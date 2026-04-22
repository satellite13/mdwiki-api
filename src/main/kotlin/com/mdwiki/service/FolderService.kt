package com.mdwiki.service

import com.mdwiki.dto.*
import com.mdwiki.model.Folder
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class FolderService(
    private val folderRepository: FolderRepository,
    private val pageRepository: PageRepository,
    private val userRepository: UserRepository,
    private val wikiFileService: WikiFileService,
    private val treeEventsService: TreeEventsService
) {

    @Volatile
    private var cachedTree: List<FolderTreeNode>? = null
    @Volatile
    private var cacheTime: Instant = Instant.MIN

    fun invalidateCache() {
        cachedTree = null
    }

    fun getTree(): List<FolderTreeNode> {
        val cached = cachedTree
        if (cached != null && Duration.between(cacheTime, Instant.now()).seconds < 30) {
            return cached
        }

        val allFolders = folderRepository.findAll()
        val allPages = pageRepository.findAllByDeletedAtIsNull()

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

        val result = buildChildren(null)
        cachedTree = result
        cacheTime = Instant.now()
        return result
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
        wikiFileService.ensureFolderDirectory(saved)
        invalidateCache()
        treeEventsService.publishTreeUpdated()
        return saved.toResponse()
    }

    @Transactional
    fun rename(id: UUID, request: UpdateFolderRequest): FolderResponse {
        val folder = folderRepository.findById(id)
            .orElseThrow { NoSuchElementException("Folder not found: $id") }

        require(!folderRepository.existsByParentIdAndName(folder.parent?.id, request.name)) {
            "Folder with name '${request.name}' already exists in this location"
        }

        val oldDir = wikiFileService.resolveFolderDirectory(folder)
        folder.name = request.name
        val newDir = wikiFileService.resolveFolderDirectory(folder)
        wikiFileService.moveFolderDirectory(oldDir, newDir)

        val saved = folderRepository.save(folder)
        syncSubtreePagePaths(saved.id!!)
        invalidateCache()
        treeEventsService.publishTreeUpdated()
        return saved.toResponse()
    }

    @Transactional
    fun move(id: UUID, request: MoveFolderRequest): FolderResponse {
        val folder = folderRepository.findById(id)
            .orElseThrow { NoSuchElementException("Folder not found: $id") }

        val oldDir = wikiFileService.resolveFolderDirectory(folder)

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

        val newDir = wikiFileService.resolveFolderDirectory(folder)
        wikiFileService.moveFolderDirectory(oldDir, newDir)

        val saved = folderRepository.save(folder)
        syncSubtreePagePaths(saved.id!!)
        invalidateCache()
        treeEventsService.publishTreeUpdated()
        return saved.toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        val folder = folderRepository.findById(id)
            .orElseThrow { NoSuchElementException("Folder not found: $id") }
        val folderDir = wikiFileService.resolveFolderDirectory(folder)

        val allFolders = folderRepository.findAll()
        val subtreeFolders = collectSubtree(folder, allFolders)
        val subtreeIds = subtreeFolders.mapNotNull { it.id }.toSet()
        val pages = pageRepository.findAll().filter { page -> page.folder?.id in subtreeIds }

        for (page in pages) {
            page.folder = null
            wikiFileService.relocatePageFile(page, null)
        }
        pageRepository.saveAll(pages)

        // Удаляем директорию до удаления сущности папки из persistence context:
        // для вложенных папок это безопаснее с точки зрения lazy parent-цепочки.
        if (folderDir.exists()) {
            folderDir.deleteRecursively()
        }
        folderRepository.delete(folder)
        invalidateCache()
        treeEventsService.publishTreeUpdated()
    }

    private fun syncSubtreePagePaths(rootFolderId: UUID) {
        val allFolders = folderRepository.findAll()
        val rootFolder = allFolders.firstOrNull { it.id == rootFolderId } ?: return
        val subtreeIds = collectSubtree(rootFolder, allFolders).mapNotNull { it.id }.toSet()
        val pages = pageRepository.findAll().filter { page -> page.folder?.id in subtreeIds }
        for (page in pages) {
            page.filePath = wikiFileService.resolvePageFile(page.slug, page.folder).absolutePath
        }
        if (pages.isNotEmpty()) {
            pageRepository.saveAll(pages)
        }
    }

    private fun collectSubtree(root: Folder, allFolders: List<Folder>): List<Folder> {
        val byParent = allFolders.groupBy { it.parent?.id }
        val result = mutableListOf<Folder>()

        fun walk(node: Folder) {
            result.add(node)
            val children = byParent[node.id] ?: emptyList()
            for (child in children) {
                walk(child)
            }
        }

        walk(root)
        return result
    }

    private fun Folder.toResponse() = FolderResponse(
        id = id!!,
        name = name,
        parentId = parent?.id,
        sortOrder = sortOrder,
        createdAt = createdAt
    )
}
