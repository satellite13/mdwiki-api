package com.mdwiki.controller

import com.mdwiki.dto.BundleExportRequest
import com.mdwiki.dto.BundleImportResponse
import com.mdwiki.dto.BundlePreviewResponse
import com.mdwiki.service.BundleService
import org.springframework.http.HttpHeaders
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.util.UUID
import jakarta.servlet.http.HttpServletResponse

@RestController
@RequestMapping("/api/bundles")
class BundleController(
    private val bundleService: BundleService
) {
    @PostMapping("/preview")
    fun preview(@RequestBody request: BundleExportRequest): BundlePreviewResponse =
        bundleService.preview(request)

    @PostMapping("/export")
    fun export(@RequestBody request: BundleExportRequest, response: HttpServletResponse) {
        val filename = "mdwiki-bundle-${LocalDate.now()}.zip"
        response.contentType = "application/zip"
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
        bundleService.export(request, response.outputStream)
    }

    @PostMapping("/import")
    fun importBundle(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(required = false) targetFolderId: UUID?,
        auth: Authentication
    ): BundleImportResponse = bundleService.importBundle(file, targetFolderId, auth.name)
}
