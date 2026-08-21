package com.mdwiki.security

object JwtScopes {
    const val PAGES_IMPORT = "pages:import"
    const val ATTACHMENTS_UPLOAD = "attachments:upload"

    data class Route(val method: String, val path: String)

    /** Registry: scope → allowed REST routes (method + servlet path). */
    private val policies: Map<String, Set<Route>> = mapOf(
        PAGES_IMPORT to setOf(Route("POST", "/api/pages/import")),
        ATTACHMENTS_UPLOAD to setOf(Route("POST", "/api/attachments")),
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
        else -> "Unknown scope: $scope"
    }
}

/** Stored in Authentication.details for scoped JWTs. */
data class JwtAuthDetails(val scope: String?)
