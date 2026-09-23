package com.routesim.app.core.route

import com.routesim.app.core.geo.GeoMath
import com.routesim.app.core.geo.GeoPoint

/**
 * Precomputed, queryable form of a [Route]'s geometry: distance/bearing/cumulative-distance
 * per segment, so the simulation engine can map "distance traveled so far" to a lat/lon and
 * heading via binary search instead of ever interpolating between two arbitrary, possibly
 * distant points (section 13).
 *
 * Consecutive duplicate points collapse into one (handles GPX/KML noise and hand-drawn
 * routes with accidental repeats). A route with only one distinct point is a valid,
 * stationary "route" (zero segments) rather than an error — this is what a plain
 * "simulate this fixed location" scenario reduces to.
 */
class SegmentedRoute private constructor(
    val route: Route,
    val segments: List<RouteSegment>,
    val totalDistanceMeters: Double,
) {
    val isStationary: Boolean get() = segments.isEmpty()

    /** Resolves a distance-along-route (clamped to the route's bounds) to a position, heading and progress. */
    fun pointAt(distanceMeters: Double): PositionOnRoute {
        if (isStationary) {
            val point = route.points.firstOrNull() ?: GeoPoint.ZERO
            return PositionOnRoute(
                point = point,
                bearingDegrees = 0.0,
                segmentIndex = 0,
                distanceTraveledMeters = 0.0,
                distanceRemainingMeters = 0.0,
                fractionComplete = 1.0,
            )
        }
        val clamped = distanceMeters.coerceIn(0.0, totalDistanceMeters)
        val segment = segmentContaining(clamped)
        val within = (clamped - segment.cumulativeDistanceAtStartMeters).coerceIn(0.0, segment.distanceMeters)
        val fraction = if (segment.distanceMeters > 0.0) within / segment.distanceMeters else 0.0
        val point = GeoMath.interpolate(segment.start, segment.end, fraction)
        return PositionOnRoute(
            point = point,
            bearingDegrees = segment.bearingDegrees,
            segmentIndex = segment.index,
            distanceTraveledMeters = clamped,
            distanceRemainingMeters = totalDistanceMeters - clamped,
            fractionComplete = if (totalDistanceMeters > 0.0) clamped / totalDistanceMeters else 1.0,
        )
    }

    /**
     * Distance-along-route of the route vertex nearest to [point]. Used to resolve a
     * station/waypoint's position onto the route's own geometry (rather than requiring exact
     * coordinate matches), e.g. when building a [com.routesim.app.core.motion.RailPlan] from a
     * timetable.
     */
    fun nearestVertexDistanceMeters(point: GeoPoint): Double {
        if (isStationary) return 0.0
        var bestDistance = Double.MAX_VALUE
        var bestCumulative = 0.0
        val startCandidate = GeoMath.distanceMeters(segments.first().start, point)
        if (startCandidate < bestDistance) {
            bestDistance = startCandidate
            bestCumulative = 0.0
        }
        for (segment in segments) {
            val d = GeoMath.distanceMeters(segment.end, point)
            if (d < bestDistance) {
                bestDistance = d
                bestCumulative = segment.cumulativeDistanceAtEndMeters
            }
        }
        return bestCumulative
    }

    /** The last segment whose start-cumulative-distance is <= [distanceMeters] (binary search). */
    fun segmentContaining(distanceMeters: Double): RouteSegment {
        var lo = 0
        var hi = segments.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (segments[mid].cumulativeDistanceAtStartMeters <= distanceMeters) lo = mid else hi = mid - 1
        }
        return segments[lo]
    }

    companion object {
        /** Points closer together than this are treated as the same point. */
        private const val DEDUPE_EPSILON_METERS = 0.5

        fun from(route: Route): SegmentedRoute {
            val deduped = dedupe(route.points)
            require(deduped.isNotEmpty()) { "Route '${route.name}' has no geometry points" }

            if (deduped.size == 1) {
                return SegmentedRoute(route, emptyList(), 0.0)
            }

            val segments = ArrayList<RouteSegment>(deduped.size - 1)
            var cumulative = 0.0
            var previousBearing: Double? = null
            for (i in 0 until deduped.size - 1) {
                val a = deduped[i]
                val b = deduped[i + 1]
                val distance = GeoMath.distanceMeters(a, b)
                val bearing = GeoMath.initialBearingDegrees(a, b)
                val turn = previousBearing?.let { GeoMath.bearingDelta(it, bearing) } ?: 0.0
                segments += RouteSegment(
                    index = i,
                    start = a,
                    end = b,
                    distanceMeters = distance,
                    bearingDegrees = bearing,
                    cumulativeDistanceAtStartMeters = cumulative,
                    turnIntoSegmentDegrees = turn,
                )
                cumulative += distance
                previousBearing = bearing
            }
            return SegmentedRoute(route, segments, cumulative)
        }

        private fun dedupe(points: List<GeoPoint>): List<GeoPoint> {
            if (points.isEmpty()) return points
            val result = ArrayList<GeoPoint>(points.size)
            result += points.first()
            for (point in points.drop(1)) {
                if (GeoMath.distanceMeters(result.last(), point) > DEDUPE_EPSILON_METERS) {
                    result += point
                }
            }
            return result
        }
    }
}
