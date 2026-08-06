package com.mdwiki.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JwtScopesTest {

    @Test
    fun `pages import allows only POST import endpoint`() {
        assertTrue(JwtScopes.allows(JwtScopes.PAGES_IMPORT, "POST", "/api/pages/import"))
        assertTrue(JwtScopes.allows(JwtScopes.PAGES_IMPORT, "post", "/api/pages/import"))
        assertFalse(JwtScopes.allows(JwtScopes.PAGES_IMPORT, "GET", "/api/pages/import"))
        assertFalse(JwtScopes.allows(JwtScopes.PAGES_IMPORT, "POST", "/api/pages"))
        assertFalse(JwtScopes.allows(JwtScopes.PAGES_IMPORT, "POST", "/api/pages/foo"))
        assertFalse(JwtScopes.allows("unknown", "POST", "/api/pages/import"))
    }
}
