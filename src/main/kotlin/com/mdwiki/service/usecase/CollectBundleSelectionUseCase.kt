package com.mdwiki.service.usecase

import com.mdwiki.config.WikiProperties
import com.mdwiki.dto.BundleExportRequest
import com.mdwiki.dto.CollectedBundle
import com.mdwiki.dto.CollectedBundleAttachment
import com.mdwiki.dto.CollectedBundleFolder
import com.mdwiki.dto.CollectedBundlePage
import com.mdwiki.model.Folder
import com.mdwiki.model.Page
import com.mdwiki.repository.AttachmentRepository
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.PageRepository
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@Component
class CollectBundleSelectionUseCase(
    private val folderRepository: FolderRepository,
    private val pageRepository: PageRepository,
    private val attachmentRepository: AttachmentRepository,
    private val wikiProperties: WikiProperties
) {
    private val uploadsDir: Path
        get() = Path.of(wikiProperties.contentDir).toAbsolutePath().normalize().resolve("uploads")

    fun execute(request: BundleExportRequest): CollectedBundle {
        val warnings = mutableListOf<String>()
        val allFolders = folderRepository.findAll()
        val foldersById = allFolders.associateBy { it.id }

        val selectedFolders = request.folderIds.map { id ->
            foldersById[id]
                ?: folderRepository.findById(id).orElse(null)
                ?: throw IllegalArgumentException("Folder not found: $id")
        }

        val rootFolders = selectedFolders.filter { folder ->
            selectedFolders.none { ancestor ->
                ancestor.id != folder.id && isDescendantOf(folder, ancestor.id)
            }
        }

        val expandedFolders = linkedMapOf<UUID, Folder>()
        for (root in rootFolders) {
            for (node in descendantsIncludingSelf(root, allFolders)) {
                val id = node.id ?: continue
                expandedFolders.putIfAbsent(id, node)
            }
        }

        val folderPaths = mutableMapOf<UUID, List<String>>()
        for (root in rootFolders) {
            val rootId = root.id ?: continue
            for (node in descendantsIncludingSelf(root, allFolders)) {
                val id = node.id ?: continue
                folderPaths[id] = pathFromRoot(node, rootId)
            }
        }

        val pagesById = linkedMapOf<UUID, Page>()
        for (folderId in expandedFolders.keys) {
            for (page in pageRepository.findByFolderId(folderId)) {
                if (page.deletedAt != null) continue
                val id = page.id ?: continue
                pagesById.putIfAbsent(id, page)
            }
        }

        for (slug in request.pageSlugs.map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
            val page = pageRepository.findBySlugAndDeletedAtIsNull(slug)
            if (page == null) {
                warnings += "Page not found or deleted: $slug"
                continue
            }
            val id = page.id ?: continue
            pagesById.putIfAbsent(id, page)
        }

        val pages = pagesById.values.sortedBy { it.slug }.map { page ->
            val folderId = page.folder?.id
            val folderPath = folderId?.let { folderPaths[it] } ?: emptyList()
            val refs = extractUploadRefs(page.contentMd)
            CollectedBundlePage(
                slug = page.slug,
                title = page.title,
                contentMd = page.contentMd ?: "",
                folderPath = folderPath,
                attachmentRefs = refs
            )
        }

        val folders = folderPaths.entries
            .map { (id, path) ->
                val folder = expandedFolders[id] ?: foldersById[id]
                CollectedBundleFolder(path = path, name = folder?.name ?: path.last())
            }
            .sortedWith(compareBy({ it.path.size }, { it.path.joinToString("/") }))

        val attachments = collectAttachments(pages, pagesById.values.toList(), warnings)

        return CollectedBundle(
            folders = folders,
            pages = pages,
            attachments = attachments,
            warnings = warnings
        )
    }

    private fun collectAttachments(
        pages: List<CollectedBundlePage>,
        sourcePages: List<Page>,
        warnings: MutableList<String>
    ): List<CollectedBundleAttachment> {
        val referencedBy = linkedMapOf<String, MutableSet<String>>()
        for (page in pages) {
            for (storedName in page.attachmentRefs) {
                referencedBy.getOrPut(storedName) { linkedSetOf() }.add(page.slug)
            }
        }

        val pageIds = sourcePages.mapNotNull { it.id }
        if (pageIds.isNotEmpty()) {
            val slugByPageId = sourcePages.associate { it.id to it.slug }
            for (attachment in attachmentRepository.findByPageIdIn(pageIds)) {
                referencedBy.getOrPut(attachment.storedName) { linkedSetOf() }
                    .add(slugByPageId[attachment.page?.id] ?: continue)
            }
        }

        val result = mutableListOf<CollectedBundleAttachment>()
        for ((storedName, slugs) in referencedBy) {
            val record = attachmentRepository.findByStoredName(storedName)
            val diskPath = uploadsDir.resolve(storedName).normalize()
            if (!diskPath.startsWith(uploadsDir) || !Files.isRegularFile(diskPath)) {
                warnings += "Attachment $storedName is missing on disk"
                continue
            }
            result += CollectedBundleAttachment(
                storedName = storedName,
                originalName = record?.originalName ?: storedName,
                contentType = record?.contentType ?: Files.probeContentType(diskPath) ?: "application/octet-stream",
                sizeBytes = record?.sizeBytes ?: Files.size(diskPath),
                diskPath = diskPath,
                referencedBy = slugs.toList()
            )
        }
        return result
    }

    private fun extractUploadRefs(contentMd: String?): List<String> {
        if (contentMd.isNullOrBlank()) return emptyList()
        return UPLOAD_REF.findAll(contentMd)
            .map { it.groupValues[1].trim().trimStart('/') }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    private fun isDescendantOf(folder: Folder, ancestorId: UUID?): Boolean {
        if (ancestorId == null) return false
        var current = folder.parent
        while (current != null) {
            if (current.id == ancestorId) return true
            current = current.parent
        }
        return false
    }

    private fun descendantsIncludingSelf(root: Folder, allFolders: List<Folder>): List<Folder> {
        val byParent = allFolders.groupBy { it.parent?.id }
        val result = mutableListOf<Folder>()
        fun walk(node: Folder) {
            result += node
            for (child in byParent[node.id].orEmpty()) {
                walk(child)
            }
        }
        walk(root)
        return result
    }

    private fun pathFromRoot(folder: Folder, rootId: UUID): List<String> {
        val names = mutableListOf<String>()
        var current: Folder? = folder
        while (current != null) {
            names.add(0, current.name)
            if (current.id == rootId) break
            current = current.parent
        }
        return names
    }

    companion object {
        val UPLOAD_REF = Regex("""/api/uploads/([^)\s"'<>]+)""")
    }
}
