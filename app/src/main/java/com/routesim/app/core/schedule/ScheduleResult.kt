package com.routesim.app.core.schedule

/** One resolved station-to-station leg of a feasible timetabled schedule. */
data class LegPlan(
    val fromStop: String,
    val toStop: String,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val requiredCruiseSpeedKmh: Double,
)

/** Outcome of [ScheduleEngine] checking a requested schedule against a route + profile. */
sealed interface ScheduleResult {
    data class Feasible(
        val requiredAverageSpeedKmh: Double,
        val recommendedCruiseSpeedKmh: Double,
        val estimatedDurationSeconds: Long,
        val perLegPlans: List<LegPlan> = emptyList(),
    ) : ScheduleResult

    /**
     * The requested start/end/duration cannot be met without exceeding the profile's speed
     * limits (section 9: "Requested schedule is inconsistent with route constraints.").
     * The engine never silently produces unrealistic movement to force a fit.
     */
    data class Infeasible(
        val reason: String,
        val requiredAverageSpeedKmh: Double,
        val maxAchievableAverageSpeedKmh: Double,
    ) : ScheduleResult
}
