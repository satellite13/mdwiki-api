package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.dto.AttachmentResponse
import com.mdwiki.error.NotFoundException
import com.mdwiki.model.Attachment
import com.mdwiki.repository.AttachmentRepository
import com.mdwiki.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

@Service
class AttachmentService(
    private val attachmentRepository: AttachmentRepository,
    private val userRepository: UserRepository,
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
        val user = userRepository.findByUsername(username)
        val ext = file.originalFilename?.substringAfterLast('.', "") ?: ""
        val storedName = "${UUID.randomUUID()}${if (ext.isNotBlank()) ".$ext" else ""}"

        Files.createDirectories(uploadsDir)
        val dest = uploadsDir.resolve(storedName).normalize()
        require(dest.startsWith(uploadsDir)) { "Invalid upload path" }
        file.inputStream.use { input ->
            Files.copy(input, dest, StandardCopyOption.REPLACE_EXISTING)
        }

        val attachment = attachmentRepository.save(Attachment(
            originalName = file.originalFilename ?: "unknown",
            storedName = storedName,
            contentType = file.contentType ?: "application/octet-stream",
            sizeBytes = file.size,
            uploadedBy = user,
            page = null // pageId association can be done later
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
