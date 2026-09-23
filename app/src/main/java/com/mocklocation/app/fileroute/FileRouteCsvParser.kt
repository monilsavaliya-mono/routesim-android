package com.mocklocation.app.fileroute

/**
 * Parses the simple CSV format: a header row naming its columns, then one row per point.
 * `timestamp`, `latitude` and `longitude` are required (in any order/position); `altitude`,
 * `speed`, `bearing`, `accuracy`, `label` and `stop` are auto-detected when their column is
 * present and ignored otherwise. Quoted fields with embedded commas are not supported —
 * this format is deliberately as simple as the JSON one is expressive.
 */
class FileRouteCsvParser : FileRouteParser {

    override fun parse(text: String, fallbackName: String): FileRouteParseResult {
        val lines = text.lineSequence()
            .map { it.trim().removeSuffix("\r") }
            .filter { it.isNotEmpty() }
            .toList()

        if (lines.isEmpty()) return FileRouteParseResult.Error("Route file is empty.")

        val header = lines.first().split(",").map { it.trim().lowercase() }
        val timestampIdx = header.indexOf("timestamp")
        val latIdx = header.indexOfFirst { it == "latitude" || it == "lat" }
        val lonIdx = header.indexOfFirst { it == "longitude" || it == "lon" || it == "lng" }
        if (timestampIdx < 0 || latIdx < 0 || lonIdx < 0) {
            return FileRouteParseResult.Error("CSV must have timestamp, latitude and longitude columns.")
        }
        val altIdx = header.indexOf("altitude")
        val speedIdx = header.indexOf("speed")
        val bearingIdx = header.indexOf("bearing")
        val accuracyIdx = header.indexOf("accuracy")
        val labelIdx = header.indexOfFirst { it == "label" || it == "station" }
        val stopIdx = header.indexOf("stop")

        val dataRows = lines.drop(1)
        val points = ArrayList<TimestampedRoutePoint>(dataRows.size)
        dataRows.forEachIndexed { i, line ->
            val row = i + 2 // header is row 1, data starts at row 2
            val cols = line.split(",").map { it.trim() }
            if (cols.size < header.size) {
                return FileRouteParseResult.Error("Malformed row at row $row: expected ${header.size} columns, found ${cols.size}.")
            }

            val timestampMillis = FileRouteTimestamps.parse(cols[timestampIdx])
                ?: return FileRouteParseResult.Error("Timestamp at row $row is invalid.")
            val latitude = cols[latIdx].toDoubleOrNull()
                ?: return FileRouteParseResult.Error("Missing or invalid latitude at row $row.")
            val longitude = cols[lonIdx].toDoubleOrNull()
                ?: return FileRouteParseResult.Error("Missing or invalid longitude at row $row.")

            points += TimestampedRoutePoint(
                timestampEpochMillis = timestampMillis,
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = altIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it)?.toDoubleOrNull() },
                speedMs = speedIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it)?.toFloatOrNull() },
                bearingDegrees = bearingIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it)?.toFloatOrNull() },
                accuracyMeters = accuracyIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it)?.toFloatOrNull() },
                label = labelIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it)?.takeIf(String::isNotBlank) },
                isStop = stopIdx.takeIf { it >= 0 }
                    ?.let { cols.getOrNull(it)?.equals("true", ignoreCase = true) } ?: false,
            )
        }

        return FileRouteValidator.validate(fallbackName, null, null, points)
    }
}
