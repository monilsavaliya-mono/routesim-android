package com.mocklocation.app.fileroute

import java.time.Instant
import java.time.OffsetDateTime

/** Timestamp parsing shared by both file-route parsers. */
object FileRouteTimestamps {

    /**
     * Accepts an ISO-8601 offset timestamp (`2026-09-24T08:00:00+05:30`), a UTC instant
     * (`2026-09-24T08:00:00Z`), or a raw epoch-millisecond number, in that order. Returns
     * null rather than throwing — callers turn that into a row-numbered, user-facing error.
     */
    fun parse(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return runCatching { OffsetDateTime.parse(trimmed).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrNull()
            ?: trimmed.toLongOrNull()
    }
}
