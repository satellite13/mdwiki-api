package com.mdwiki.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.Collections
import java.util.Enumeration

@Component
class McpAcceptHeaderFilter : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return request.requestURI != "/mcp/sse"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val wrapped = request.withNormalizedAcceptHeader()
        filterChain.doFilter(wrapped, response)
    }

    private fun HttpServletRequest.withNormalizedAcceptHeader(): HttpServletRequest {
        val current = getHeader("Accept").orEmpty()
        val normalized = normalizeAcceptHeader(current)

        if (normalized == current) {
            return this
        }

        return object : HttpServletRequestWrapper(this) {
            override fun getHeader(name: String): String? {
                return if (name.equals("Accept", ignoreCase = true)) normalized else super.getHeader(name)
            }

            override fun getHeaders(name: String): Enumeration<String> {
                return if (name.equals("Accept", ignoreCase = true)) {
                    Collections.enumeration(listOf(normalized))
                } else {
                    super.getHeaders(name)
                }
            }
        }
    }

    private fun normalizeAcceptHeader(current: String): String {
        val hasJson = current.contains("application/json", ignoreCase = true)
        val hasEventStream = current.contains("text/event-stream", ignoreCase = true)

        return when {
            hasJson && hasEventStream -> current
            current.isBlank() -> "application/json, text/event-stream"
            hasJson -> "$current, text/event-stream"
            hasEventStream -> "$current, application/json"
            else -> "$current, application/json, text/event-stream"
        }
    }
}
