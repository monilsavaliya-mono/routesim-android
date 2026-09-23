package com.routesim.app.core.motion

/** A discrete "something happens here" point along a road route: a light, an intersection, a random hold-up. */
enum class RouteEventType { TRAFFIC_LIGHT, INTERSECTION, TRAFFIC_STOP }

/**
 * A candidate stop generated along a route. [willTrigger] is decided up front (from a seeded
 * random draw against [com.routesim.app.core.profile.TransportProfile.stopProbability]) so a
 * deterministic-mode run reproduces exactly the same stop pattern every time.
 */
data class RouteEvent(
    val type: RouteEventType,
    val distanceMeters: Double,
    val dwellSeconds: Double,
    val willTrigger: Boolean,
)
