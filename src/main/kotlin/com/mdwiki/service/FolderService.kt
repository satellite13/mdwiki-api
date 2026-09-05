package com.mdwiki.service

import com.mdwiki.dto.*
import com.mdwiki.error.ConflictException
import com.mdwiki.mapper.displayTitle
import com.mdwiki.mapper.toResponse
import com.mdwiki.model.Folder
import com.mdwiki.model.UserRole
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.service.usecase.DeletePageUseCase
import com.mdwiki.util.NaturalSort
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FolderService(
    private val folderRepository: FolderRepository,
    private val pageRepository: PageRepository,
    private val wikiFileService: WikiFileService,
    private val treeEventsService: TreeEventsService,
    private val deletePageUseCase: DeletePageUseCase,
    @Lazy private val syncService: SyncService,
    private val folderAccessPolicy: FolderAccessPolicy
) {

    fun invalidateCache() {
        // Trees are actor-specific and deliberately rebuilt on every request.
    }

    /** Keep the persistence session open: tree visibility touches lazy folder.owner. */
    @Transactional(readOnly = true)
    fun getTree(username: String): List<FolderTreeNode> = buildTreeFor(username)

    private fun buildTreeFor(username: String): List<FolderTreeNode> {
        val actor = folderAccessPolicy.actor(username)
        val visibleFolders = folderRepository.findAll()
            .filter { actor.role == UserRole.ADMIN || it.owner == null || it.owner?.id == actor.id }
        val visibleIds = visibleFolders.mapNotNull { it.id }.toSet()
        val visiblePages = pageRepository.findAllByDeletedAtIsNull()
            .filter { it.folder == null || it.folder?.owner == null || it.folder?.id in visibleIds }
        val foldersByParent = visibleFolders.groupBy { it.parent?.id }
        val pagesByFolder = visiblePages.groupBy { it.folder?.id }
        fun children(parentId: UUID?): List<FolderTreeNode> {
            val folderNodes = (foldersByParent[parentId] ?: emptyList()).sortedBy { it.sortOrder }.map {
                FolderTreeNode("folder-${it.id}", it.name, "folder", children = children(it.id))
            }
            val pageNodes = (pagesByFolder[parentId] ?: emptyList())
                .sortedWith { left, right -> NaturalSort.compare(left.displayTitle(), right.displayTitle()) }
                .map { FolderTreeNode(it.id.toString(), it.displayTitle(), "page", it.slug) }
            return folderNodes + pageNodes
        }
        return children(null)
    }

    @Transactional
    fun create(request: CreateFolderRequest, username: String): FolderResponse {
        val parent = request.parentId?.let {
            folderRepository.findById(it).orElseThrow { NoSuchElementException("Parent folder not found: $it") }
        }
        val user = folderAccessPolicy.requireCreate(parent, username)
        val duplicate = if (parent == null) {
            folderRepository.existsByOwnerIsNullAndParentIdIsNullAndName(request.name)
        } else {
            folderRepository.existsByParentIdAndName(request.parentId, request.name)
        }
        if (duplicate) {
            throw ConflictException("Folder with name '${request.name}' already exists in this location")
        }

        val folder = Folder(
            name = request.name,
            parent = parent,
            createdBy = user,
            owner = parent?.owner
        )
        val saved = folderRepository.save(folder)
        wikiFileService.ensureFolderDirectory(saved)
        invalidateCache()
        treeEventsService.publishTreeUpdated()
        return saved.toResponse()
    }

    /** Creates or returns a user-owned PKM root folder under the global mutation lock. */
    @Transactional
    fun getOrCreateOwnedPkmFolder(name: String, username: String): Folder {
        MultiPageMutationLock.acquire(pageRepository)
        val owner = folderAccessPolicy.actor(username)
        folderRepository.findByOwnerIdAndParentIdIsNullAndName(owner.id!!, name)?.let { return it }
        val saved = folderRepository.saveAndFlush(
            Folder(name = name, createdBy = owner, owner = owner)
        )
        wikiFileService.ensureFolderDirectory(saved)
        invalidateCache()
        treeEventsService.publishTreeUpdated()
        return saved
    }

    @Transactional
    fun rename(id: UUID, request: UpdateFolderRequest, username: String): FolderResponse {
        MultiPageMutationLock.acquire(pageRepository)
        val folder = folderRepository.findById(id)
            .orElseThrow { NoSuchElementException("Folder not found: $id") }
        folderAccessPolicy.requireAccess(folder, username)

        // Проверяем конфликт только при реальной смене имени:
        // иначе existsByParentIdAndName находит саму папку
        val duplicate = when {
            request.name == folder.name -> false
            folder.parent != null -> folderRepository.existsByParentIdAndName(folder.parent?.id, request.name)
            folder.owner != null -> folderRepository.findByOwnerIdAndParentIdIsNullAndName(
                folder.owner!!.id!!, request.name
            ) != null
            else -> folderRepository.existsByOwnerIsNullAndParentIdIsNullAndName(request.name)
        }
        if (duplicate) {
            throw ConflictException("Folder with name '${request.name}' already exists in this location")
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
    fun move(id: UUID, request: MoveFolderRequest, username: String): FolderResponse {
        MultiPageMutationLock.acquire(pageRepository)
        val folder = folderRepository.findById(id)
            .orElseThrow { NoSuchElementException("Folder not found: $id") }

        val oldParentId = folder.parent?.id
        val oldDir = wikiFileService.resolveFolderDirectory(folder)

        val requestedParent = request.parentId?.let {
            folderRepository.findById(it)
                .orElseThrow { NoSuchElementException("Target parent folder not found: $it") }
        }
        folderAccessPolicy.requireMove(folder, requestedParent, username)

        if (requestedParent != null) {
            require(request.parentId != id) { "Cannot move folder into itself" }
            val targetParent = requestedParent

            // Check for circular reference: walk up from targetParent to root
            var current: Folder? = targetParent
            while (current != null) {
                require(current.id != id) { "Cannot move folder into its own subtree" }
                current = current.parent
            }
        }

        // Check before mutating the managed entity: auto-flush would otherwise make
        // the moving folder find itself in the destination.
        val duplicateAtTarget = if (request.parentId == null) {
            folderRepository.existsByOwnerIsNullAndParentIdIsNullAndName(folder.name)
        } else {
            folderRepository.existsByParentIdAndName(request.parentId, folder.name)
        }
        if (request.parentId != oldParentId && duplicateAtTarget) {
            throw ConflictException("Folder with name '${folder.name}' already exists in target location")
        }
        folder.parent = requestedParent

        val newDir = wikiFileService.resolveFolderDirectory(folder)
        wikiFileService.moveFolderDirectory(oldDir, newDir)

        val saved = folderRepository.save(folder)
        syncSubtreePagePaths(saved.id!!)
        invalidateCache()
        treeEventsService.publishTreeUpdated()
        return saved.toResponse()
    }

    @Transactional
    fun delete(
        id: UUID,
        username: String,
        pageAction: FolderDeletePageAction = FolderDeletePageAction.DELETE
    ) {
        MultiPageMutationLock.acquire(pageRepository)
        val folder = folderRepository.findById(id)
            .orElseThrow { NoSuchElementException("Folder not found: $id") }
        val folderDir = wikiFileService.resolveFolderDirectory(folder)

        val allFolders = folderRepository.findAll()
        val subtreeFolders = collectSubtree(folder, allFolders)
        folderAccessPolicy.requireDeleteSubtree(
            subtreeFolders,
            username,
            movePagesToRoot = pageAction == FolderDeletePageAction.MOVE_TO_ROOT
        )
        val subtreeIds = subtreeFolders.mapNotNull { it.id }.toSet()
        // Include soft-deleted pages: fk_pages_folder has no ON DELETE CASCADE, so leftover
        // trash rows with folder_id block folder removal and the TX rolls back quietly from UX POV.
        val pages = subtreeIds.flatMap { folderId -> pageRepository.findByFolderId(folderId) }
            .distinctBy { it.id }

        when (pageAction) {
            FolderDeletePageAction.DELETE -> {
                for (page in pages) {
                    deletePageUseCase.executePreAuthorized(
                        slug = page.slug,
                        mode = DeletePageUseCase.DeleteMode.HARD,
                        scheduleReconcile = false,
                        ignoreLocked = true
                    )
                }
            }
            FolderDeletePageAction.MOVE_TO_ROOT -> {
                for (page in pages) {
                    val previousFolder = page.folder
                    page.folder = null
                    // Soft-deleted files already live under .trash — only relocate active pages.
                    if (page.deletedAt == null && previousFolder != null) {
                        wikiFileService.relocatePageFile(page, null)
                    }
                }
                if (pages.isNotEmpty()) {
                    pageRepository.saveAll(pages)
                }
            }
        }

        // Удаляем директорию до удаления сущности папки из persistence context:
        // для вложенных папок это безопаснее с точки зрения lazy parent-цепочки.
        if (folderDir.exists()) {
            folderDir.deleteRecursively()
        }
        // Child folders cascade via FK ON DELETE CASCADE; delete root after pages are unlinked.
        folderRepository.delete(folder)
        invalidateCache()
        treeEventsService.publishTreeUpdated()
        if (pageAction == FolderDeletePageAction.DELETE && pages.isNotEmpty()) {
            syncService.scheduleReconcileFromDisk()
        }
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
}
