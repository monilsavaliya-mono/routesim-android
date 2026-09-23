package com.routesim.app.core.motion

/** Kinematic state carried tick to tick by a [MotionModel]. */
data class MotionState(
    val distanceTraveledMeters: Double,
    val speedMps: Double,
    val phase: SimulationPhase,
    /** Seconds left sitting still at a stop/station; >0 implies [phase] == DWELLING. */
    val dwellRemainingSeconds: Double = 0.0,
    /** Index into the model's event/leg list the vehicle is currently at or approaching, for diagnostics. */
    val activeIndex: Int = -1,
) {
    companion object {
        fun start() = MotionState(distanceTraveledMeters = 0.0, speedMps = 0.0, phase = SimulationPhase.IDLE)
    }
}
