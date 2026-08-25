package com.mdwiki.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class PersistentInstantTest {

    @Test
    fun `same treats leftover nanos as equal`() {
        val stored = Instant.parse("2026-08-15T10:00:00.123456Z")
        assertTrue(PersistentInstant.same(stored, stored.plusNanos(789)))
        assertTrue(PersistentInstant.same(stored.plusNanos(789), stored))
    }

    @Test
    fun `same rejects a real microsecond change`() {
        val stored = Instant.parse("2026-08-15T10:00:00.123456Z")
        assertFalse(PersistentInstant.same(stored, stored.plusNanos(1000)))
    }

    @Test
    fun `now is truncated to microseconds`() {
        assertEquals(0, PersistentInstant.now().nano % 1000)
    }
}
