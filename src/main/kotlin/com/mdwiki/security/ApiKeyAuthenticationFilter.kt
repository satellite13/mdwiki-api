package com.mdwiki.security

import com.mdwiki.service.ApiKeyService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class ApiKeyAuthenticationFilter(
    private val apiKeyService: ApiKeyService
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return !request.requestURI.startsWith("/mcp")
    }

    override fun shouldNotFilterErrorDispatch(): Boolean = true

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val apiKey = request.getHeader("X-API-Key")
        if (apiKey != null) {
            val user = apiKeyService.validateKey(apiKey)
            if (user != null) {
                val authorities = listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
                val auth = UsernamePasswordAuthenticationToken(user.username, null, authorities)
                SecurityContextHolder.getContext().authentication = auth
            } else {
                writeUnauthorized(response, "Invalid API key")
                return
            }
        } else {
            writeUnauthorized(response, "X-API-Key header required")
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun writeUnauthorized(response: HttpServletResponse, message: String) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.writer.write("""{"error":"$message"}""")
        response.writer.flush()
    }
}
