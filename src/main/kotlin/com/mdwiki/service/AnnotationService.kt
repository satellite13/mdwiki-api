package com.mdwiki.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.mdwiki.config.WikiProperties
import com.mdwiki.dto.AnnotationResponse
import com.mdwiki.dto.CreateAnnotationRequest
import com.mdwiki.dto.UpdateAnnotationRequest
import com.mdwiki.error.NotFoundException
import com.mdwiki.mapper.toResponse
import com.mdwiki.model.Annotation
import com.mdwiki.repository.AnnotationRepository
import com.mdwiki.repository.PageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

@Service
class AnnotationService(
    private val annotationRepository: AnnotationRepository,
    private val pageRepository: PageRepository,
    private val wikiProperties: WikiProperties,
    private val folderAccessPolicy: FolderAccessPolicy
) {
    private val yamlMapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule.Builder().build())

    private val annotationsDir: Path
        get() = Path.of(wikiProperties.contentDir).toAbsolutePath().normalize().resolve(".mdwiki/annotations")

    @Transactional(readOnly = true)
    fun listBySlug(slug: String, requestingUsername: String): List<AnnotationResponse> {
        // Shared-wiki reads intentionally remain global.
        val page = pageRepository.findBySlug(slug) ?: throw NotFoundException("Page not found: $slug")
        return annotationRepository.findByPageId(page.id!!).map { it.toResponse() }
    }

    @Transactional
    fun create(slug: String, request: CreateAnnotationRequest, username: String): AnnotationResponse {
        val page = pageRepository.findBySlug(slug) ?: throw NotFoundException("Page not found: $slug")
        page.folder?.let { folderAccessPolicy.requireAccess(it, username) }
        val annotation = annotationRepository.save(Annotation(
            pageId = page.id!!,
            highlightedText = request.highlightedText,
            anchorContext = request.anchorContext,
            comment = request.comment,
            rangeStart = request.rangeStart,
            rangeEnd = request.rangeEnd,
            color = request.color,
            createdBy = username
        ))
        syncYaml(slug)
        return annotation.toResponse()
    }

    @Transactional
    fun update(id: UUID, request: UpdateAnnotationRequest, username: String): AnnotationResponse {
        val annotation = annotationRepository.findById(id)
            .orElseThrow { NotFoundException("Annotation not found: $id") }
        val page = pageRepository.findById(annotation.pageId)
            .orElseThrow { NotFoundException("Page not found") }
        page.folder?.let { folderAccessPolicy.requireAccess(it, username) }
        when {
            request.clearComment == true -> annotation.comment = null
            request.comment != null -> annotation.comment = request.comment
        }
        when {
            request.clearColor == true -> annotation.color = null
            request.color != null -> annotation.color = request.color
        }
        annotation.updatedAt = Instant.now()
        annotationRepository.save(annotation)
        syncYaml(page.slug)
        return annotation.toResponse()
    }

    @Transactional
    fun delete(id: UUID, username: String) {
        val annotation = annotationRepository.findById(id)
            .orElseThrow { NotFoundException("Annotation not found: $id") }
        val page = pageRepository.findById(annotation.pageId)
            .orElseThrow { NotFoundException("Page not found") }
        page.folder?.let { folderAccessPolicy.requireAccess(it, username) }
        annotationRepository.delete(annotation)
        syncYaml(page.slug)
    }

    private fun syncYaml(slug: String) {
        val annotations = annotationRepository.findByPageId(
            pageRepository.findBySlug(slug)?.id ?: return
        )
        val yamlFile = annotationsDir.resolve("$slug.yaml")
        Files.createDirectories(yamlFile.parent)
        val data = mapOf("annotations" to annotations.map { it.toYamlMap() })
        yamlMapper.writeValue(yamlFile.toFile(), data)
    }

    private fun Annotation.toYamlMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        map["id"] = id.toString()
        map["highlightedText"] = highlightedText
        map["anchorContext"] = anchorContext
        comment?.let { map["comment"] = it }
        rangeStart?.let { map["rangeStart"] = it }
        rangeEnd?.let { map["rangeEnd"] = it }
        color?.let { map["color"] = it }
        map["createdBy"] = createdBy
        map["createdAt"] = createdAt.toString()
        return map
    }
}
