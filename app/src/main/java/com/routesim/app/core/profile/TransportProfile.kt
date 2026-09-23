package com.routesim.app.core.profile

import com.routesim.app.core.route.RouteType
import kotlinx.serialization.Serializable

/**
 * Motion characteristics for a transport mode (section 5). Values are the physical
 * knobs [com.routesim.app.core.motion.RoadMotionModel] and
 * [com.routesim.app.core.motion.RailMotionModel] read every simulation tick; nothing
 * about "what a taxi looks like" is hard-coded into the motion models themselves.
 */
@Serializable
data class TransportProfile(
    val id: String,
    val name: String,
    val type: RouteType,
    /** Hard ceiling the motion model will never exceed. */
    val maxSpeedKmh: Double,
    /** Speed the model settles towards while cruising, absent other constraints. */
    val cruiseSpeedKmh: Double,
    /** Typical acceleration while speeding up, in m/s^2. */
    val accelerationMps2: Double,
    /** Typical braking magnitude while slowing down, in m/s^2 (positive number). */
    val decelerationMps2: Double,
    /** Fraction of cruise speed applied as continuous +/- noise while cruising (small speed changes). */
    val speedVariability: Double = 0.08,
    /** Expected number of traffic-light/intersection-like stop events per kilometer. */
    val stopFrequencyPerKm: Double = 0.0,
    /** Chance [0,1] that a generated stop candidate actually triggers a stop (vs. a slow-through). */
    val stopProbability: Double = 0.6,
    val minDwellSeconds: Double = 0.0,
    val maxDwellSeconds: Double = 0.0,
    /** How strongly road curvature reduces target speed through a turn: 0 = ignore, 1 = full physical reduction. */
    val curveSpeedFactor: Double = 0.6,
    val isCustom: Boolean = false,
) {
    init {
        require(maxSpeedKmh > 0) { "maxSpeedKmh must be positive" }
        require(cruiseSpeedKmh in 0.0..maxSpeedKmh) { "cruiseSpeedKmh must be within (0, maxSpeedKmh]" }
        require(accelerationMps2 > 0) { "accelerationMps2 must be positive" }
        require(decelerationMps2 > 0) { "decelerationMps2 must be positive" }
        require(minDwellSeconds <= maxDwellSeconds) { "minDwellSeconds must be <= maxDwellSeconds" }
    }
}
