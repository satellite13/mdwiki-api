package com.mdwiki.util

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * PostgreSQL `timestamptz` stores microseconds. Java [Instant.now] has nanoseconds,
 * so a value written to the DB and the same instant echoed from the in-memory entity
 * (or a previous HTTP response) are not `==`. Optimistic locks must compare at DB precision.
 */
object PersistentInstant {
    private val PRECISION = ChronoUnit.MICROS

    fun now(): Instant = Instant.now().truncatedTo(PRECISION)

    fun same(left: Instant, right: Instant): Boolean =
        left.truncatedTo(PRECISION) == right.truncatedTo(PRECISION)
}
