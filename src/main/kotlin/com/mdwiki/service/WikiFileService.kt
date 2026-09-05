package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.model.Folder
import com.mdwiki.model.Page
import com.mdwiki.repository.FolderRepository
import com.mdwiki.util.PathSanitizer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Service
class WikiFileService(
    private val wikiProperties: WikiProperties,
    private val fileWatcherService: FileWatcherService,
    private val folderRepository: FolderRepository
) {
    private val log = LoggerFactory.getLogger(WikiFileService::class.java)

    companion object {
        const val TRASH_DIR_NAME = ".trash"
    }

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
        page.filePath = targetFile.absolutePath
        runAfterCommit {
            fileWatcherService.ignoreNextChange(targetFile.absolutePath)
            targetFile.writeText(content)
        }
    }

    /**
     * Планирует одну итоговую файловую операцию после commit, учитывая одновременно новый slug и folder.
     * До commit меняется только persisted filePath; содержимое файлов и каталоги не затрагиваются.
     */
    fun schedulePageFileUpdate(
        page: Page,
        previousSlug: String,
        previousFolder: Folder?,
        previousFilePath: String?,
        content: String
    ) {
        val sourceFile = previousFilePath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: File(resolveFolderDirectory(previousFolder), "$previousSlug.md")
        val targetFile = File(resolveFolderDirectory(page.folder), "${page.slug}.md")
        val samePath = sourceFile.absoluteFile.normalize() == targetFile.absoluteFile.normalize()
        if (!samePath && targetFile.exists()) {
            throw IllegalStateException("Cannot update page file: target already exists: ${targetFile.absolutePath}")
        }
        page.filePath = targetFile.absolutePath
        runAfterCommit {
            if (samePath) {
                targetFile.parentFile?.mkdirs()
                fileWatcherService.ignoreNextChange(targetFile.absolutePath)
                targetFile.writeText(content)
            } else {
                writeRenamedFile(sourceFile, targetFile, content)
            }
        }
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
    fun renamePageFileToSlug(page: Page, newSlug: String, content: String = page.contentMd ?: "") {
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
        if (sourceFile?.absolutePath != targetFile.absolutePath && targetFile.exists()) {
            throw IllegalStateException("Cannot rename page file: target already exists: ${targetFile.absolutePath}")
        }
        val renameOperation = {
            try {
                writeRenamedFile(sourceFile, targetFile, content)
            } catch (error: Exception) {
                throw IllegalStateException(
                    "Page slug was committed but markdown file rename failed; source was preserved: ${targetFile.absolutePath}",
                    error
                )
            }
        }

        page.slug = newSlug
        page.filePath = targetFile.absolutePath
        runAfterCommit(renameOperation)
    }

    fun deletePageFile(page: Page) {
        val sourcePath = page.filePath ?: return
        val sourceFile = File(sourcePath)
        if (sourceFile.exists()) {
            fileWatcherService.ignoreNextChange(sourceFile.absolutePath)
            sourceFile.delete()
        }
    }

    /** Корзина файлов: `<contentDir>/.trash/<slug>.md`. Исключена из sync и file-watcher. */
    fun trashDir(): File = File(contentRoot(), TRASH_DIR_NAME)

    /** true, если путь лежит внутри корзины. */
    fun isTrashPath(file: File): Boolean =
        file.toPath().toAbsolutePath().normalize()
            .startsWith(trashDir().toPath().toAbsolutePath().normalize())

    /**
     * Перемещает файл страницы в корзину (soft-delete) и обновляет `page.filePath`.
     * @return false, если файла на диске не было (страница без файла — тоже валидный случай).
     */
    fun movePageFileToTrash(page: Page): Boolean {
        val sourcePath = page.filePath ?: return false
        val sourceFile = File(sourcePath)
        if (!sourceFile.isFile) return false
        val targetFile = File(trashDir(), "${page.slug}.md")
        targetFile.parentFile?.mkdirs()
        fileWatcherService.ignoreNextChange(sourceFile.absolutePath)
        fileWatcherService.ignoreNextChange(targetFile.absolutePath)
        Files.move(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        page.filePath = targetFile.absolutePath
        return true
    }

    /**
     * Возвращает файл страницы из корзины на место (restore) и обновляет `page.filePath`.
     * Если целевой файл уже существует (кто-то положил новый вручную) — не затираем его:
     * строка всё равно воскреснет, а sync подтянет контент из файла.
     * @return false, если файла в корзине нет.
     */
    fun restorePageFileFromTrash(page: Page): Boolean {
        val sourceFile = File(trashDir(), "${page.slug}.md")
        if (!sourceFile.isFile) return false
        val targetFile = resolvePageFile(page.slug, page.folder)
        if (targetFile.exists()) {
            log.warn(
                "restorePageFileFromTrash: target '{}' already exists; keeping it, trash copy left in place",
                targetFile.absolutePath
            )
            page.filePath = targetFile.absolutePath
            return false
        }
        fileWatcherService.ignoreNextChange(sourceFile.absolutePath)
        fileWatcherService.ignoreNextChange(targetFile.absolutePath)
        Files.move(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        page.filePath = targetFile.absolutePath
        return true
    }

    /** Первый `$slug.md` под корнем контента (как при синхронизации). */
    fun findMarkdownFileForSlug(slug: String): File? {
        val root = contentRoot()
        if (!root.exists()) return null
        val name = "$slug.md"
        // NIO Files.walk — как в WikiSyncEngine.collectMarkdownFiles:
        // File.walkTopDown() на части JVM слеп к не-ASCII (кириллическим) путям.
        // Корзина (.trash) исключается: страница в корзине не должна «воскресать» при GET.
        return try {
            Files.walk(root.toPath()).use { stream ->
                stream
                    .filter { !isTrashPath(it.toFile()) }
                    .filter { Files.isRegularFile(it) && it.fileName?.toString() == name }
                    .findFirst()
                    .map { it.toFile() }
                    .orElse(null)
            }
        } catch (e: Exception) {
            log.warn("findMarkdownFileForSlug: walk failed for slug '{}': {}", slug, e.message)
            null
        }
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
        // NIO Files.walk — см. findMarkdownFileForSlug (walkTopDown слеп к не-ASCII путям).
        try {
            Files.walk(oldDir.toPath()).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && it.fileName?.toString()?.endsWith(".md") == true }
                    .forEach { path ->
                        val sourceFile = path.toFile()
                        val relative = sourceFile.relativeTo(oldDir).path
                        val targetFile = File(newDir, relative)
                        fileWatcherService.ignoreNextChange(sourceFile.absolutePath)
                        fileWatcherService.ignoreNextChange(targetFile.absolutePath)
                    }
            }
        } catch (e: Exception) {
            log.warn("moveFolderDirectory: walk failed for '{}': {}", oldDir.absolutePath, e.message)
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

    /**
     * Публикует новый файл без overwrite и удаляет source только после успешной записи.
     * При ошибке удаления source новый target удаляется как компенсация.
     */
    private fun writeRenamedFile(sourceFile: File?, targetFile: File, content: String) {
        if (sourceFile?.absolutePath == targetFile.absolutePath) {
            fileWatcherService.ignoreNextChange(targetFile.absolutePath)
            targetFile.writeText(content)
            return
        }
        if (targetFile.exists()) {
            throw IllegalStateException("Cannot rename page file: target already exists: ${targetFile.absolutePath}")
        }
        targetFile.parentFile?.mkdirs()
        val tempFile = File.createTempFile(".${targetFile.name}.", ".tmp", targetFile.parentFile)
        try {
            tempFile.writeText(content)
            fileWatcherService.ignoreNextChange(targetFile.absolutePath)
            Files.move(tempFile.toPath(), targetFile.toPath())
            if (sourceFile != null && sourceFile.exists()) {
                fileWatcherService.ignoreNextChange(sourceFile.absolutePath)
                if (!sourceFile.delete()) {
                    targetFile.delete()
                    throw IllegalStateException("Cannot delete source file: ${sourceFile.absolutePath}")
                }
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun runAfterCommit(operation: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            operation()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                operation()
            }
        })
    }
}
