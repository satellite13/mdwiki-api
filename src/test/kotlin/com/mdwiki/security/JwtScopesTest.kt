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
        assertFalse(JwtScopes.allows(JwtScopes.PAGES_IMPORT, "POST", "/api/attachments"))
        assertFalse(JwtScopes.allows("unknown", "POST", "/api/pages/import"))
    }

    @Test
    fun `attachments upload allows only POST attachments endpoint`() {
        assertTrue(JwtScopes.allows(JwtScopes.ATTACHMENTS_UPLOAD, "POST", "/api/attachments"))
        assertTrue(JwtScopes.allows(JwtScopes.ATTACHMENTS_UPLOAD, "post", "/api/attachments"))
        assertFalse(JwtScopes.allows(JwtScopes.ATTACHMENTS_UPLOAD, "GET", "/api/attachments"))
        assertFalse(JwtScopes.allows(JwtScopes.ATTACHMENTS_UPLOAD, "DELETE", "/api/attachments"))
        assertFalse(JwtScopes.allows(JwtScopes.ATTACHMENTS_UPLOAD, "POST", "/api/attachments/foo"))
        assertFalse(JwtScopes.allows(JwtScopes.ATTACHMENTS_UPLOAD, "POST", "/api/pages/import"))
    }

    @Test
    fun `registry knows supported scopes`() {
        assertTrue(JwtScopes.isKnown(JwtScopes.PAGES_IMPORT))
        assertTrue(JwtScopes.isKnown(JwtScopes.ATTACHMENTS_UPLOAD))
        assertFalse(JwtScopes.isKnown("unknown"))
        assertFalse(JwtScopes.isKnown(""))
    }
}
