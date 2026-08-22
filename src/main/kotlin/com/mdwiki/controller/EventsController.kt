package com.mdwiki.controller

import com.mdwiki.service.JwtService
import com.mdwiki.service.TreeEventsService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/events")
class EventsController(
    private val jwtService: JwtService,
    private val treeEventsService: TreeEventsService
) {
    // Основной путь — Authorization: Bearer (JwtAuthenticationFilter уже выставил Authentication).
    // Query-param token — legacy для клиентов, которые не умеют ставить заголовки
    // (нативный EventSource): токен в URL попадает в access-логи и историю браузера,
    // поэтому новым клиентам следует использовать заголовок.
    @GetMapping("/tree", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribeTree(
        @RequestParam token: String?,
        authentication: Authentication?
    ): ResponseEntity<SseEmitter> {
        if (authentication == null && !isValidQueryToken(token)) {
            // Do not throw: ExceptionHandler would return JSON, and EventSource
            // Accept: text/event-stream then becomes HttpMediaTypeNotAcceptableException.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(treeEventsService.subscribe())
    }

    private fun isValidQueryToken(token: String?): Boolean {
        val rawToken = token?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return jwtService.validateToken(rawToken)
    }
}
