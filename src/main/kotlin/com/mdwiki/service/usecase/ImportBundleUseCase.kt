package com.mdwiki.service.usecase

import com.fasterxml.jackson.databind.ObjectMapper
import com.mdwiki.config.WikiProperties
import com.mdwiki.dto.BundleImportResponse
import com.mdwiki.dto.BundleManifest
import com.mdwiki.dto.BundleSlugRemap
import com.mdwiki.dto.CreateFolderRequest
import com.mdwiki.dto.CreatePageRequest
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.service.AttachmentService
import com.mdwiki.service.FolderService
import com.mdwiki.service.MultiPageMutationLock
import com.mdwiki.service.WikilinkService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.zip.ZipInputStream

@Component
class ImportBundleUseCase(
    private val objectMapper: ObjectMapper,
    private val folderService: FolderService,
    private val folderRepository: FolderRepository,
    private val pageRepository: PageRepository,
    private val createPageUseCase: CreatePageUseCase,
    private val attachmentService: AttachmentService,
    private val wikilinkService: WikilinkService,
    private val wikiProperties: WikiProperties
) {
    @Transactional
    fun execute(
        zipStream: InputStream,
        targetFolderId: UUID?,
        username: String,
        declaredSizeBytes: Long? = null
    ): BundleImportResponse {
        val maxBytes = wikiProperties.bundle.maxSize.toBytes()
        if (declaredSizeBytes != null && declaredSizeBytes > maxBytes) {
            throw IllegalArgumentException("Bundle exceeds max size of ${wikiProperties.bundle.maxSize}")
        }
        MultiPageMutationLock.acquire(pageRepository)
        if (targetFolderId != null && folderRepository.findById(targetFolderId).isEmpty) {
            throw IllegalArgumentException("Target folder not found: $targetFolderId")
        }

        val workDir = Files.createTempDirectory("mdwiki-bundle-")
        try {
            unpackZip(zipStream, workDir, maxBytes)
            val manifestFile = workDir.resolve("manifest.json")
            if (!Files.isRegularFile(manifestFile)) {
                throw IllegalArgumentException("Bundle is missing manifest.json")
            }
            val manifest = objectMapper.readValue(manifestFile.toFile(), BundleManifest::class.java)
            if (manifest.format != BundleManifest.FORMAT) {
                throw IllegalArgumentException("Unsupported bundle format: ${manifest.format}")
            }
            if (manifest.version != BundleManifest.VERSION) {
                throw IllegalArgumentException("Unsupported bundle version: ${manifest.version}")
            }

            val folderIds = createFolders(manifest, targetFolderId, username)
            val slugMap = allocateSlugs(manifest.pages.map { it.slug })
            val storedNameMap = importAttachments(manifest, workDir, username)
            val errors = mutableListOf<String>()
            var createdPages = 0

            for (page in manifest.pages) {
                try {
                    val sourceFile = safeResolve(workDir, page.file)
                    if (!Files.isRegularFile(sourceFile)) {
                        errors += "Missing page file for slug '${page.slug}'"
                        continue
                    }
                    var content = Files.readString(sourceFile)
                    content = BundleContentRewriter.rewriteSlugs(content, slugMap, wikilinkService)
                    content = BundleContentRewriter.rewriteUploads(content, storedNameMap)
                    val newSlug = slugMap[page.slug] ?: page.slug
                    createPageUseCase.execute(
                        CreatePageRequest(
                            slug = newSlug,
                            title = page.title,
                            contentMd = content,
                            folderId = folderIdForPath(page.folderPath, folderIds, targetFolderId)
                        ),
                        username
                    )
                    createdPages++
                } catch (e: Exception) {
                    errors += "Failed to import page '${page.slug}': ${e.message}"
                }
            }

            return BundleImportResponse(
                createdPages = createdPages,
                createdFolders = folderIds.size,
                remappedSlugs = slugMap
                    .filter { (from, to) -> from != to }
                    .map { (from, to) -> BundleSlugRemap(from, to) },
                attachments = storedNameMap.size,
                errors = errors
            )
        } finally {
            workDir.toFile().deleteRecursively()
        }
    }

    private fun createFolders(
        manifest: BundleManifest,
        targetFolderId: UUID?,
        username: String
    ): Map<List<String>, UUID> {
        val paths = linkedSetOf<List<String>>()
        for (folder in manifest.folders) {
            if (folder.path.isNotEmpty()) paths += folder.path
        }
        for (page in manifest.pages) {
            if (page.folderPath.isNotEmpty()) {
                for (i in 1..page.folderPath.size) {
                    paths += page.folderPath.take(i)
                }
            }
        }
        val ordered = paths.sortedWith(compareBy({ it.size }, { it.joinToString("/") }))
        val ids = mutableMapOf<List<String>, UUID>()
        for (path in ordered) {
            val parentId = if (path.size == 1) targetFolderId else ids[path.dropLast(1)]
            val desiredName = path.last()
            val name = uniqueFolderName(parentId, desiredName)
            val created = folderService.create(CreateFolderRequest(name = name, parentId = parentId), username)
            ids[path] = created.id
        }
        return ids
    }

    private fun uniqueFolderName(parentId: UUID?, desired: String): String {
        if (!folderRepository.existsByParentIdAndName(parentId, desired)) return desired
        var n = 2
        while (folderRepository.existsByParentIdAndName(parentId, "$desired ($n)")) {
            n++
        }
        return "$desired ($n)"
    }

    private fun allocateSlugs(bundleSlugs: List<String>): Map<String, String> {
        val taken = mutableSetOf<String>()
        val map = linkedMapOf<String, String>()
        for (slug in bundleSlugs) {
            var candidate = slug
            var n = 2
            while (candidate in taken || pageRepository.existsBySlug(candidate)) {
                candidate = "$slug-$n"
                n++
            }
            taken += candidate
            map[slug] = candidate
        }
        return map
    }

    private fun importAttachments(
        manifest: BundleManifest,
        workDir: Path,
        username: String
    ): Map<String, String> {
        val map = linkedMapOf<String, String>()
        for (attachment in manifest.attachments) {
            val source = safeResolve(workDir, attachment.file)
            if (!Files.isRegularFile(source)) {
                continue
            }
            val uploaded = attachmentService.uploadFromTrustedPath(
                filePath = source,
                username = username,
                pageId = null,
                originalName = attachment.originalName,
                contentType = attachment.contentType
            )
            map[attachment.storedName] = uploaded.storedName
        }
        return map
    }

    private fun folderIdForPath(
        folderPath: List<String>,
        folderIds: Map<List<String>, UUID>,
        targetFolderId: UUID?
    ): UUID? = if (folderPath.isEmpty()) targetFolderId else folderIds[folderPath]

    private fun unpackZip(zipStream: InputStream, destDir: Path, maxBytes: Long) {
        var written = 0L
        ZipInputStream(zipStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val target = safeResolve(destDir, entry.name)
                    Files.createDirectories(target.parent)
                    Files.newOutputStream(target).use { out ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            written += n
                            if (written > maxBytes) {
                                throw IllegalArgumentException("Bundle exceeds max size of ${wikiProperties.bundle.maxSize}")
                            }
                            out.write(buffer, 0, n)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    companion object {
        fun safeResolve(destDir: Path, entryName: String): Path {
            val normalizedName = entryName.replace('\\', '/').trimStart('/')
            if (normalizedName.isEmpty() || normalizedName.startsWith("/") || normalizedName.contains("..")) {
                throw IllegalArgumentException("Illegal zip entry path: $entryName")
            }
            val target = destDir.resolve(normalizedName).normalize()
            if (!target.startsWith(destDir)) {
                throw IllegalArgumentException("Illegal zip entry path: $entryName")
            }
            return target
        }
    }
}
