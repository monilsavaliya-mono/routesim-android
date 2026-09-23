package com.mocklocation.app.fileroute

/** Turns raw file text into a validated [FileRoute], or a user-facing reason it couldn't. */
interface FileRouteParser {
    /** [fallbackName] is used when the file itself doesn't specify a route name (e.g. the picked file's display name). */
    fun parse(text: String, fallbackName: String): FileRouteParseResult
}
