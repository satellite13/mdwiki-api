package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.model.Folder
import com.mdwiki.model.Page
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Service
class WikiFileService(
    private val wikiProperties: WikiProperties,
    private val fileWatcherService: FileWatcherService
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
        val targetFile = resolvePageFile(page.slug, page.folder)
        targetFile.parentFile?.mkdirs()
        fileWatcherService.ignoreNextChange(targetFile.absolutePath)
        targetFile.writeText(content)
        page.filePath = targetFile.absolutePath
    }

    fun relocatePageFile(page: Page, targetFolder: Folder?) {
        val targetFile = resolvePageFile(page.slug, targetFolder)
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
        Files.move(
            sourceFile.toPath(),
            targetFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
        page.filePath = targetFile.absolutePath
        deleteEmptyAncestors(sourceFile.parentFile, contentRoot())
    }

    fun deletePageFile(page: Page) {
        val sourcePath = page.filePath ?: return
        val sourceFile = File(sourcePath)
        if (sourceFile.exists()) {
            fileWatcherService.ignoreNextChange(sourceFile.absolutePath)
            sourceFile.delete()
            deleteEmptyAncestors(sourceFile.parentFile, contentRoot())
        }
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

    private fun buildFolderSegments(folder: Folder): List<String> {
        val segments = mutableListOf<String>()
        var current: Folder? = folder
        while (current != null) {
            segments.add(sanitizePathSegment(current.name))
            current = current.parent
        }
        return segments.reversed()
    }

    private fun sanitizePathSegment(input: String): String {
        val cleaned = input
            .trim()
            .replace('/', '-')
            .replace('\\', '-')
        return if (cleaned.isBlank()) "folder" else cleaned
    }

    private fun deleteEmptyAncestors(start: File?, root: File) {
        var current = start
        val rootPath = root.toPath().normalize()
        while (current != null) {
            val currentPath: Path = current.toPath().normalize()
            if (currentPath == rootPath) {
                return
            }
            val children = current.listFiles()
            if (children != null && children.isNotEmpty()) {
                return
            }
            if (!current.delete()) {
                return
            }
            current = current.parentFile
        }
    }
}
