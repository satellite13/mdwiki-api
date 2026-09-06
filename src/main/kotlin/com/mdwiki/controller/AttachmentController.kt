package com.mdwiki.controller

import com.mdwiki.dto.AttachmentResponse
import com.mdwiki.service.AttachmentService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/attachments")
class AttachmentController(private val attachmentService: AttachmentService) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) pageId: UUID?,
        auth: Authentication
    ): List<AttachmentResponse> {
        return attachmentService.list(page, size, pageId, null, auth.name).content
    }

    @PostMapping
    fun upload(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(required = false) pageId: UUID?,
        auth: Authentication
    ): AttachmentResponse {
        return attachmentService.upload(file, auth.name, pageId)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID, auth: Authentication) {
        attachmentService.delete(id, auth.name)
    }
}
