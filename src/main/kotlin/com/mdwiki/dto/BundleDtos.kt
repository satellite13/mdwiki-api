package com.mdwiki.dto

import java.time.Instant
import java.util.UUID

data class BundleExportRequest(
    val pageSlugs: List<String> = emptyList(),
    val folderIds: List<UUID> = emptyList()
)

data class BundlePreviewFolder(
    val path: List<String>,
    val name: String
)

data class BundlePreviewPage(
    val slug: String,
    val title: String,
    val folderPath: List<String>
)

data class BundlePreviewAttachment(
    val storedName: String,
    val originalName: String,
    val sizeBytes: Long,
    val referencedBy: List<String>
)

data class BundlePreviewResponse(
    val folders: List<BundlePreviewFolder>,
    val pages: List<BundlePreviewPage>,
    val attachments: List<BundlePreviewAttachment>,
    val attachmentCount: Int,
    val attachmentBytes: Long,
    val warnings: List<String>
)

data class BundleSlugRemap(
    val from: String,
    val to: String
)

data class BundleImportResponse(
    val createdPages: Int,
    val createdFolders: Int,
    val remappedSlugs: List<BundleSlugRemap>,
    val attachments: Int,
    val errors: List<String>
)

data class BundleManifest(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: String = Instant.now().toString(),
    val folders: List<BundleManifestFolder> = emptyList(),
    val pages: List<BundleManifestPage> = emptyList(),
    val attachments: List<BundleManifestAttachment> = emptyList()
) {
    companion object {
        const val FORMAT = "mdwiki-bundle"
        const val VERSION = 1
    }
}

data class BundleManifestFolder(
    val path: List<String>,
    val name: String
)

data class BundleManifestPage(
    val slug: String,
    val title: String,
    val folderPath: List<String>,
    val file: String,
    val attachmentRefs: List<String> = emptyList()
)

data class BundleManifestAttachment(
    val storedName: String,
    val originalName: String,
    val contentType: String,
    val file: String,
    val referencedBy: List<String> = emptyList()
)

data class CollectedBundleFolder(
    val path: List<String>,
    val name: String
)

data class CollectedBundlePage(
    val slug: String,
    val title: String,
    val contentMd: String,
    val folderPath: List<String>,
    val attachmentRefs: List<String>
)

data class CollectedBundleAttachment(
    val storedName: String,
    val originalName: String,
    val contentType: String,
    val sizeBytes: Long,
    val diskPath: java.nio.file.Path,
    val referencedBy: List<String>
)

data class CollectedBundle(
    val folders: List<CollectedBundleFolder>,
    val pages: List<CollectedBundlePage>,
    val attachments: List<CollectedBundleAttachment>,
    val warnings: List<String>
) {
    fun toPreview(): BundlePreviewResponse = BundlePreviewResponse(
        folders = folders.map { BundlePreviewFolder(it.path, it.name) },
        pages = pages.map { BundlePreviewPage(it.slug, it.title, it.folderPath) },
        attachments = attachments.map {
            BundlePreviewAttachment(it.storedName, it.originalName, it.sizeBytes, it.referencedBy)
        },
        attachmentCount = attachments.size,
        attachmentBytes = attachments.sumOf { it.sizeBytes },
        warnings = warnings
    )
}
