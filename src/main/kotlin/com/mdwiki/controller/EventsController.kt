package com.mdwiki.controller

import com.mdwiki.repository.UserRepository
import com.mdwiki.service.JwtService
import com.mdwiki.service.TreeEventsService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/events")
class EventsController(
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
    private val treeEventsService: TreeEventsService
) {
    @GetMapping("/tree", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribeTree(@RequestParam token: String?): SseEmitter {
        val rawToken = token?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing token")

        if (!jwtService.validateToken(rawToken)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token")
        }

        val username = jwtService.extractUsername(rawToken)
        val user = userRepository.findByUsername(username)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found")

        // Only authenticated wiki users are allowed to subscribe to tree events.
        if (user.role.name !in setOf("READER", "EDITOR", "ADMIN")) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden")
        }

        return treeEventsService.subscribe()
    }
}
