package com.mocklocation.app.model

/**
 * A geographic coordinate (latitude, longitude).
 */
data class LatLng(
    val latitude: Double,
    val longitude: Double
) {
    /** Calculate approximate distance in meters using Haversine formula. */
    fun distanceTo(other: LatLng): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = Math.sin(dLat / 2).pow(2) +
                Math.cos(Math.toRadians(latitude)) *
                Math.cos(Math.toRadians(other.latitude)) *
                Math.sin(dLon / 2).pow(2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    /**
     * Great-circle (slerp) interpolation toward another point by fraction [t] (0..1).
     *
     * A plain lat/lon lerp is fine at the metre-scale spacing OSRM's polylines use — the
     * error versus a great circle is unmeasurable — but it drifts off the true path once
     * two points are tens of kilometres apart, which is exactly the point spacing a
     * timestamped file route can have. Slerping here fixes that for both callers at once
     * without a second, parallel interpolation implementation: at OSRM's spacing the two
     * methods are numerically indistinguishable, so this changes nothing observable for
     * the existing route engine.
     */
    fun interpolateTo(other: LatLng, t: Double): LatLng {
        if (t <= 0.0) return this
        if (t >= 1.0) return other

        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)
        val lat2 = Math.toRadians(other.latitude)
        val lon2 = Math.toRadians(other.longitude)

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1
        val a = Math.sin(dLat / 2).pow(2) + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2).pow(2)
        val angularDistance = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        // Antipodal-ish or coincident points: no well-defined great-circle arc to slerp
        // along, and a lerp is exactly as arbitrary — fall back to it rather than divide
        // by a near-zero sine below.
        if (angularDistance < 1e-9) {
            return LatLng(
                latitude = latitude + (other.latitude - latitude) * t,
                longitude = longitude + (other.longitude - longitude) * t
            )
        }

        val sinAngular = Math.sin(angularDistance)
        val scaleStart = Math.sin((1 - t) * angularDistance) / sinAngular
        val scaleEnd = Math.sin(t * angularDistance) / sinAngular

        val x = scaleStart * Math.cos(lat1) * Math.cos(lon1) + scaleEnd * Math.cos(lat2) * Math.cos(lon2)
        val y = scaleStart * Math.cos(lat1) * Math.sin(lon1) + scaleEnd * Math.cos(lat2) * Math.sin(lon2)
        val z = scaleStart * Math.sin(lat1) + scaleEnd * Math.sin(lat2)

        val resultLat = Math.atan2(z, Math.sqrt(x * x + y * y))
        val resultLon = Math.atan2(y, x)
        return LatLng(Math.toDegrees(resultLat), Math.toDegrees(resultLon))
    }

    /** Bearing in degrees from this point to another. */
    fun bearingTo(other: LatLng): Float {
        val dLon = Math.toRadians(other.longitude - longitude)
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
        return (Math.toDegrees(Math.atan2(y, x)) + 360).toFloat() % 360f
    }
}

private fun Double.pow(exp: Int): Double {
    var result = 1.0
    repeat(exp) { result *= this }
    return result
}
