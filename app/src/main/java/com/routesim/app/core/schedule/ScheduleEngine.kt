package com.routesim.app.core.schedule

import com.routesim.app.core.profile.TransportProfile
import com.routesim.app.core.route.SegmentedRoute
import com.routesim.app.core.route.Timetable
import kotlin.math.roundToLong

/**
 * Computes the motion parameters a journey needs to satisfy a requested schedule (section 9).
 * Never fabricates a feasible-looking plan when the numbers don't work out: if the required
 * average speed falls outside what the transport profile can realistically sustain, it reports
 * [ScheduleResult.Infeasible] with the fixed, user-facing reason string instead.
 */
object ScheduleEngine {

    const val INFEASIBLE_REASON = "Requested schedule is inconsistent with route constraints."

    /** Point-to-point (non-timetabled) schedule check: a single start/duration for the whole route. */
    fun evaluate(segmentedRoute: SegmentedRoute, profile: TransportProfile, schedule: ScheduleConfig): ScheduleResult {
        val targetDuration = schedule.effectiveTargetDurationSeconds
            ?: return ScheduleResult.Feasible(
                requiredAverageSpeedKmh = profile.cruiseSpeedKmh,
                recommendedCruiseSpeedKmh = profile.cruiseSpeedKmh,
                estimatedDurationSeconds = estimateNaturalDurationSeconds(segmentedRoute, profile),
            )

        if (targetDuration <= 0L) {
            return ScheduleResult.Infeasible(INFEASIBLE_REASON, requiredAverageSpeedKmh = Double.POSITIVE_INFINITY, maxAchievableAverageSpeedKmh = profile.maxSpeedKmh)
        }

        val totalKm = segmentedRoute.totalDistanceMeters / 1000.0
        if (totalKm <= 0.0) {
            // Stationary simulation: there is no travel to schedule, any positive duration is trivially fine.
            return ScheduleResult.Feasible(0.0, 0.0, targetDuration)
        }

        val requiredAvgSpeedKmh = totalKm / (targetDuration / 3600.0)

        // A margin below max speed accounts for unavoidable acceleration/braking/dwell overhead
        // that a pure average-speed check can't see; going below a "crawl" fraction of cruise
        // speed isn't really driving this profile any more, it's a different journey.
        val maxFeasibleAvgSpeedKmh = profile.maxSpeedKmh * 0.98
        val minFeasibleAvgSpeedKmh = profile.cruiseSpeedKmh * 0.15

        if (requiredAvgSpeedKmh > maxFeasibleAvgSpeedKmh || requiredAvgSpeedKmh < minFeasibleAvgSpeedKmh) {
            return ScheduleResult.Infeasible(INFEASIBLE_REASON, requiredAvgSpeedKmh, maxFeasibleAvgSpeedKmh)
        }

        val recommendedCruiseKmh = requiredAvgSpeedKmh.coerceIn(profile.cruiseSpeedKmh * 0.3, profile.maxSpeedKmh)
        return ScheduleResult.Feasible(requiredAvgSpeedKmh, recommendedCruiseKmh, targetDuration)
    }

    /** Per-station-leg schedule check for a rail (or any timetabled) journey. */
    fun evaluateTimetable(segmentedRoute: SegmentedRoute, profile: TransportProfile, timetable: Timetable): ScheduleResult {
        val maxFeasibleAvgSpeedKmh = profile.maxSpeedKmh * 0.98
        val legs = mutableListOf<LegPlan>()
        var worstRequiredSpeedKmh = 0.0

        for (i in 0 until timetable.stops.size - 1) {
            val from = timetable.stops[i]
            val to = timetable.stops[i + 1]
            val departureOffset = from.departureOffsetSeconds
            val arrivalOffset = to.arrivalOffsetSeconds
            if (departureOffset == null || arrivalOffset == null) {
                return ScheduleResult.Infeasible(INFEASIBLE_REASON, Double.NaN, maxFeasibleAvgSpeedKmh)
            }
            val legDurationSeconds = arrivalOffset - departureOffset
            if (legDurationSeconds <= 0L) {
                return ScheduleResult.Infeasible(INFEASIBLE_REASON, Double.POSITIVE_INFINITY, maxFeasibleAvgSpeedKmh)
            }

            val fromDistance = segmentedRoute.nearestVertexDistanceMeters(from.point)
            val toDistance = segmentedRoute.nearestVertexDistanceMeters(to.point)
            val legDistanceMeters = (toDistance - fromDistance).takeIf { it > 0.0 }
                ?: com.routesim.app.core.geo.GeoMath.distanceMeters(from.point, to.point)

            val requiredKmh = (legDistanceMeters / 1000.0) / (legDurationSeconds / 3600.0)
            worstRequiredSpeedKmh = maxOf(worstRequiredSpeedKmh, requiredKmh)

            if (requiredKmh > maxFeasibleAvgSpeedKmh) {
                return ScheduleResult.Infeasible(INFEASIBLE_REASON, requiredKmh, maxFeasibleAvgSpeedKmh)
            }

            legs += LegPlan(from.name, to.name, legDistanceMeters, legDurationSeconds, requiredKmh)
        }

        return ScheduleResult.Feasible(
            requiredAverageSpeedKmh = worstRequiredSpeedKmh,
            recommendedCruiseSpeedKmh = worstRequiredSpeedKmh.coerceAtMost(profile.maxSpeedKmh),
            estimatedDurationSeconds = timetable.totalScheduledDurationSeconds,
            perLegPlans = legs,
        )
    }

    private fun estimateNaturalDurationSeconds(segmentedRoute: SegmentedRoute, profile: TransportProfile): Long {
        if (segmentedRoute.totalDistanceMeters <= 0.0) return 0L
        val cruiseMps = profile.cruiseSpeedKmh / 3.6
        if (cruiseMps <= 0.0) return 0L
        // Naive travel-time estimate plus a flat overhead fudge factor for accel/decel/stops.
        val travelSeconds = segmentedRoute.totalDistanceMeters / cruiseMps
        val stopOverheadSeconds = (segmentedRoute.totalDistanceMeters / 1000.0) *
            profile.stopFrequencyPerKm * profile.stopProbability *
            ((profile.minDwellSeconds + profile.maxDwellSeconds) / 2.0)
        return (travelSeconds + stopOverheadSeconds).roundToLong()
    }
}
