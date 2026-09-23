package com.routesim.app.core.geo

import kotlinx.serialization.Serializable

/**
 * A single geographic coordinate. Altitude is optional: many geometry sources (drawn
 * routes, most GPX/KML files) never provide it.
 */
@Serializable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
    }

    companion object {
        val ZERO = GeoPoint(0.0, 0.0)
    }
}
