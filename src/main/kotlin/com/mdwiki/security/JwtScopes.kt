package com.mdwiki.security

object JwtScopes {
    const val PAGES_IMPORT = "pages:import"
    const val ATTACHMENTS_UPLOAD = "attachments:upload"
    const val BUNDLES_EXPORT = "bundles:export"
    const val BUNDLES_IMPORT = "bundles:import"

    data class Route(val method: String, val path: String)

    /** Registry: scope → allowed REST routes (method + servlet path). */
    private val policies: Map<String, Set<Route>> = mapOf(
        PAGES_IMPORT to setOf(Route("POST", "/api/pages/import")),
        ATTACHMENTS_UPLOAD to setOf(Route("POST", "/api/attachments")),
        BUNDLES_EXPORT to setOf(
            Route("POST", "/api/bundles/preview"),
            Route("POST", "/api/bundles/export")
        ),
        BUNDLES_IMPORT to setOf(Route("POST", "/api/bundles/import")),
    )

    fun isKnown(scope: String): Boolean = scope.isNotBlank() && scope in policies

    fun allows(scope: String, method: String, servletPath: String): Boolean {
        val allowed = policies[scope] ?: return false
        return allowed.any { it.method.equals(method, ignoreCase = true) && it.path == servletPath }
    }

    fun usageHint(scope: String): String = when (scope) {
        PAGES_IMPORT ->
            "Authorization: Bearer <token> → POST /api/pages/import (multipart field 'files')"
        ATTACHMENTS_UPLOAD ->
            "Authorization: Bearer <token> → POST /api/attachments (multipart field 'file', optional pageId)"
        BUNDLES_EXPORT ->
            "Authorization: Bearer <token> → POST /api/bundles/preview or POST /api/bundles/export (JSON body pageSlugs/folderIds)"
        BUNDLES_IMPORT ->
            "Authorization: Bearer <token> → POST /api/bundles/import (multipart field 'file', optional targetFolderId)"
        else -> "Unknown scope: $scope"
    }
}

/** Stored in Authentication.details for scoped JWTs. */
data class JwtAuthDetails(val scope: String?)
