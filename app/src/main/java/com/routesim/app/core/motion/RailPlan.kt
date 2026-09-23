package com.routesim.app.core.motion

import com.routesim.app.core.profile.TransportProfile
import com.routesim.app.core.route.SegmentedRoute
import com.routesim.app.core.route.Timetable
import com.routesim.app.core.schedule.ScheduleResult

/** One station-to-station leg of a rail journey, resolved to positions along the route. */
data class RailLeg(
    val fromStationName: String,
    val toStationName: String,
    val startDistanceMeters: Double,
    val endDistanceMeters: Double,
    val cruiseSpeedKmh: Double,
    /** Dwell time at [toStationName] before the next leg departs; 0 for the final leg (journey ends there). */
    val dwellAtArrivalSeconds: Double,
)

data class RailPlan(val legs: List<RailLeg>) {
    val totalDistanceMeters: Double get() = legs.lastOrNull()?.endDistanceMeters ?: 0.0

    /** The leg whose [start, end) range contains [distanceMeters], or the last leg if past the end. */
    fun legAt(distanceMeters: Double): RailLeg? {
        if (legs.isEmpty()) return null
        val leg = legs.firstOrNull { distanceMeters < it.endDistanceMeters }
        return leg ?: legs.last()
    }

    fun legIndexAt(distanceMeters: Double): Int {
        if (legs.isEmpty()) return -1
        val idx = legs.indexOfFirst { distanceMeters < it.endDistanceMeters }
        return if (idx >= 0) idx else legs.lastIndex
    }

    companion object {
        /**
         * Builds a [RailPlan] from a station timetable. Station positions are resolved to the
         * nearest vertex of the route's own geometry (stations are expected to be route
         * waypoints), so leg boundaries always land exactly on the route path. Per-leg cruise
         * speed comes from the schedule engine's feasibility check when one was run, otherwise
         * falls back to the profile's default cruise speed (a schedule-free "just run the
         * timetable's stops and dwell times" mode).
         */
        fun build(
            segmentedRoute: SegmentedRoute,
            timetable: Timetable,
            profile: TransportProfile,
            scheduleResult: ScheduleResult.Feasible? = null,
        ): RailPlan {
            val legPlansByPair = scheduleResult?.perLegPlans?.associateBy { it.fromStop to it.toStop }
            val legs = (0 until timetable.stops.size - 1).map { i ->
                val from = timetable.stops[i]
                val to = timetable.stops[i + 1]
                val startDistance = segmentedRoute.nearestVertexDistanceMeters(from.point)
                var endDistance = segmentedRoute.nearestVertexDistanceMeters(to.point)
                if (endDistance <= startDistance) {
                    // Route geometry didn't resolve monotonically (e.g. stations not on the
                    // supplied geometry) — fall back to even spacing so the plan stays valid.
                    endDistance = segmentedRoute.totalDistanceMeters *
                        (i + 1).toDouble() / (timetable.stops.size - 1).toDouble()
                }
                val cruiseSpeed = legPlansByPair?.get(from.name to to.name)?.requiredCruiseSpeedKmh
                    ?: profile.cruiseSpeedKmh
                RailLeg(
                    fromStationName = from.name,
                    toStationName = to.name,
                    startDistanceMeters = startDistance,
                    endDistanceMeters = endDistance,
                    cruiseSpeedKmh = cruiseSpeed.coerceAtMost(profile.maxSpeedKmh),
                    dwellAtArrivalSeconds = if (i == timetable.stops.size - 2) 0.0 else to.dwellSeconds.toDouble(),
                )
            }
            return RailPlan(legs)
        }
    }
}
