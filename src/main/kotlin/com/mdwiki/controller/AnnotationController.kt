package com.mdwiki.controller

import com.mdwiki.dto.AnnotationResponse
import com.mdwiki.dto.CreateAnnotationRequest
import com.mdwiki.dto.UpdateAnnotationRequest
import com.mdwiki.service.AnnotationService
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
class AnnotationController(private val annotationService: AnnotationService) {

    @GetMapping("/api/pages/{slug}/annotations")
    fun listBySlug(@PathVariable slug: String): List<AnnotationResponse> {
        return annotationService.listBySlug(slug)
    }

    @PostMapping("/api/pages/{slug}/annotations")
    fun create(
        @PathVariable slug: String,
        @Valid @RequestBody request: CreateAnnotationRequest,
        auth: Authentication
    ): AnnotationResponse {
        return annotationService.create(slug, request, auth.name)
    }

    @PutMapping("/api/annotations/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateAnnotationRequest
    ): AnnotationResponse {
        return annotationService.update(id, request)
    }

    @DeleteMapping("/api/annotations/{id}")
    fun delete(@PathVariable id: UUID) {
        annotationService.delete(id)
    }
}
