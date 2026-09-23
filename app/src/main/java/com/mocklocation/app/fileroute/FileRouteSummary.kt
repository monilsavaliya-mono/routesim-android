package com.mocklocation.app.fileroute

/** Display-ready metadata for the FILE console tab: "TRAIN · Delhi → Jaipur · 1,842 pts · 08:00–12:15". */
data class FileRouteSummary(
    val name: String,
    val type: String?,
    val pointCount: Int,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
) {
    companion object {
        fun from(route: FileRoute): FileRouteSummary = FileRouteSummary(
            name = route.name,
            type = route.type,
            pointCount = route.points.size,
            distanceMeters = route.totalDistanceMeters,
            durationSeconds = route.durationSeconds,
            startEpochMillis = route.startEpochMillis,
            endEpochMillis = route.endEpochMillis,
        )
    }
}
