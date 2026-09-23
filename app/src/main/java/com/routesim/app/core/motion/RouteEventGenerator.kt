package com.routesim.app.core.motion

import com.routesim.app.core.profile.TransportProfile
import com.routesim.app.core.route.SegmentedRoute
import kotlin.math.round
import kotlin.random.Random

/**
 * Procedurally scatters traffic-light/intersection-like stop candidates along a road route,
 * spaced by [TransportProfile.stopFrequencyPerKm] with jitter so they don't land at perfectly
 * even intervals. Whether each candidate actually causes a stop, and for how long, is decided
 * here too — from the same seeded [Random] the rest of the simulation uses, so DETERMINISTIC
 * mode reproduces an identical stop pattern (section 12/21).
 */
object RouteEventGenerator {

    fun generate(segmentedRoute: SegmentedRoute, profile: TransportProfile, random: Random): List<RouteEvent> {
        if (segmentedRoute.isStationary || profile.stopFrequencyPerKm <= 0.0) return emptyList()

        val totalKm = segmentedRoute.totalDistanceMeters / 1000.0
        val expectedCount = round(profile.stopFrequencyPerKm * totalKm).toInt()
        if (expectedCount <= 0) return emptyList()

        val spacing = segmentedRoute.totalDistanceMeters / (expectedCount + 1)
        return (1..expectedCount).map { i ->
            val jitter = (random.nextDouble() - 0.5) * spacing * 0.4
            val distance = (spacing * i + jitter)
                .coerceIn(spacing * 0.15, segmentedRoute.totalDistanceMeters - spacing * 0.15)
            val willTrigger = random.nextDouble() < profile.stopProbability
            val dwell = if (profile.maxDwellSeconds > profile.minDwellSeconds) {
                profile.minDwellSeconds + random.nextDouble() * (profile.maxDwellSeconds - profile.minDwellSeconds)
            } else {
                profile.minDwellSeconds
            }
            RouteEvent(
                type = if (random.nextBoolean()) RouteEventType.TRAFFIC_LIGHT else RouteEventType.INTERSECTION,
                distanceMeters = distance,
                dwellSeconds = dwell,
                willTrigger = willTrigger,
            )
        }.sortedBy { it.distanceMeters }
    }
}
