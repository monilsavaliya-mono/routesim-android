package com.mocklocation.app.fileroute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileRouteJsonParserTest {

    private val parser = FileRouteJsonParser()

    @Test
    fun `parses the canonical example with all optional fields`() {
        val json = """
            {
              "name": "Delhi to Jaipur Test",
              "type": "train",
              "description": "Example route",
              "points": [
                { "timestamp": "2026-09-24T08:00:00+05:30", "latitude": 28.6139, "longitude": 77.2090 },
                { "timestamp": "2026-09-24T09:15:00+05:30", "latitude": 27.1767, "longitude": 78.0081,
                  "altitude": 171.0, "speed": 0.0, "bearing": 90.0, "accuracy": 5.0,
                  "label": "Agra", "stop": true }
              ]
            }
        """.trimIndent()

        val result = parser.parse(json, fallbackName = "fallback")
        assertTrue(result is FileRouteParseResult.Success)
        val route = (result as FileRouteParseResult.Success).route
        assertEquals("Delhi to Jaipur Test", route.name)
        assertEquals("train", route.type)
        assertEquals("Example route", route.description)
        assertEquals(2, route.points.size)

        val second = route.points[1]
        assertEquals(27.1767, second.latitude, 1e-9)
        assertEquals(78.0081, second.longitude, 1e-9)
        assertEquals(171.0, second.altitudeMeters!!, 1e-9)
        assertEquals(0.0f, second.speedMs!!, 1e-6f)
        assertEquals(90.0f, second.bearingDegrees!!, 1e-6f)
        assertEquals(5.0f, second.accuracyMeters!!, 1e-6f)
        assertEquals("Agra", second.label)
        assertTrue(second.isStop)
    }

    @Test
    fun `falls back to the caller-supplied name when the file has none`() {
        val json = """{"points":[
            {"timestamp":"2026-01-01T00:00:00Z","latitude":0,"longitude":0},
            {"timestamp":"2026-01-01T00:01:00Z","latitude":0,"longitude":0}
        ]}"""
        val result = parser.parse(json, fallbackName = "imported.json")
        assertTrue(result is FileRouteParseResult.Success)
        assertEquals("imported.json", (result as FileRouteParseResult.Success).route.name)
    }

    @Test
    fun `optional fields absent stay null rather than defaulting to zero`() {
        val json = """{"points":[
            {"timestamp":"2026-01-01T00:00:00Z","latitude":1,"longitude":2},
            {"timestamp":"2026-01-01T00:01:00Z","latitude":3,"longitude":4}
        ]}"""
        val route = (parser.parse(json, "n") as FileRouteParseResult.Success).route
        assertNull(route.points[0].speedMs)
        assertNull(route.points[0].bearingDegrees)
        assertNull(route.points[0].altitudeMeters)
        assertNull(route.points[0].accuracyMeters)
        assertNull(route.points[0].label)
        assertTrue(!route.points[0].isStop)
    }

    @Test
    fun `station is accepted as an alias for label`() {
        val json = """{"points":[
            {"timestamp":"2026-01-01T00:00:00Z","latitude":1,"longitude":2,"station":"Central"},
            {"timestamp":"2026-01-01T00:01:00Z","latitude":3,"longitude":4}
        ]}"""
        val route = (parser.parse(json, "n") as FileRouteParseResult.Success).route
        assertEquals("Central", route.points[0].label)
    }

    @Test
    fun `malformed JSON is reported, not thrown`() {
        val result = parser.parse("{not valid json", "n")
        assertTrue(result is FileRouteParseResult.Error)
        assertTrue((result as FileRouteParseResult.Error).message.startsWith("Could not parse JSON"))
    }

    @Test
    fun `a missing points array is reported`() {
        val result = parser.parse("""{"name":"x"}""", "n")
        assertTrue(result is FileRouteParseResult.Error)
    }

    @Test
    fun `an empty points array is reported as an empty file`() {
        val result = parser.parse("""{"points":[]}""", "n")
        assertTrue(result is FileRouteParseResult.Error)
        assertEquals("Route file is empty.", (result as FileRouteParseResult.Error).message)
    }

    @Test
    fun `a missing timestamp is reported with its row number`() {
        val json = """{"points":[
            {"latitude":1,"longitude":2},
            {"timestamp":"2026-01-01T00:01:00Z","latitude":3,"longitude":4}
        ]}"""
        val result = parser.parse(json, "n")
        assertTrue(result is FileRouteParseResult.Error)
        assertEquals("Timestamp at row 1 is invalid.", (result as FileRouteParseResult.Error).message)
    }

    @Test
    fun `an unparsable timestamp is reported with its row number`() {
        val json = """{"points":[
            {"timestamp":"2026-01-01T00:00:00Z","latitude":1,"longitude":2},
            {"timestamp":"not-a-date","latitude":3,"longitude":4}
        ]}"""
        val result = parser.parse(json, "n")
        assertTrue(result is FileRouteParseResult.Error)
        assertEquals("Timestamp at row 2 is invalid.", (result as FileRouteParseResult.Error).message)
    }

    @Test
    fun `a missing latitude is reported`() {
        val json = """{"points":[
            {"timestamp":"2026-01-01T00:00:00Z","longitude":2},
            {"timestamp":"2026-01-01T00:01:00Z","latitude":3,"longitude":4}
        ]}"""
        val result = parser.parse(json, "n")
        assertTrue(result is FileRouteParseResult.Error)
        assertEquals("Missing latitude at row 1.", (result as FileRouteParseResult.Error).message)
    }

    @Test
    fun `a missing longitude is reported`() {
        val json = """{"points":[
            {"timestamp":"2026-01-01T00:00:00Z","latitude":1},
            {"timestamp":"2026-01-01T00:01:00Z","latitude":3,"longitude":4}
        ]}"""
        val result = parser.parse(json, "n")
        assertTrue(result is FileRouteParseResult.Error)
        assertEquals("Missing longitude at row 1.", (result as FileRouteParseResult.Error).message)
    }

    @Test
    fun `an epoch-millisecond timestamp is accepted as a fallback format`() {
        val json = """{"points":[
            {"timestamp":"1700000000000","latitude":1,"longitude":2},
            {"timestamp":"1700000060000","latitude":3,"longitude":4}
        ]}"""
        val result = parser.parse(json, "n")
        assertTrue(result is FileRouteParseResult.Success)
        assertEquals(1700000000000L, (result as FileRouteParseResult.Success).route.points[0].timestampEpochMillis)
    }

    @Test
    fun `out-of-order timestamps are rejected end to end`() {
        val json = """{"points":[
            {"timestamp":"2026-01-01T00:01:00Z","latitude":1,"longitude":2},
            {"timestamp":"2026-01-01T00:00:00Z","latitude":3,"longitude":4}
        ]}"""
        val result = parser.parse(json, "n")
        assertTrue(result is FileRouteParseResult.Error)
        assertEquals("Route timestamps must be strictly increasing.", (result as FileRouteParseResult.Error).message)
    }
}
