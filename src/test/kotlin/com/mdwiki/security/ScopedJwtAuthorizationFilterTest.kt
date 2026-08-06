package com.mdwiki.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

class ScopedJwtAuthorizationFilterTest {

    private val filter = ScopedJwtAuthorizationFilter(ObjectMapper())

    @Test
    fun `scoped token allows pages import`() {
        setAuth(JwtScopes.PAGES_IMPORT)
        val request = MockHttpServletRequest("POST", "/api/pages/import")
        request.servletPath = "/api/pages/import"
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, MockFilterChain())
        assertEquals(200, response.status)
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `scoped token forbids other paths`() {
        setAuth(JwtScopes.PAGES_IMPORT)
        val request = MockHttpServletRequest("GET", "/api/pages")
        request.servletPath = "/api/pages"
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, MockFilterChain())
        assertEquals(403, response.status)
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `unscoped token is not restricted`() {
        setAuth(scope = null)
        val request = MockHttpServletRequest("GET", "/api/pages")
        request.servletPath = "/api/pages"
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, MockFilterChain())
        assertEquals(200, response.status)
        SecurityContextHolder.clearContext()
    }

    private fun setAuth(scope: String?) {
        val auth = UsernamePasswordAuthenticationToken(
            "editor",
            null,
            listOf(SimpleGrantedAuthority("ROLE_EDITOR"))
        )
        auth.details = JwtAuthDetails(scope)
        SecurityContextHolder.getContext().authentication = auth
    }
}
