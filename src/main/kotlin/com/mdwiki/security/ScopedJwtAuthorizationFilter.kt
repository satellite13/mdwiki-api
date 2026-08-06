package com.mdwiki.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * When Authentication carries a JWT [JwtAuthDetails.scope], restrict access to
 * paths allowed for that scope. Unscoped JWTs (login) are unaffected.
 */
@Component
class ScopedJwtAuthorizationFilter(
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val auth = SecurityContextHolder.getContext().authentication
        val scope = (auth?.details as? JwtAuthDetails)?.scope
        if (!scope.isNullOrBlank()) {
            val path = request.servletPath.ifBlank { request.requestURI }
            if (!JwtScopes.allows(scope, request.method, path)) {
                response.status = HttpServletResponse.SC_FORBIDDEN
                response.contentType = MediaType.APPLICATION_JSON_VALUE
                objectMapper.writeValue(
                    response.outputStream,
                    mapOf(
                        "error" to "FORBIDDEN",
                        "message" to "Token scope '$scope' does not allow ${request.method} $path",
                        "path" to path
                    )
                )
                return
            }
        }
        filterChain.doFilter(request, response)
    }
}
