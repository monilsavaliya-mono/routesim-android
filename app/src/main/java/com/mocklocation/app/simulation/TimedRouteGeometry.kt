package com.mocklocation.app.simulation

import com.mocklocation.app.fileroute.FileRoute
import com.mocklocation.app.fileroute.TimestampedRoutePoint
import com.mocklocation.app.model.LatLng

/**
 * Time-domain counterpart to [RouteGeometry]: given milliseconds elapsed since a
 * [FileRoute]'s first timestamp, returns how far along the route (in metres) playback
 * should be, and at what speed. `SimulationEngine` drives its existing `distanceMeters`
 * scalar from [arcLengthAtElapsedMs] instead of integrating speed itself — the file's own
 * timestamps are authoritative — which is what lets every other position/heading/altitude
 * query it already makes against a plain [RouteGeometry] built from the same point order
 * keep working unmodified: both classes compute the identical cumulative-distance array
 * from the identical point list, so a distance handed from one to the other lands exactly
 * on the point in time the file described.
 *
 * Every lookup is a binary search over the keyframes, so replaying a very large route
 * costs the same per tick as a small one — nothing here re-scans the point list.
 */
class TimedRouteGeometry(fileRoute: FileRoute) {

    val points: List<TimestampedRoutePoint> = fileRoute.points

    /** cumulativeDistance[i] = metres from points[0] to points[i]. */
    private val cumulativeDistance = DoubleArray(points.size)

    /** cumulativeTimeMs[i] = ms from points[0]'s timestamp to points[i]'s. */
    private val cumulativeTimeMs = LongArray(points.size)

    val totalDistanceMeters: Double
    val totalDurationMs: Long

    init {
        require(points.size >= 2) { "TimedRouteGeometry needs at least 2 points" }
        var distanceAcc = 0.0
        val startMs = points.first().timestampEpochMillis
        for (i in points.indices) {
            if (i > 0) distanceAcc += latLngAt(i - 1).distanceTo(latLngAt(i))
            cumulativeDistance[i] = distanceAcc
            cumulativeTimeMs[i] = points[i].timestampEpochMillis - startMs
        }
        totalDistanceMeters = distanceAcc
        totalDurationMs = cumulativeTimeMs.last()
    }

    private fun latLngAt(index: Int) = LatLng(points[index].latitude, points[index].longitude)

    /** Index of the last keyframe at or before [elapsedMs] (binary search, never a linear scan). */
    fun indexAt(elapsedMs: Long): Int {
        val t = elapsedMs.coerceIn(0L, totalDurationMs)
        var lo = 0
        var hi = points.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (cumulativeTimeMs[mid] <= t) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** Distance travelled, in metres, at [elapsedMs] — linearly interpolated within the bracketing keyframes' time span. */
    fun arcLengthAtElapsedMs(elapsedMs: Long): Double {
        val t = elapsedMs.coerceIn(0L, totalDurationMs)
        val i = indexAt(t)
        if (i >= points.lastIndex) return totalDistanceMeters
        val segTimeMs = cumulativeTimeMs[i + 1] - cumulativeTimeMs[i]
        val fraction = if (segTimeMs > 0L) (t - cumulativeTimeMs[i]).toDouble() / segTimeMs else 0.0
        val segDistance = cumulativeDistance[i + 1] - cumulativeDistance[i]
        return cumulativeDistance[i] + segDistance * fraction
    }

    /** The elapsed-ms that would produce [distanceMeters] from [arcLengthAtElapsedMs] — the inverse lookup, used to seek by distance fraction. */
    fun elapsedMsAtArcLength(distanceMeters: Double): Long {
        if (points.size < 2) return 0L
        val d = distanceMeters.coerceIn(0.0, totalDistanceMeters)
        var lo = 0
        var hi = points.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (cumulativeDistance[mid] <= d) lo = mid else hi = mid - 1
        }
        if (lo >= points.lastIndex) return totalDurationMs
        val segDistance = cumulativeDistance[lo + 1] - cumulativeDistance[lo]
        val fraction = if (segDistance > 0.0) (d - cumulativeDistance[lo]) / segDistance else 0.0
        val segTimeMs = cumulativeTimeMs[lo + 1] - cumulativeTimeMs[lo]
        return cumulativeTimeMs[lo] + (segTimeMs * fraction).toLong()
    }

    /**
     * Speed in m/s at [elapsedMs]: the file's own explicit value when the bracketing
     * keyframe has one, otherwise distance/time over that keyframe's segment — computed,
     * never invented (section: SPEED).
     */
    fun speedAtElapsedMs(elapsedMs: Long): Double {
        val t = elapsedMs.coerceIn(0L, totalDurationMs)
        val i = indexAt(t).coerceAtMost((points.size - 2).coerceAtLeast(0))
        points[i].speedMs?.let { return it.toDouble() }
        val segTimeMs = cumulativeTimeMs[i + 1] - cumulativeTimeMs[i]
        val segDistance = cumulativeDistance[i + 1] - cumulativeDistance[i]
        return if (segTimeMs > 0L) segDistance / (segTimeMs / 1000.0) else 0.0
    }

    /** Explicit bearing from the current keyframe, when the file supplied one; null falls back to geometry-derived heading. */
    fun bearingOverrideAtElapsedMs(elapsedMs: Long): Float? {
        val i = indexAt(elapsedMs)
        return points[i].bearingDegrees ?: points.getOrNull(i + 1)?.bearingDegrees
    }

    /** Explicit altitude from the file, linearly interpolated when both bracketing keyframes have one; null otherwise. */
    fun altitudeAtElapsedMs(elapsedMs: Long): Double? {
        val t = elapsedMs.coerceIn(0L, totalDurationMs)
        val i = indexAt(t)
        if (i >= points.lastIndex) return points.last().altitudeMeters
        val a = points[i].altitudeMeters
        val b = points[i + 1].altitudeMeters
        return when {
            a != null && b != null -> {
                val segTimeMs = cumulativeTimeMs[i + 1] - cumulativeTimeMs[i]
                val fraction = if (segTimeMs > 0L) (t - cumulativeTimeMs[i]).toDouble() / segTimeMs else 0.0
                a + (b - a) * fraction
            }
            a != null -> a
            else -> b
        }
    }

    /** Explicit accuracy from the current keyframe, when the file supplied one. */
    fun accuracyOverrideAtElapsedMs(elapsedMs: Long): Float? = points[indexAt(elapsedMs)].accuracyMeters
}
