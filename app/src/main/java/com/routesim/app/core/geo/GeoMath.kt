package com.routesim.app.core.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Spherical-earth geographic math used throughout the simulation engine: distance,
 * bearing, destination-point projection and interpolation between two points.
 *
 * A spherical model (not an ellipsoidal one, e.g. Vincenty) is used deliberately: the
 * error versus WGS84 is well under 0.5% over any route-segment-scale distance, which is
 * far smaller than the GPS noise the app injects on purpose, so the extra complexity of
 * an ellipsoidal solver would not make the simulated output more realistic.
 */
object GeoMath {

    const val EARTH_RADIUS_METERS: Double = 6_371_000.0

    /** Great-circle distance between two points, in meters (haversine formula). */
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2.0).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2.0).pow(2)
        val c = 2.0 * atan2(sqrt(h), sqrt(1.0 - h))
        return EARTH_RADIUS_METERS * c
    }

    /** Initial compass bearing (0..360, 0 = north, clockwise) travelling from [a] to [b]. */
    fun initialBearingDegrees(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return normalizeBearing(Math.toDegrees(atan2(y, x)))
    }

    /** The point reached by travelling [distanceMeters] from [origin] on bearing [bearingDegrees]. */
    fun destinationPoint(origin: GeoPoint, bearingDegrees: Double, distanceMeters: Double): GeoPoint {
        val angularDistance = distanceMeters / EARTH_RADIUS_METERS
        val bearingRad = Math.toRadians(bearingDegrees)
        val lat1 = Math.toRadians(origin.latitude)
        val lon1 = Math.toRadians(origin.longitude)

        val lat2 = asin(
            sin(lat1) * cos(angularDistance) + cos(lat1) * sin(angularDistance) * cos(bearingRad)
        )
        val lon2 = lon1 + atan2(
            sin(bearingRad) * sin(angularDistance) * cos(lat1),
            cos(angularDistance) - sin(lat1) * sin(lat2),
        )
        return GeoPoint(Math.toDegrees(lat2), normalizeLongitude(Math.toDegrees(lon2)), origin.altitude)
    }

    /**
     * Position [fraction] (0..1) of the way from [a] to [b] along the great-circle path,
     * with altitude linearly interpolated. Used to place a simulated sample within a
     * route segment — never jumps straight between distant points, always walks the
     * geometry the segment was built from.
     */
    fun interpolate(a: GeoPoint, b: GeoPoint, fraction: Double): GeoPoint {
        val clamped = fraction.coerceIn(0.0, 1.0)
        val distance = distanceMeters(a, b)
        val point = if (distance < 1e-6) {
            a
        } else {
            val bearing = initialBearingDegrees(a, b)
            destinationPoint(a, bearing, distance * clamped)
        }
        val altitude = when {
            a.altitude != null && b.altitude != null -> a.altitude + (b.altitude - a.altitude) * clamped
            else -> b.altitude ?: a.altitude
        }
        return point.copy(altitude = altitude)
    }

    /** Normalizes a bearing to the [0, 360) range. */
    fun normalizeBearing(degrees: Double): Double {
        var d = degrees % 360.0
        if (d < 0.0) d += 360.0
        return d
    }

    /** Normalizes a longitude to the [-180, 180] range. */
    fun normalizeLongitude(degrees: Double): Double {
        var d = degrees
        while (d > 180.0) d -= 360.0
        while (d < -180.0) d += 360.0
        return d
    }

    /**
     * Signed shortest angular difference turning from bearing [from] to bearing [to],
     * in the range (-180, 180]. Positive is a clockwise (right) turn.
     */
    fun bearingDelta(from: Double, to: Double): Double {
        var delta = (normalizeBearing(to) - normalizeBearing(from)) % 360.0
        if (delta <= -180.0) delta += 360.0
        if (delta > 180.0) delta -= 360.0
        return delta
    }

    /**
     * Moves [current] bearing towards [target] by at most [maxDeltaDegrees], turning the
     * shorter way around the compass. Used to make the simulated marker's heading change
     * gradually across a segment transition instead of snapping instantly.
     */
    fun smoothBearing(current: Double, target: Double, maxDeltaDegrees: Double): Double {
        val delta = bearingDelta(current, target)
        val step = delta.coerceIn(-maxDeltaDegrees, maxDeltaDegrees)
        return normalizeBearing(current + step)
    }
}
