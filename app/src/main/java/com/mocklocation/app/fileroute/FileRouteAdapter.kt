package com.mocklocation.app.fileroute

import com.mocklocation.app.model.LatLng
import com.mocklocation.app.model.Route
import com.mocklocation.app.simulation.TimedRouteGeometry

/**
 * The seam between the imported file and the app's existing playback model (section: THIS
 * IS THE KEY DESIGN PRINCIPLE). Builds a plain [Route] — so the map, the route strip, the
 * notification and everything else that already renders a [Route] needs no changes at all
 * — alongside the [TimedRouteGeometry] the engine drives position from.
 */
object FileRouteAdapter {

    data class Adapted(val route: Route, val timedGeometry: TimedRouteGeometry)

    fun adapt(fileRoute: FileRoute): Adapted {
        val timedGeometry = TimedRouteGeometry(fileRoute)
        val route = Route(
            points = fileRoute.points.map { LatLng(it.latitude, it.longitude) },
            distanceMeters = timedGeometry.totalDistanceMeters,
            durationSeconds = fileRoute.durationSeconds.toDouble().coerceAtLeast(0.0),
        )
        return Adapted(route, timedGeometry)
    }
}

/** Picks the JSON or CSV parser by MIME type, file extension, or (as a last resort) the file's own leading character. */
object FileRouteImporter {

    fun parse(fileName: String, mimeType: String?, text: String): FileRouteParseResult {
        val looksLikeJson = mimeType?.contains("json", ignoreCase = true) == true ||
            fileName.endsWith(".json", ignoreCase = true) ||
            (mimeType?.contains("csv", ignoreCase = true) != true &&
                !fileName.endsWith(".csv", ignoreCase = true) &&
                text.trimStart().startsWith("{"))

        val parser: FileRouteParser = if (looksLikeJson) FileRouteJsonParser() else FileRouteCsvParser()
        val fallbackName = fileName.substringBeforeLast('.').ifBlank { "Imported route" }
        return parser.parse(text, fallbackName)
    }
}
