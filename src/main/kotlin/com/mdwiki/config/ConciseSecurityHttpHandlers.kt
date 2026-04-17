package com.mdwiki.config

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.access.AccessDeniedHandlerImpl
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

/**
 * Пишет в лог одну строку с контекстом запроса и делегирует стандартный ответ 403.
 */
@Component
class ConciseAccessDeniedHandler : AccessDeniedHandler {
    private val log = LoggerFactory.getLogger(javaClass)
    private val delegate: AccessDeniedHandler = AccessDeniedHandlerImpl()

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: org.springframework.security.access.AccessDeniedException
    ) {
        val auth = SecurityContextHolder.getContext().authentication
        val principal = auth?.name ?: "anonymous"
        val rawAuthorities = auth?.authorities?.joinToString(",") { it.authority ?: "" }
        val authorities = if (rawAuthorities.isNullOrEmpty()) "-" else rawAuthorities
        log.warn(
            "Доступ запрещён (HTTP 403): {} {} — principal={}, authorities={}, причина={} ({})",
            request.method,
            request.requestURI,
            principal,
            authorities,
            accessDeniedException.message,
            accessDeniedException.javaClass.simpleName
        )
        delegate.handle(request, response, accessDeniedException)
    }
}

/**
 * Пишет в лог одну строку и отдаёт JSON 401, если ответ ещё не ушёл клиенту.
 */
@Component
class ConciseAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: org.springframework.security.core.AuthenticationException
    ) {
        log.warn(
            "Требуется аутентификация (HTTP 401): {} {} — {}",
            request.method,
            request.requestURI,
            authException.message ?: authException.javaClass.simpleName
        )
        if (response.isCommitted) {
            return
        }
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val body = mapOf(
            "error" to "UNAUTHORIZED",
            "message" to (authException.message ?: "Authentication required")
        )
        objectMapper.writeValue(response.outputStream, body)
    }
}
