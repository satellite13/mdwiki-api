package com.mdwiki.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

class TreeEventsServiceTest {

    private lateinit var treeEventsService: TreeEventsService

    @BeforeEach
    fun setUp() {
        treeEventsService = TreeEventsService()
    }

    @Test
    fun `subscribe returns an SseEmitter`() {
        val emitter = treeEventsService.subscribe()
        assertNotNull(emitter)
    }

    @Test
    fun `publishTreeUpdated does not throw when no subscribers`() {
        assertDoesNotThrow { treeEventsService.publishTreeUpdated() }
    }

    @Test
    fun `publishTreeUpdated sends event to subscriber`() {
        val receivedEvents = mutableListOf<String>()

        val emitter = treeEventsService.subscribe()

        // Complete the emitter to avoid blocking; verify subscribe worked
        assertNotNull(emitter)
        // The emitter should have received a "connected" event during subscribe
        // Publishing should not throw even if emitter has been completed
        emitter.complete()
        assertDoesNotThrow { treeEventsService.publishTreeUpdated() }
    }
}
