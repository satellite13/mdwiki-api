package com.mdwiki.controller

import com.mdwiki.config.WikiProperties
import com.mdwiki.error.NotFoundException
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import org.springframework.http.ResponseEntity
import org.springframework.util.StringUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

data class UploadResponse(
    val url: String
)

@RestController
@RequestMapping("/api/uploads")
class UploadController(
    private val wikiProperties: WikiProperties
) {
    private val uploadsDir: Path
        get() = Path.of(wikiProperties.contentDir).toAbsolutePath().normalize().resolve("uploads")

    @PostMapping
    fun upload(@RequestParam("file") file: MultipartFile): UploadResponse {
        if (file.isEmpty) {
            throw IllegalArgumentException("Uploaded file is empty")
        }
        val contentType = file.contentType?.lowercase()
        if (contentType == null || !contentType.startsWith("image/")) {
            throw IllegalArgumentException("Only image files are allowed")
        }

        Files.createDirectories(uploadsDir)

        val originalName = StringUtils.cleanPath(file.originalFilename ?: "file")
        val extension = originalName.substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.isNotBlank() && it.all { char -> char.isLetterOrDigit() } }
        val storedName = buildString {
            append(UUID.randomUUID())
            if (extension != null) {
                append(".")
                append(extension)
            }
        }

        val targetPath = uploadsDir.resolve(storedName).normalize()
        file.inputStream.use { input ->
            Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING)
        }

        return UploadResponse(url = "/api/uploads/$storedName")
    }

    @GetMapping("/{fileName:.+}")
    fun getFile(@PathVariable fileName: String): ResponseEntity<Resource> {
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw NotFoundException("File not found")
        }

        val filePath = uploadsDir.resolve(fileName).normalize()
        if (!filePath.startsWith(uploadsDir) || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw NotFoundException("File not found")
        }

        val resource = UrlResource(filePath.toUri())
        val mediaType = MediaTypeFactory.getMediaType(filePath.fileName.toString()).orElse(MediaType.APPLICATION_OCTET_STREAM)
        return ResponseEntity.ok()
            .contentType(mediaType)
            .body(resource)
    }
}
