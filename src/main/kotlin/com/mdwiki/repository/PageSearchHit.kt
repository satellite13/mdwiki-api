package com.mdwiki.repository

import java.util.UUID
import java.time.Instant

/** Row from native FTS query with [ts_headline](https://www.postgresql.org/docs/current/functions-textsearch.html). */
interface PageSearchHit {
    fun getId(): UUID
    fun getSlug(): String
    fun getTitle(): String
    fun getUpdatedAt(): Instant
    /** Highlighted excerpt (markers «【» … «】») around query hits. */
    fun getHeadline(): String
}
