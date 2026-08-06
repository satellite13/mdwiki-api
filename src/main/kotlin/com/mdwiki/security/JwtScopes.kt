package com.mdwiki.security

object JwtScopes {
    const val PAGES_IMPORT = "pages:import"

    /** Paths allowed for [PAGES_IMPORT] scoped tokens (method + servlet path). */
    fun allows(scope: String, method: String, servletPath: String): Boolean {
        return when (scope) {
            PAGES_IMPORT ->
                method.equals("POST", ignoreCase = true) && servletPath == "/api/pages/import"
            else -> false
        }
    }
}

/** Stored in Authentication.details for scoped JWTs. */
data class JwtAuthDetails(val scope: String?)
