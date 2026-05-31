package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.dto.AttachmentResponse
import com.mdwiki.error.NotFoundException
import com.mdwiki.model.Attachment
import com.mdwiki.repository.AttachmentRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    private val wikiProperties: WikiProperties
) {

    private val uploadsDir: Path
        get() = Path.of(wikiProperties.contentDir).toAbsolutePath().normalize().resolve("uploads")

    @Transactional(readOnly = true)
    fun list(page: Int, size: Int, pageId: UUID?): List<AttachmentResponse> {
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
            pageId = pageId
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
        return persistUpload(
            originalName = originalName?.takeIf { it.isNotBlank() } ?: normalizedPath.fileName.toString(),
            contentType = contentType ?: Files.probeContentType(normalizedPath) ?: "application/octet-stream",
            sizeBytes = Files.size(normalizedPath),
            username = username,
            pageId = pageId
        ) { destination ->
            Files.copy(normalizedPath, destination, StandardCopyOption.REPLACE_EXISTING)
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
            pageId = pageId
        ) { destination ->
            Files.write(destination, bytes)
        }
    }

    private fun persistUpload(
        originalName: String,
        contentType: String,
        sizeBytes: Long,
        username: String,
        pageId: UUID?,
        writeToDestination: (Path) -> Unit
    ): AttachmentResponse {
        val user = userRepository.findByUsername(username)
        val linkedPage = pageId?.let {
            pageRepository.findById(it).orElseThrow { NotFoundException("Page not found: $it") }
        }
        val ext = originalName.substringAfterLast('.', "")
        val storedName = "${UUID.randomUUID()}${if (ext.isNotBlank()) ".$ext" else ""}"

        Files.createDirectories(uploadsDir)
        val dest = uploadsDir.resolve(storedName).normalize()
        require(dest.startsWith(uploadsDir)) { "Invalid upload path" }
        writeToDestination(dest)

        val attachment = attachmentRepository.save(Attachment(
            originalName = originalName,
            storedName = storedName,
            contentType = contentType,
            sizeBytes = sizeBytes,
            uploadedBy = user,
            page = linkedPage
        ))

        return attachment.toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        val attachment = attachmentRepository.findById(id)
            .orElseThrow { NotFoundException("Attachment not found") }
        val filePath = uploadsDir.resolve(attachment.storedName).normalize()
        if (filePath.startsWith(uploadsDir)) {
            Files.deleteIfExists(filePath)
        }
        attachmentRepository.delete(attachment)
    }

    private fun Attachment.toResponse() = AttachmentResponse(
        id = id!!,
        originalName = originalName,
        storedName = storedName,
        contentType = contentType,
        sizeBytes = sizeBytes,
        uploadedBy = uploadedBy?.username,
        pageId = page?.id,
        url = "/api/uploads/$storedName",
        createdAt = createdAt
    )
}
