package com.routesim.app.core.route

import com.routesim.app.core.geo.GeoPoint
import kotlinx.serialization.Serializable

/** Where a route's geometry originated. Surfaced in the UI (section 8: SIMULATED / DATA-DRIVEN / LIVE DATA). */
@Serializable
enum class RouteSource {
    LOCAL_DEMO,
    USER_DRAWN,
    IMPORTED_GPX,
    IMPORTED_KML,
    EXTERNAL_PROVIDER,
}

/**
 * Route acquisition is intentionally separate from simulation (section 2): a [Route] is
 * just ordered geometry plus optional scheduling metadata. Nothing here knows how it will
 * be driven — that is [com.routesim.app.core.motion.MotionModel]'s job.
 */
@Serializable
data class Route(
    val id: String,
    val name: String,
    val type: RouteType,
    val points: List<GeoPoint>,
    val waypoints: List<Waypoint> = emptyList(),
    val timetable: Timetable? = null,
    val source: RouteSource = RouteSource.LOCAL_DEMO,
    val speedLimitKmh: Double? = null,
)
