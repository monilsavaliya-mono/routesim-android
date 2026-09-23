package com.mocklocation.app.fileroute

/**
 * One `(time, latitude, longitude)` sample from an imported route file, plus whatever
 * optional metadata the file supplied. This is the whole idea behind File Route Playback:
 * the file is the trajectory — it does not matter whether it came from a taxi, a train, a
 * bike, or a boat, as long as it is a chronological sequence of these.
 */
data class TimestampedRoutePoint(
    val timestampEpochMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    /** Explicit speed from the file, in m/s. Computed from distance/time when absent. */
    val speedMs: Float? = null,
    /** Explicit bearing from the file, in degrees. Computed from neighbouring points when absent. */
    val bearingDegrees: Float? = null,
    val accuracyMeters: Float? = null,
    val label: String? = null,
    val isStop: Boolean = false,
)
