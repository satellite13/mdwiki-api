package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.dto.BundleExportRequest
import com.mdwiki.dto.BundleImportResponse
import com.mdwiki.dto.BundlePreviewResponse
import com.mdwiki.service.usecase.CollectBundleSelectionUseCase
import com.mdwiki.service.usecase.ExportBundleUseCase
import com.mdwiki.service.usecase.ImportBundleUseCase
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@Service
class BundleService(
    private val collectBundleSelectionUseCase: CollectBundleSelectionUseCase,
    private val exportBundleUseCase: ExportBundleUseCase,
    private val importBundleUseCase: ImportBundleUseCase,
    private val wikiProperties: WikiProperties
) {
    fun preview(request: BundleExportRequest): BundlePreviewResponse =
        collectBundleSelectionUseCase.execute(request).toPreview()

    fun export(request: BundleExportRequest, output: OutputStream) {
        exportBundleUseCase.execute(request, output)
    }

    fun importBundle(file: MultipartFile, targetFolderId: UUID?, username: String): BundleImportResponse {
        if (file.isEmpty) {
            throw IllegalArgumentException("Bundle file is empty")
        }
        val filename = file.originalFilename?.lowercase() ?: ""
        if (filename.isNotEmpty() && !filename.endsWith(".zip")) {
            throw IllegalArgumentException("Bundle must be a .zip file")
        }
        return file.inputStream.use { stream ->
            importBundleUseCase.execute(
                zipStream = stream,
                targetFolderId = targetFolderId,
                username = username,
                declaredSizeBytes = file.size
            )
        }
    }

    fun importFromPath(filePath: Path, targetFolderId: UUID?, username: String): BundleImportResponse {
        val normalized = filePath.toAbsolutePath().normalize()
        if (!Files.isRegularFile(normalized)) {
            throw IllegalArgumentException("Bundle file does not exist: $normalized")
        }
        val realPath = normalized.toRealPath()
        requireImportPathAllowed(realPath)
        return Files.newInputStream(realPath).use { stream ->
            importBundleUseCase.execute(
                zipStream = stream,
                targetFolderId = targetFolderId,
                username = username,
                declaredSizeBytes = Files.size(realPath)
            )
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
}
