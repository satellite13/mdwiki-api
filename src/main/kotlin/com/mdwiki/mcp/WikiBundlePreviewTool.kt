package com.mdwiki.mcp

import com.mdwiki.dto.BundleExportRequest
import com.mdwiki.mcp.McpSupport.parseUuid
import com.mdwiki.service.BundleService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WikiBundlePreviewTool(private val bundleService: BundleService) {

    @McpTool(
        name = "wiki_bundle_preview",
        description = "Preview a document bundle for export: selected pages and folders plus related attachments. " +
            "Does not create a zip. Requires EDITOR or ADMIN. For the zip itself use wiki_auth_token(scope=bundles:export) " +
            "then POST /api/bundles/export."
    )
    fun preview(
        @McpToolParam(description = "Comma-separated page slugs to include", required = false)
        pageSlugs: String?,
        @McpToolParam(description = "Comma-separated folder UUIDs to include (descendants included)", required = false)
        folderIds: String?
    ): Map<String, Any?> {
        val request = BundleExportRequest(
            pageSlugs = splitCsv(pageSlugs),
            folderIds = splitCsv(folderIds).map(::parseUuid)
        )
        val preview = bundleService.preview(request)
        return mapOf(
            "pages" to preview.pages.map { mapOf("slug" to it.slug, "title" to it.title, "folderPath" to it.folderPath) },
            "folders" to preview.folders.map { mapOf("path" to it.path, "name" to it.name) },
            "attachmentCount" to preview.attachmentCount,
            "attachmentBytes" to preview.attachmentBytes,
            "attachments" to preview.attachments.map {
                mapOf(
                    "storedName" to it.storedName,
                    "originalName" to it.originalName,
                    "sizeBytes" to it.sizeBytes,
                    "referencedBy" to it.referencedBy
                )
            },
            "warnings" to preview.warnings
        )
    }

    private fun splitCsv(raw: String?): List<String> =
        raw?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
}
