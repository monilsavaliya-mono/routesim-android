package com.mocklocation.app.fileroute

import org.json.JSONException
import org.json.JSONObject

/**
 * Parses the canonical File Route JSON shape:
 * ```
 * { "name": "...", "type": "train", "points": [
 *     { "timestamp": "...", "latitude": .., "longitude": .., ... }, ...
 * ] }
 * ```
 * `name` and `points[].timestamp/latitude/longitude` are required; everything else
 * (`type`, `description`, `altitude`, `speed`, `bearing`, `accuracy`, `label`, `stop`,
 * `station`) is optional and preserved when present.
 */
class FileRouteJsonParser : FileRouteParser {

    override fun parse(text: String, fallbackName: String): FileRouteParseResult {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return FileRouteParseResult.Error("Could not parse JSON: ${e.message}")
        }

        val name = if (root.has("name")) root.optString("name", fallbackName) else fallbackName
        val type = if (root.has("type")) root.optString("type") else null
        val description = if (root.has("description")) root.optString("description") else null

        val pointsArray = root.optJSONArray("points")
            ?: return FileRouteParseResult.Error("JSON route must contain a \"points\" array.")
        if (pointsArray.length() == 0) {
            return FileRouteParseResult.Error("Route file is empty.")
        }

        val points = ArrayList<TimestampedRoutePoint>(pointsArray.length())
        for (i in 0 until pointsArray.length()) {
            val row = i + 1
            val obj = pointsArray.optJSONObject(i)
                ?: return FileRouteParseResult.Error("Malformed point at row $row.")

            if (!obj.has("timestamp")) {
                return FileRouteParseResult.Error("Timestamp at row $row is invalid.")
            }
            val timestampMillis = FileRouteTimestamps.parse(obj.optString("timestamp"))
                ?: return FileRouteParseResult.Error("Timestamp at row $row is invalid.")

            if (!obj.has("latitude")) return FileRouteParseResult.Error("Missing latitude at row $row.")
            if (!obj.has("longitude")) return FileRouteParseResult.Error("Missing longitude at row $row.")
            val latitude = obj.optDouble("latitude", Double.NaN)
            val longitude = obj.optDouble("longitude", Double.NaN)
            if (latitude.isNaN()) return FileRouteParseResult.Error("Missing or invalid latitude at row $row.")
            if (longitude.isNaN()) return FileRouteParseResult.Error("Missing or invalid longitude at row $row.")

            points += TimestampedRoutePoint(
                timestampEpochMillis = timestampMillis,
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = if (obj.has("altitude")) obj.optDouble("altitude").takeUnless { it.isNaN() } else null,
                speedMs = if (obj.has("speed")) obj.optDouble("speed").takeUnless { it.isNaN() }?.toFloat() else null,
                bearingDegrees = if (obj.has("bearing")) obj.optDouble("bearing").takeUnless { it.isNaN() }?.toFloat() else null,
                accuracyMeters = if (obj.has("accuracy")) obj.optDouble("accuracy").takeUnless { it.isNaN() }?.toFloat() else null,
                label = when {
                    obj.has("label") -> obj.optString("label").takeIf { it.isNotBlank() }
                    obj.has("station") -> obj.optString("station").takeIf { it.isNotBlank() }
                    else -> null
                },
                isStop = obj.optBoolean("stop", false),
            )
        }

        return FileRouteValidator.validate(name, type, description, points)
    }
}
