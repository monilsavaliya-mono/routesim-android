package com.routesim.app.core.motion

import com.routesim.app.core.profile.TransportProfile
import com.routesim.app.core.route.SegmentedRoute
import com.routesim.app.core.route.Timetable
import kotlin.random.Random

/** Everything a [MotionModel] needs to advance one tick, besides the previous [MotionState]. */
data class MotionContext(
    val segmentedRoute: SegmentedRoute,
    val profile: TransportProfile,
    val routeEvents: List<RouteEvent> = emptyList(),
    val timetable: Timetable? = null,
    val railPlan: RailPlan? = null,
    /** Cruise speed the schedule engine computed to hit a requested arrival time; overrides the profile's default when set. */
    val scheduledCruiseSpeedKmh: Double? = null,
    val random: Random,
)

/**
 * Turns a route + transport profile + elapsed time into a new kinematic state. Implementations
 * must integrate speed smoothly (no instant speed changes) and must never move faster than
 * physically consistent with the previous tick's speed and the profile's accel/decel limits.
 */
interface MotionModel {
    fun step(current: MotionState, deltaSeconds: Double, context: MotionContext): MotionState
}
