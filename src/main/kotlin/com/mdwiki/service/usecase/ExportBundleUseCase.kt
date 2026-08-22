package com.mdwiki.service.usecase

import com.fasterxml.jackson.databind.ObjectMapper
import com.mdwiki.dto.BundleExportRequest
import com.mdwiki.dto.BundleManifest
import com.mdwiki.dto.BundleManifestAttachment
import com.mdwiki.dto.BundleManifestFolder
import com.mdwiki.dto.BundleManifestPage
import org.springframework.stereotype.Component
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Component
class ExportBundleUseCase(
    private val collectBundleSelectionUseCase: CollectBundleSelectionUseCase,
    private val objectMapper: ObjectMapper
) {
    fun execute(request: BundleExportRequest, output: OutputStream) {
        val collected = collectBundleSelectionUseCase.execute(request)
        val manifest = BundleManifest(
            format = BundleManifest.FORMAT,
            version = BundleManifest.VERSION,
            exportedAt = Instant.now().toString(),
            folders = collected.folders.map { BundleManifestFolder(it.path, it.name) },
            pages = collected.pages.map { page ->
                BundleManifestPage(
                    slug = page.slug,
                    title = page.title,
                    folderPath = page.folderPath,
                    file = "pages/${page.slug}.md",
                    attachmentRefs = page.attachmentRefs
                )
            },
            attachments = collected.attachments.map { attachment ->
                BundleManifestAttachment(
                    storedName = attachment.storedName,
                    originalName = attachment.originalName,
                    contentType = attachment.contentType,
                    file = "attachments/${attachment.storedName}",
                    referencedBy = attachment.referencedBy
                )
            }
        )

        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(objectMapper.writeValueAsBytes(manifest))
            zip.closeEntry()

            for (page in collected.pages) {
                zip.putNextEntry(ZipEntry("pages/${page.slug}.md"))
                zip.write(page.contentMd.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            for (attachment in collected.attachments) {
                zip.putNextEntry(ZipEntry("attachments/${attachment.storedName}"))
                attachment.diskPath.toFile().inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}
