package com.routesim.app.core.motion

import kotlin.math.abs

/**
 * Physics-driven motion model shared by every non-rail route type (section 4/5): walking,
 * cycling, taxi, car, bus, generic road and custom all use this model, differentiated purely
 * by the [com.routesim.app.core.profile.TransportProfile] plugged into [MotionContext] — no
 * per-type branching lives here.
 *
 * Each tick: pick a target speed (profile cruise speed, capped by the route's own speed limit
 * and any schedule-derived cruise speed, reduced through curves, reduced to zero on approach to
 * a triggering stop event or the route's end), then move the current speed towards that target
 * at the profile's acceleration/deceleration limit — never an instant jump. Distance is
 * integrated with the trapezoidal average of the tick's start/end speed for a smoother curve
 * than a naive Euler step.
 */
class RoadMotionModel : MotionModel {

    override fun step(current: MotionState, deltaSeconds: Double, context: MotionContext): MotionState {
        if (deltaSeconds <= 0.0) return current
        val route = context.segmentedRoute
        val profile = context.profile

        if (route.isStationary) {
            return current.copy(distanceTraveledMeters = 0.0, speedMps = 0.0, phase = SimulationPhase.COMPLETED)
        }
        if (current.phase == SimulationPhase.COMPLETED) return current

        if (current.dwellRemainingSeconds > 0.0) {
            val remaining = current.dwellRemainingSeconds - deltaSeconds
            return if (remaining > 0.0) {
                current.copy(dwellRemainingSeconds = remaining, phase = SimulationPhase.DWELLING, speedMps = 0.0)
            } else {
                current.copy(dwellRemainingSeconds = 0.0, phase = SimulationPhase.ACCELERATING, speedMps = 0.0)
            }
        }

        val cruiseCeilingKmh = minOf(profile.maxSpeedKmh, route.route.speedLimitKmh ?: Double.MAX_VALUE)
        val baseCruiseKmh = (context.scheduledCruiseSpeedKmh ?: profile.cruiseSpeedKmh).coerceAtMost(cruiseCeilingKmh)
        var targetMps = baseCruiseKmh / 3.6

        val segment = route.segmentContaining(current.distanceTraveledMeters)
        val turnMagnitude = abs(segment.turnIntoSegmentDegrees).coerceAtMost(90.0)
        val curveFactor = (1.0 - profile.curveSpeedFactor * (turnMagnitude / 90.0)).coerceIn(0.2, 1.0)
        targetMps *= curveFactor

        val decelMps2 = profile.decelerationMps2
        fun brakingDistanceFor(speed: Double) = (speed * speed) / (2.0 * decelMps2)

        val nextEvent = context.routeEvents
            .filter { it.willTrigger && it.distanceMeters > current.distanceTraveledMeters }
            .minByOrNull { it.distanceMeters }

        val distanceToRouteEnd = route.totalDistanceMeters - current.distanceTraveledMeters
        val distanceToEvent = nextEvent?.let { it.distanceMeters - current.distanceTraveledMeters }
        val brakingHorizon = brakingDistanceFor(current.speedMps.coerceAtLeast(targetMps)) + 3.0

        var activeIndex = current.activeIndex
        var approachingStop = false

        when {
            distanceToRouteEnd <= brakingHorizon.coerceAtLeast(1.0) -> {
                targetMps = 0.0
                approachingStop = distanceToRouteEnd < 0.5
            }
            distanceToEvent != null && distanceToEvent <= brakingHorizon.coerceAtLeast(1.0) -> {
                targetMps = 0.0
                activeIndex = context.routeEvents.indexOf(nextEvent)
                approachingStop = distanceToEvent < 0.5
            }
        }

        val epsilon = 0.05
        var newSpeed: Double
        var phase: SimulationPhase
        when {
            targetMps > current.speedMps + epsilon -> {
                newSpeed = (current.speedMps + profile.accelerationMps2 * deltaSeconds).coerceAtMost(targetMps)
                phase = SimulationPhase.ACCELERATING
            }
            targetMps < current.speedMps - epsilon -> {
                newSpeed = (current.speedMps - decelMps2 * deltaSeconds).coerceAtLeast(targetMps)
                phase = SimulationPhase.BRAKING
            }
            else -> {
                val jitterRange = targetMps * profile.speedVariability
                val jitter = (context.random.nextDouble() - 0.5) * 2.0 * jitterRange * deltaSeconds.coerceAtMost(1.0)
                newSpeed = (current.speedMps + jitter)
                    .coerceIn((targetMps - jitterRange).coerceAtLeast(0.0), targetMps + jitterRange)
                phase = SimulationPhase.CRUISING
            }
        }

        if (approachingStop && newSpeed <= 0.15) {
            newSpeed = 0.0
            phase = if (distanceToRouteEnd < 0.5) SimulationPhase.COMPLETED else SimulationPhase.STOPPED
        }

        val averageSpeed = (current.speedMps + newSpeed) / 2.0
        val newDistance = (current.distanceTraveledMeters + averageSpeed * deltaSeconds)
            .coerceIn(0.0, route.totalDistanceMeters)

        return when (phase) {
            SimulationPhase.COMPLETED -> current.copy(
                distanceTraveledMeters = route.totalDistanceMeters,
                speedMps = 0.0,
                phase = SimulationPhase.COMPLETED,
                activeIndex = activeIndex,
            )
            SimulationPhase.STOPPED -> current.copy(
                distanceTraveledMeters = newDistance,
                speedMps = 0.0,
                phase = SimulationPhase.STOPPED,
                dwellRemainingSeconds = nextEvent?.dwellSeconds ?: 0.0,
                activeIndex = activeIndex,
            )
            else -> current.copy(
                distanceTraveledMeters = newDistance,
                speedMps = newSpeed,
                phase = phase,
                activeIndex = activeIndex,
            )
        }
    }
}
