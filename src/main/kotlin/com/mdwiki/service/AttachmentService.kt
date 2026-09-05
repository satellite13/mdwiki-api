package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.dto.AttachmentResponse
import com.mdwiki.error.NotFoundException
import com.mdwiki.mapper.toResponse
import com.mdwiki.model.Attachment
import com.mdwiki.repository.AttachmentRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.UUID

@Service
class AttachmentService(
    private val attachmentRepository: AttachmentRepository,
    private val userRepository: UserRepository,
    private val pageRepository: PageRepository,
    private val wikiProperties: WikiProperties,
    private val folderAccessPolicy: FolderAccessPolicy
) {

    private val log = LoggerFactory.getLogger(AttachmentService::class.java)

    data class AttachmentSyncResult(val added: Int)

    private val uploadRefPattern = Regex("""/api/uploads/([^)\s"'<>]+)""")

    // Типы, исполняемые браузером при отдаче с permitAll-endpoint'а — stored XSS
    private val blockedContentTypes = setOf("image/svg+xml", "text/html", "application/xhtml+xml")

    private val uploadsDir: Path
        get() = Path.of(wikiProperties.contentDir).toAbsolutePath().normalize().resolve("uploads")

    /**
     * Регистрирует файлы из `uploads/`, которых нет в таблице `attachments`.
     * Нужно после очистки БД: файлы на PVC остаются, а markdown-ссылки на них работают,
     * но список вложений в UI пуст.
     */
    @Transactional
    fun syncFromDisk(): AttachmentSyncResult {
        val dir = uploadsDir
        if (!Files.isDirectory(dir)) {
            return AttachmentSyncResult(0)
        }

        val existingNames = attachmentRepository.findAll().map { it.storedName }.toSet()
        val pageByStoredName = findPageLinksForUploads()
        var added = 0

        Files.list(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach { path ->
                val storedName = path.fileName.toString()
                if (storedName in existingNames) return@forEach

                val linkedPage = pageByStoredName[storedName]?.let { pageId ->
                    pageRepository.findById(pageId).orElse(null)
                }

                attachmentRepository.save(
                    Attachment(
                        originalName = storedName,
                        storedName = storedName,
                        contentType = Files.probeContentType(path) ?: guessContentType(storedName),
                        sizeBytes = Files.size(path),
                        uploadedBy = null,
                        page = linkedPage
                    )
                )
                added++
            }
        }

        if (added > 0) {
            log.info("Attachment sync: registered {} file(s) from disk", added)
        }
        return AttachmentSyncResult(added)
    }

    private fun findPageLinksForUploads(): Map<String, UUID> {
        val result = linkedMapOf<String, UUID>()
        for (page in pageRepository.findAllByDeletedAtIsNull()) {
            val content = page.contentMd ?: continue
            val pageId = page.id ?: continue
            for (match in uploadRefPattern.findAll(content)) {
                val storedName = match.groupValues[1].trim().trimStart('/')
                if (storedName.isNotEmpty()) {
                    result.putIfAbsent(storedName, pageId)
                }
            }
        }
        return result
    }

    private fun guessContentType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }

    @Transactional(readOnly = true)
    fun list(page: Int, size: Int, pageId: UUID?, requestingUsername: String): List<AttachmentResponse> {
        // Shared-wiki reads intentionally remain global; actor is propagated for a stable contract.
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val results = if (pageId != null) {
            attachmentRepository.findByPageId(pageId, pageable)
        } else {
            attachmentRepository.findAll(pageable)
        }
        return results.content.map { it.toResponse() }
    }

    @Transactional
    fun upload(file: MultipartFile, username: String, pageId: UUID?): AttachmentResponse {
        if (file.isEmpty) {
            throw IllegalArgumentException("Uploaded file is empty")
        }
        val originalName = file.originalFilename?.takeIf { it.isNotBlank() } ?: "unknown"
        val contentType = file.contentType ?: "application/octet-stream"
        return persistUpload(
            originalName = originalName,
            contentType = contentType,
            sizeBytes = file.size,
            username = username,
            pageId = pageId,
            enforceMaxSize = true
        ) { destination ->
            file.inputStream.use { input ->
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    @Transactional
    fun uploadFromFile(
        filePath: Path,
        username: String,
        pageId: UUID?,
        originalName: String? = null,
        contentType: String? = null
    ): AttachmentResponse {
        val normalizedPath = filePath.toAbsolutePath().normalize()
        if (!Files.exists(normalizedPath) || !Files.isRegularFile(normalizedPath)) {
            throw IllegalArgumentException("File does not exist: $normalizedPath")
        }
        // Защита от LFI: импорт по пути разрешён только из явно настроенных директорий
        val realPath = normalizedPath.toRealPath()
        requireImportPathAllowed(realPath)
        return persistUpload(
            originalName = originalName?.takeIf { it.isNotBlank() } ?: normalizedPath.fileName.toString(),
            contentType = contentType ?: Files.probeContentType(normalizedPath) ?: "application/octet-stream",
            sizeBytes = Files.size(normalizedPath),
            username = username,
            pageId = pageId,
            enforceMaxSize = true
        ) { destination ->
            Files.copy(normalizedPath, destination, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun requireImportPathAllowed(realPath: Path) {
        val allowedDirs = wikiProperties.attachments.allowedImportDirs
        if (allowedDirs.isEmpty()) {
            throw IllegalArgumentException(
                "Import from host path is disabled: configure mdwiki.attachments.allowed-import-dirs"
            )
        }
        val insideAllowed = allowedDirs.any { dir ->
            val allowedDir = Path.of(dir).toAbsolutePath().normalize()
            Files.isDirectory(allowedDir) && realPath.startsWith(allowedDir.toRealPath())
        }
        if (!insideAllowed) {
            throw IllegalArgumentException("File path is outside allowed import directories: $realPath")
        }
    }

    @Transactional
    fun uploadFromBase64(
        base64Data: String,
        filename: String,
        username: String,
        pageId: UUID?,
        contentType: String? = null
    ): AttachmentResponse {
        val normalizedBase64 = base64Data.substringAfter("base64,", base64Data).trim()
        val bytes = try {
            Base64.getDecoder().decode(normalizedBase64)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid base64 payload")
        }
        if (bytes.isEmpty()) {
            throw IllegalArgumentException("Uploaded file is empty")
        }
        return persistUpload(
            originalName = filename.ifBlank { "unknown" },
            contentType = contentType ?: "application/octet-stream",
            sizeBytes = bytes.size.toLong(),
            username = username,
            pageId = pageId,
            enforceMaxSize = true
        ) { destination ->
            Files.write(destination, bytes)
        }
    }

    /**
     * Upload from a path that the server already trusts (unpacked bundle, not user-supplied host path).
     * Skips allowed-import-dirs and the regular 20MB attachment cap — bundle size is checked separately.
     */
    @Transactional
    fun uploadFromTrustedPath(
        filePath: Path,
        username: String,
        pageId: UUID?,
        originalName: String,
        contentType: String
    ): AttachmentResponse {
        val normalizedPath = filePath.toAbsolutePath().normalize()
        if (!Files.exists(normalizedPath) || !Files.isRegularFile(normalizedPath)) {
            throw IllegalArgumentException("File does not exist: $normalizedPath")
        }
        return persistUpload(
            originalName = originalName.ifBlank { normalizedPath.fileName.toString() },
            contentType = contentType.ifBlank { Files.probeContentType(normalizedPath) ?: "application/octet-stream" },
            sizeBytes = Files.size(normalizedPath),
            username = username,
            pageId = pageId,
            enforceMaxSize = false
        ) { destination ->
            Files.copy(normalizedPath, destination, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun persistUpload(
        originalName: String,
        contentType: String,
        sizeBytes: Long,
        username: String,
        pageId: UUID?,
        enforceMaxSize: Boolean = true,
        writeToDestination: (Path) -> Unit
    ): AttachmentResponse {
        if (enforceMaxSize && sizeBytes > MAX_ATTACHMENT_BYTES) {
            throw IllegalArgumentException("File exceeds max attachment size of 20MB")
        }
        val normalizedContentType = contentType.substringBefore(';').trim().lowercase()
        if (normalizedContentType in blockedContentTypes) {
            throw IllegalArgumentException("Files of type '$contentType' are not allowed")
        }
        val user = userRepository.findByUsername(username)
        val linkedPage = pageId?.let {
            pageRepository.findById(it).orElseThrow { NotFoundException("Page not found: $it") }
                .also { page -> page.folder?.let { folderAccessPolicy.requireAccess(it, username) } }
        }
        val ext = originalName.substringAfterLast('.', "")
        val storedName = "${UUID.randomUUID()}${if (ext.isNotBlank()) ".$ext" else ""}"

        Files.createDirectories(uploadsDir)
        val dest = uploadsDir.resolve(storedName).normalize()
        require(dest.startsWith(uploadsDir)) { "Invalid upload path" }
        writeToDestination(dest)
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        runCatching { Files.deleteIfExists(dest) }
                    }
                }
            })
        }
        return try {
            val attachment = attachmentRepository.save(Attachment(
                originalName = originalName,
                storedName = storedName,
                contentType = contentType,
                sizeBytes = sizeBytes,
                uploadedBy = user,
                page = linkedPage
            ))
            attachmentRepository.flush()
            attachment.toResponse()
        } catch (error: Exception) {
            Files.deleteIfExists(dest)
            throw error
        }
    }

    @Transactional
    fun delete(id: UUID, username: String) {
        val attachment = attachmentRepository.findById(id)
            .orElseThrow { NotFoundException("Attachment not found") }
        attachment.page?.folder?.let { folderAccessPolicy.requireAccess(it, username) }
        deletePreAuthorized(attachment)
    }

    @Transactional
    internal fun deletePreAuthorized(id: UUID) {
        val attachment = attachmentRepository.findById(id)
            .orElseThrow { NotFoundException("Attachment not found") }
        deletePreAuthorized(attachment)
    }

    @Transactional
    internal fun linkPreAuthorized(id: UUID, pageId: UUID): AttachmentResponse {
        val attachment = attachmentRepository.findById(id)
            .orElseThrow { NotFoundException("Attachment not found") }
        attachment.page = pageRepository.findById(pageId)
            .orElseThrow { NotFoundException("Page not found: $pageId") }
        return attachmentRepository.save(attachment).toResponse()
    }

    private fun deletePreAuthorized(attachment: Attachment) {
        val filePath = uploadsDir.resolve(attachment.storedName).normalize()
        if (filePath.startsWith(uploadsDir)) {
            Files.deleteIfExists(filePath)
        }
        attachmentRepository.delete(attachment)
    }

    companion object {
        const val MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024
    }
}
