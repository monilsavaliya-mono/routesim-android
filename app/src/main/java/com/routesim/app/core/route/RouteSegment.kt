package com.routesim.app.core.route

import com.routesim.app.core.geo.GeoPoint

/** One leg of a route's dense geometry, with pre-computed distance/bearing/cumulative distance. */
data class RouteSegment(
    val index: Int,
    val start: GeoPoint,
    val end: GeoPoint,
    val distanceMeters: Double,
    val bearingDegrees: Double,
    val cumulativeDistanceAtStartMeters: Double,
    /** Signed heading change (degrees) from the previous segment into this one; 0 for the first segment. */
    val turnIntoSegmentDegrees: Double = 0.0,
) {
    val cumulativeDistanceAtEndMeters: Double get() = cumulativeDistanceAtStartMeters + distanceMeters
}

/** A point resolved from a distance-along-route query, with everything the simulation loop needs. */
data class PositionOnRoute(
    val point: GeoPoint,
    val bearingDegrees: Double,
    val segmentIndex: Int,
    val distanceTraveledMeters: Double,
    val distanceRemainingMeters: Double,
    val fractionComplete: Double,
)
