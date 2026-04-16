package com.mdwiki.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Service
class TreeEventsService {
    private val log = LoggerFactory.getLogger(TreeEventsService::class.java)
    private val emitters = ConcurrentHashMap<Long, SseEmitter>()
    private val emitterIdSeq = AtomicLong(0)

    fun subscribe(): SseEmitter {
        val emitterId = emitterIdSeq.incrementAndGet()
        val emitter = SseEmitter(0L)
        emitters[emitterId] = emitter

        emitter.onCompletion { emitters.remove(emitterId) }
        emitter.onTimeout { emitters.remove(emitterId) }
        emitter.onError { emitters.remove(emitterId) }

        // Initial event helps client confirm connection is alive.
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"))
        } catch (_: IOException) {
            emitters.remove(emitterId)
        }

        return emitter
    }

    fun publishTreeUpdated() {
        if (emitters.isEmpty()) return

        val deadEmitters = mutableListOf<Long>()
        emitters.forEach { (id, emitter) ->
            try {
                emitter.send(SseEmitter.event().name("tree-updated").data("changed"))
            } catch (_: IOException) {
                deadEmitters.add(id)
            } catch (e: Exception) {
                log.debug("Failed to emit tree-updated event", e)
                deadEmitters.add(id)
            }
        }
        deadEmitters.forEach { emitters.remove(it) }
    }
}
