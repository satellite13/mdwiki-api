package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.dto.AttachmentResponse
import com.mdwiki.error.NotFoundException
import com.mdwiki.model.Attachment
import com.mdwiki.repository.AttachmentRepository
import com.mdwiki.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.util.UUID

@Service
class AttachmentService(
    private val attachmentRepository: AttachmentRepository,
    private val userRepository: UserRepository,
    private val wikiProperties: WikiProperties
) {

    private val uploadsDir: File get() = File(wikiProperties.contentDir, "uploads").also { it.mkdirs() }

    fun list(page: Int, size: Int, pageId: UUID?): List<AttachmentResponse> {
        val pageable = PageRequest.of(page, size)
        val results = if (pageId != null) {
            attachmentRepository.findByPageId(pageId, pageable)
        } else {
            attachmentRepository.findAllBy(pageable)
        }
        return results.content.map { it.toResponse() }
    }

    fun upload(file: MultipartFile, username: String, pageId: UUID?): AttachmentResponse {
        val user = userRepository.findByUsername(username)
        val ext = file.originalFilename?.substringAfterLast('.', "") ?: ""
        val storedName = "${UUID.randomUUID()}${if (ext.isNotBlank()) ".$ext" else ""}"

        val dest = File(uploadsDir, storedName)
        file.transferTo(dest)

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

    fun delete(id: UUID) {
        val attachment = attachmentRepository.findById(id)
            .orElseThrow { NotFoundException("Attachment not found") }
        val file = File(uploadsDir, attachment.storedName)
        file.delete()
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
