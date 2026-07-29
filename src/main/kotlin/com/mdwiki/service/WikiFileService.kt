package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.model.Folder
import com.mdwiki.model.Page
import com.mdwiki.repository.FolderRepository
import com.mdwiki.util.PathSanitizer
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Service
class WikiFileService(
    private val wikiProperties: WikiProperties,
    private val fileWatcherService: FileWatcherService,
    private val folderRepository: FolderRepository
) {
    fun contentRoot(): File = File(wikiProperties.contentDir).also { it.mkdirs() }

    fun resolveFolderDirectory(folder: Folder?): File {
        if (folder == null) return contentRoot()
        val segments = buildFolderSegments(folder)
        var current = contentRoot()
        for (segment in segments) {
            current = File(current, segment)
        }
        return current
    }

    fun ensureFolderDirectory(folder: Folder?): File {
        val dir = resolveFolderDirectory(folder)
        dir.mkdirs()
        return dir
    }

    fun resolvePageFile(slug: String, folder: Folder?): File {
        val dir = ensureFolderDirectory(folder)
        return File(dir, "$slug.md")
    }

    fun createOrRewritePageFile(page: Page, content: String) {
        val targetFile = resolveTargetMarkdownFile(page)
        targetFile.parentFile?.mkdirs()
        fileWatcherService.ignoreNextChange(targetFile.absolutePath)
        targetFile.writeText(content)
        page.filePath = targetFile.absolutePath
    }

    fun relocatePageFile(page: Page, targetFolder: Folder?) {
        val resolvedTarget = resolveFolderForFileOps(targetFolder)
        if (resolvedTarget != null) {
            touchFolderChain(resolvedTarget)
        }
        val targetFile = resolvePageFile(page.slug, resolvedTarget)
        val sourcePath = page.filePath
        if (sourcePath.isNullOrBlank()) {
            targetFile.parentFile?.mkdirs()
            if (!targetFile.exists()) {
                fileWatcherService.ignoreNextChange(targetFile.absolutePath)
                targetFile.writeText(page.contentMd ?: "")
            }
            page.filePath = targetFile.absolutePath
            return
        }

        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) {
            targetFile.parentFile?.mkdirs()
            fileWatcherService.ignoreNextChange(targetFile.absolutePath)
            targetFile.writeText(page.contentMd ?: "")
            page.filePath = targetFile.absolutePath
            return
        }

        if (sourceFile.absolutePath == targetFile.absolutePath) {
            page.filePath = targetFile.absolutePath
            return
        }

        targetFile.parentFile?.mkdirs()
        fileWatcherService.ignoreNextChange(sourceFile.absolutePath)
        fileWatcherService.ignoreNextChange(targetFile.absolutePath)
        moveFile(sourceFile, targetFile)
        page.filePath = targetFile.absolutePath
    }

    /**
     * Переименовывает markdown-файл страницы под новый slug (тот же каталог папки), обновляет [page.slug] и [page.filePath].
     * Сначала опирается на [Page.filePath], чтобы не трогать ленивую цепочку [Folder] (иначе возможен LazyInitializationException).
     */
    fun renamePageFileToSlug(page: Page, newSlug: String) {
        if (page.slug == newSlug) return

        val sourceFromPath = page.filePath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
        val sourceFile = sourceFromPath ?: run {
            val folderForOps = resolveFolderForFileOps(page.folder)
            if (folderForOps != null) {
                touchFolderChain(folderForOps)
                resolvePageFile(page.slug, folderForOps).takeIf { it.exists() }
            } else {
                resolvePageFile(page.slug, null).takeIf { it.exists() }
            }
        }

        val targetFile = when {
            sourceFile != null && sourceFile.exists() ->
                File(sourceFile.parentFile!!, "$newSlug.md")
            else -> {
                val folderForOps = resolveFolderForFileOps(page.folder)
                if (folderForOps == null) {
                    File(contentRoot(), "$newSlug.md")
                } else {
                    touchFolderChain(folderForOps)
                    resolvePageFile(newSlug, folderForOps)
                }
            }
        }

        targetFile.parentFile?.mkdirs()
        if (sourceFile != null && sourceFile.exists()) {
            if (sourceFile.absolutePath != targetFile.absolutePath) {
                fileWatcherService.ignoreNextChange(sourceFile.absolutePath)
                fileWatcherService.ignoreNextChange(targetFile.absolutePath)
                moveFile(sourceFile, targetFile)
            }
        } else {
            fileWatcherService.ignoreNextChange(targetFile.absolutePath)
            if (!targetFile.exists()) {
                targetFile.writeText(page.contentMd ?: "")
            }
        }
        page.slug = newSlug
        page.filePath = targetFile.absolutePath
    }

    fun deletePageFile(page: Page) {
        val sourcePath = page.filePath ?: return
        val sourceFile = File(sourcePath)
        if (sourceFile.exists()) {
            fileWatcherService.ignoreNextChange(sourceFile.absolutePath)
            sourceFile.delete()
        }
    }

    /** Первый `$slug.md` под корнем контента (как при синхронизации). */
    fun findMarkdownFileForSlug(slug: String): File? {
        val root = contentRoot()
        if (!root.exists()) return null
        val name = "$slug.md"
        return root.walkTopDown()
            .firstOrNull { it.isFile && it.name == name }
    }

    private fun isUnderContentRoot(file: File): Boolean =
        try {
            val root = contentRoot().canonicalFile.toPath()
            val target = file.canonicalFile.toPath()
            target.startsWith(root)
        } catch (_: Exception) {
            false
        }

    /**
     * Удаляет markdown-файл, если он лежит внутри content dir (орфан без строки в БД).
     * @return true, если файл был и удалён
     */
    fun deleteOrphanMarkdownIfExists(file: File): Boolean {
        if (!file.isFile || !file.name.endsWith(".md", ignoreCase = true)) return false
        if (!isUnderContentRoot(file)) return false
        fileWatcherService.ignoreNextChange(file.absolutePath)
        return file.delete()
    }

    fun moveFolderDirectory(oldDir: File, newDir: File) {
        if (oldDir.absolutePath == newDir.absolutePath) return
        if (!oldDir.exists()) {
            newDir.mkdirs()
            return
        }
        oldDir.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .forEach { sourceFile ->
                val relative = sourceFile.relativeTo(oldDir).path
                val targetFile = File(newDir, relative)
                fileWatcherService.ignoreNextChange(sourceFile.absolutePath)
                fileWatcherService.ignoreNextChange(targetFile.absolutePath)
            }
        newDir.parentFile?.mkdirs()
        Files.move(oldDir.toPath(), newDir.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    fun deleteFolderDirectory(folder: Folder) {
        val dir = resolveFolderDirectory(folder)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    /**
     * Целевой `.md` для записи: при известном [Page.filePath] не трогаем ленивый [Page.folder];
     * иначе подгружаем папку по id из [FolderRepository] в текущей сессии.
     */
    private fun resolveTargetMarkdownFile(page: Page): File {
        val fp = page.filePath?.trim().orEmpty()
        if (fp.isNotEmpty()) {
            val parent = File(fp).parentFile
            if (parent != null) {
                return File(parent, "${page.slug}.md")
            }
        }
        val loaded = resolveFolderForFileOps(page.folder)
        if (loaded != null) {
            touchFolderChain(loaded)
            return resolvePageFile(page.slug, loaded)
        }
        return resolvePageFile(page.slug, null)
    }

    /** Подменяет возможный отсоединённый Hibernate-proxy на сущность из persistence context. */
    private fun resolveFolderForFileOps(folder: Folder?): Folder? {
        val id = folder?.id ?: return null
        return folderRepository.findById(id).orElse(null)
    }

    /**
     * Обходит parent-цепочку, чтобы Hibernate подгрузил [Folder] до вызовов [buildFolderSegments]
     * (иначе после частичных flush в той же транзакции возможен LazyInitializationException).
     */
    private fun touchFolderChain(folder: Folder?) {
        var current = folder ?: return
        while (true) {
            current.name
            current = current.parent ?: break
        }
    }

    private fun buildFolderSegments(folder: Folder): List<String> {
        val segments = mutableListOf<String>()
        var current: Folder? = folder
        while (current != null) {
            segments.add(PathSanitizer.sanitizePathSegment(current.name))
            current = current.parent
        }
        return segments.reversed()
    }

    /**
     * File-уровневый move без Path/Files.toPath(): на части окружений Path.encode
     * падает на не-ASCII пути (например, кириллица в имени папки).
     */
    private fun moveFile(sourceFile: File, targetFile: File) {
        if (targetFile.exists() && !targetFile.delete()) {
            throw IllegalStateException("Cannot replace existing file: ${targetFile.absolutePath}")
        }
        targetFile.parentFile?.mkdirs()
        if (sourceFile.renameTo(targetFile)) {
            return
        }
        sourceFile.copyTo(targetFile, overwrite = true)
        if (!sourceFile.delete()) {
            throw IllegalStateException("Cannot delete source file after copy: ${sourceFile.absolutePath}")
        }
    }
}
