package com.mocklocation.app.fileroute

import com.mocklocation.app.model.LatLng

/** A validated, chronologically-ordered route imported from a JSON or CSV file. */
data class FileRoute(
    val name: String,
    val type: String? = null,
    val description: String? = null,
    val points: List<TimestampedRoutePoint>,
) {
    init {
        require(points.size >= 2) { "FileRoute must have at least 2 points" }
    }

    val startEpochMillis: Long get() = points.first().timestampEpochMillis
    val endEpochMillis: Long get() = points.last().timestampEpochMillis
    val durationSeconds: Long get() = (endEpochMillis - startEpochMillis) / 1000L

    /** Sum of the haversine distance between consecutive points, in metres. */
    val totalDistanceMeters: Double by lazy {
        var distance = 0.0
        for (i in 1 until points.size) {
            distance += LatLng(points[i - 1].latitude, points[i - 1].longitude)
                .distanceTo(LatLng(points[i].latitude, points[i].longitude))
        }
        distance
    }

    /** Points explicitly flagged as a stop, or carrying a label/station name worth showing. */
    val notableStops: List<TimestampedRoutePoint>
        get() = points.filter { it.isStop || !it.label.isNullOrBlank() }
}

/** Result of parsing (and validating) a route file: either a usable [FileRoute] or a user-facing reason it failed. */
sealed interface FileRouteParseResult {
    data class Success(val route: FileRoute) : FileRouteParseResult
    data class Error(val message: String) : FileRouteParseResult
}
