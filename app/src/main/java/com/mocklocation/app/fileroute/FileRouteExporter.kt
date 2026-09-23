package com.mocklocation.app.fileroute

import com.mocklocation.app.model.LatLng
import com.mocklocation.app.model.Route
import com.mocklocation.app.simulation.RouteGeometry
import com.mocklocation.app.simulation.TimedRouteGeometry
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Re-serializes a validated [FileRoute] with every field resolved — speed and bearing
 * computed from neighbouring points wherever the source file left them out — in the same
 * JSON/CSV shapes the importer reads, columns per section: EXPORT. The output is the
 * *normalized* trajectory: re-importing it reproduces the same playback, whether or not
 * the original file supplied every optional field.
 */
object FileRouteExporter {

    private val timestampFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun toJson(fileRoute: FileRoute): String {
        val resolved = resolve(fileRoute)
        val pointsArray = JSONArray()
        resolved.forEach { row ->
            val obj = JSONObject()
            obj.put("timestamp", row.timestamp)
            obj.put("latitude", row.latitude)
            obj.put("longitude", row.longitude)
            row.altitude?.let { obj.put("altitude", it) }
            obj.put("speed", row.speed)
            obj.put("bearing", row.bearing)
            row.accuracy?.let { obj.put("accuracy", it) }
            obj.put("state", row.state)
            row.label?.let { obj.put("label", it) }
            pointsArray.put(obj)
        }
        val root = JSONObject()
        root.put("name", fileRoute.name)
        fileRoute.type?.let { root.put("type", it) }
        fileRoute.description?.let { root.put("description", it) }
        root.put("points", pointsArray)
        return root.toString(2)
    }

    fun toCsv(fileRoute: FileRoute): String {
        val resolved = resolve(fileRoute)
        val sb = StringBuilder("timestamp,latitude,longitude,altitude,speed,bearing,accuracy,state\n")
        resolved.forEach { row ->
            sb.append(row.timestamp).append(',')
                .append(row.latitude).append(',')
                .append(row.longitude).append(',')
                .append(row.altitude ?: "").append(',')
                .append(row.speed).append(',')
                .append(row.bearing).append(',')
                .append(row.accuracy ?: "").append(',')
                .append(row.state).append('\n')
        }
        return sb.toString()
    }

    private data class ResolvedRow(
        val timestamp: String,
        val latitude: Double,
        val longitude: Double,
        val altitude: Double?,
        val speed: Double,
        val bearing: Float,
        val accuracy: Float?,
        val state: String,
        val label: String?,
    )

    private fun resolve(fileRoute: FileRoute): List<ResolvedRow> {
        val timed = TimedRouteGeometry(fileRoute)
        val geometry = RouteGeometry(
            Route(
                points = fileRoute.points.map { LatLng(it.latitude, it.longitude) },
                distanceMeters = timed.totalDistanceMeters,
                durationSeconds = fileRoute.durationSeconds.toDouble(),
            )
        )
        return fileRoute.points.map { point ->
            val elapsedMs = point.timestampEpochMillis - fileRoute.startEpochMillis
            ResolvedRow(
                timestamp = timestampFormatter.format(
                    Instant.ofEpochMilli(point.timestampEpochMillis).atZone(ZoneId.systemDefault())
                ),
                latitude = point.latitude,
                longitude = point.longitude,
                altitude = point.altitudeMeters,
                speed = (point.speedMs ?: timed.speedAtElapsedMs(elapsedMs).toFloat()).toDouble(),
                bearing = point.bearingDegrees ?: geometry.headingAt(timed.arcLengthAtElapsedMs(elapsedMs)),
                accuracy = point.accuracyMeters,
                state = if (point.isStop) "STOP" else "MOVING",
                label = point.label,
            )
        }
    }
}
