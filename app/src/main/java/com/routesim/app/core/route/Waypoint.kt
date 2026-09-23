package com.routesim.app.core.route

import com.routesim.app.core.geo.GeoPoint
import kotlinx.serialization.Serializable

/** A user-meaningful marker along a route: a via point, a named stop, start or destination. */
@Serializable
data class Waypoint(
    val point: GeoPoint,
    val name: String? = null,
    val order: Int = 0,
)

/**
 * One stop in a [Timetable]: a station name/location plus scheduled offsets (in seconds
 * from journey start). The first stop has no arrival offset (the journey starts there);
 * the last stop has no departure offset (the journey ends there).
 */
@Serializable
data class TimetableStop(
    val name: String,
    val point: GeoPoint,
    val arrivalOffsetSeconds: Long?,
    val departureOffsetSeconds: Long?,
) {
    val dwellSeconds: Long
        get() = if (arrivalOffsetSeconds != null && departureOffsetSeconds != null) {
            (departureOffsetSeconds - arrivalOffsetSeconds).coerceAtLeast(0)
        } else {
            0
        }
}

/** An ordered station schedule for a [RouteType.RAIL] (or any scheduled) journey. */
@Serializable
data class Timetable(val stops: List<TimetableStop>) {
    init {
        require(stops.size >= 2) { "A timetable needs at least an origin and a destination stop" }
    }

    val totalScheduledDurationSeconds: Long
        get() = (stops.last().arrivalOffsetSeconds ?: 0L) - (stops.first().departureOffsetSeconds ?: 0L)
}
