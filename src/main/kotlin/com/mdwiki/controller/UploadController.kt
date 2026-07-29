package com.mdwiki.controller

import com.mdwiki.config.WikiProperties
import com.mdwiki.error.NotFoundException
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Files
import java.nio.file.Path

@RestController
@RequestMapping("/api/uploads")
class UploadController(
    private val wikiProperties: WikiProperties
) {
    private val uploadsDir: Path
        get() = Path.of(wikiProperties.contentDir).toAbsolutePath().normalize().resolve("uploads")

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
        val builder = ResponseEntity.ok()
            .contentType(mediaType)
            // Файлы отдаются без аутентификации — запрещаем браузеру исполнять их как активный контент
            .header("X-Content-Type-Options", "nosniff")
        if (mediaType.subtype.contains("svg") || mediaType == MediaType.TEXT_HTML) {
            // Легаси-файлы, загруженные до allowlist'а, отдаём только как скачиваемые
            builder.header("Content-Disposition", "attachment")
        }
        return builder.body(resource)
    }
}
