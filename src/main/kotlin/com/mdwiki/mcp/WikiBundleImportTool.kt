package com.mdwiki.mcp

import com.mdwiki.mcp.McpSupport.currentUsername
import com.mdwiki.mcp.McpSupport.parseUuid
import com.mdwiki.service.BundleService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
class WikiBundleImportTool(private val bundleService: BundleService) {

    @McpTool(
        name = "wiki_bundle_import",
        description = "Import an mdwiki bundle zip from a file path on the mdwiki-api host. " +
            "Creates a new subtree under targetFolderId (or wiki root). Colliding slugs get a -2 suffix; " +
            "wikilinks and attachment URLs inside the bundle are rewritten. " +
            "Path must be inside mdwiki.attachments.allowed-import-dirs. Requires EDITOR or ADMIN. " +
            "For a client-uploaded zip use wiki_auth_token(scope=bundles:import) then POST /api/bundles/import."
    )
    fun importBundle(
        @McpToolParam(description = "Absolute or relative zip path on the server host")
        filePath: String,
        @McpToolParam(description = "Optional parent folder UUID for the imported subtree", required = false)
        targetFolderId: String?
    ): Map<String, Any?> {
        val username = currentUsername()
        val parsedFolderId = targetFolderId?.takeIf { it.isNotBlank() }?.let(::parseUuid)
        val result = bundleService.importFromPath(Path.of(filePath), parsedFolderId, username)
        return mapOf(
            "createdPages" to result.createdPages,
            "createdFolders" to result.createdFolders,
            "remappedSlugs" to result.remappedSlugs.map { mapOf("from" to it.from, "to" to it.to) },
            "attachments" to result.attachments,
            "errors" to result.errors
        )
    }
}
