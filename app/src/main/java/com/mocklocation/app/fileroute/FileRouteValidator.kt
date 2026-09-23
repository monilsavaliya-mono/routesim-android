package com.mocklocation.app.fileroute

/**
 * List-level validation shared by the JSON and CSV parsers, once they have turned their
 * rows into [TimestampedRoutePoint]s. Per-row problems (an unparsable timestamp, a
 * missing column) are reported by the parser itself, with the row number, since only it
 * still has that context; this checks the properties of the route as a whole.
 *
 * Never silently repairs the file — an out-of-order or duplicate timestamp is reported,
 * not resorted or dropped, so the imported trajectory is always exactly what the file
 * described.
 */
object FileRouteValidator {

    fun validate(
        name: String,
        type: String?,
        description: String?,
        points: List<TimestampedRoutePoint>,
    ): FileRouteParseResult {
        if (points.isEmpty()) {
            return FileRouteParseResult.Error("Route file is empty.")
        }
        if (points.size < 2) {
            return FileRouteParseResult.Error("Route contains fewer than 2 points.")
        }

        points.forEachIndexed { index, point ->
            val row = index + 1
            if (point.latitude.isNaN() || point.latitude.isInfinite() || point.latitude !in -90.0..90.0) {
                return FileRouteParseResult.Error("Latitude ${point.latitude} at row $row is outside the valid range.")
            }
            if (point.longitude.isNaN() || point.longitude.isInfinite() || point.longitude !in -180.0..180.0) {
                return FileRouteParseResult.Error("Longitude ${point.longitude} at row $row is outside the valid range.")
            }
        }

        for (i in 1 until points.size) {
            if (points[i].timestampEpochMillis <= points[i - 1].timestampEpochMillis) {
                return FileRouteParseResult.Error("Route timestamps must be strictly increasing.")
            }
        }

        return FileRouteParseResult.Success(
            FileRoute(
                name = name.ifBlank { "Imported route" },
                type = type?.takeIf { it.isNotBlank() },
                description = description?.takeIf { it.isNotBlank() },
                points = points,
            )
        )
    }
}
