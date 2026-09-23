package com.routesim.app.core.motion

/**
 * Timetable-driven motion model for rail journeys (section 6): accelerate away from a station
 * to the leg's cruise speed, hold it, brake smoothly into the next station, dwell for its
 * scheduled dwell time, repeat. The per-leg cruise speed comes from [MotionContext.railPlan],
 * which [RailPlan.build] derives from [com.routesim.app.core.schedule.ScheduleEngine] so the
 * simulated journey actually lands on its timetable.
 */
class RailMotionModel : MotionModel {

    override fun step(current: MotionState, deltaSeconds: Double, context: MotionContext): MotionState {
        if (deltaSeconds <= 0.0) return current
        val route = context.segmentedRoute
        val profile = context.profile
        val plan = context.railPlan

        if (route.isStationary || plan == null || plan.legs.isEmpty()) {
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

        val legIndex = plan.legIndexAt(current.distanceTraveledMeters)
        val leg = plan.legs[legIndex]
        val isFinalLeg = legIndex == plan.legs.lastIndex

        var targetMps = leg.cruiseSpeedKmh.coerceAtMost(profile.maxSpeedKmh) / 3.6

        val decelMps2 = profile.decelerationMps2
        fun brakingDistanceFor(speed: Double) = (speed * speed) / (2.0 * decelMps2)

        val distanceToLegEnd = leg.endDistanceMeters - current.distanceTraveledMeters
        val brakingHorizon = brakingDistanceFor(current.speedMps.coerceAtLeast(targetMps)) + 5.0
        var approachingStop = false
        if (distanceToLegEnd <= brakingHorizon.coerceAtLeast(1.0)) {
            targetMps = 0.0
            approachingStop = distanceToLegEnd < 1.0
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
            phase = if (isFinalLeg) SimulationPhase.COMPLETED else SimulationPhase.STOPPED
        }

        val averageSpeed = (current.speedMps + newSpeed) / 2.0
        val newDistance = (current.distanceTraveledMeters + averageSpeed * deltaSeconds)
            .coerceIn(0.0, plan.totalDistanceMeters)

        return when (phase) {
            SimulationPhase.COMPLETED -> current.copy(
                distanceTraveledMeters = plan.totalDistanceMeters,
                speedMps = 0.0,
                phase = SimulationPhase.COMPLETED,
                activeIndex = legIndex,
            )
            SimulationPhase.STOPPED -> current.copy(
                distanceTraveledMeters = newDistance,
                speedMps = 0.0,
                phase = SimulationPhase.STOPPED,
                dwellRemainingSeconds = leg.dwellAtArrivalSeconds,
                activeIndex = legIndex,
            )
            else -> current.copy(
                distanceTraveledMeters = newDistance,
                speedMps = newSpeed,
                phase = phase,
                activeIndex = legIndex,
            )
        }
    }
}
