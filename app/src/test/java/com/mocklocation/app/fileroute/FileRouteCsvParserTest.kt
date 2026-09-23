package com.mocklocation.app.fileroute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileRouteCsvParserTest {

    private val parser = FileRouteCsvParser()

    @Test
    fun `parses the minimal three-column example`() {
        val csv = """
            timestamp,latitude,longitude
            2026-09-24T08:00:00+05:30,28.6139,77.2090
            2026-09-24T08:05:00+05:30,28.6250,77.2200
            2026-09-24T08:15:00+05:30,28.6800,77.3000
        """.trimIndent()

        val result = parser.parse(csv, fallbackName = "sample_route.csv")
        assertTrue(result is FileRouteParseResult.Success)
        val route = (result as FileRouteParseResult.Success).route
        assertEquals("sample_route.csv", route.name)
        assertEquals(3, route.points.size)
        assertEquals(28.6139, route.points[0].latitude, 1e-9)
        assertEquals(77.3000, route.points[2].longitude, 1e-9)
    }

    @Test
    fun `auto-detects optional columns regardless of order`() {
        val csv = """
            label,latitude,speed,timestamp,longitude,stop
            Origin,10.0,1.5,2026-01-01T00:00:00Z,20.0,true
            ,10.1,2.5,2026-01-01T00:01:00Z,20.1,false
        """.trimIndent()

        val route = (parser.parse(csv, "n") as FileRouteParseResult.Success).route
        assertEquals("Origin", route.points[0].label)
        assertTrue(route.points[0].isStop)
        assertEquals(1.5f, route.points[0].speedMs!!, 1e-6f)
        assertEquals(false, route.points[1].isStop)
    }

    @Test
    fun `columns not present are left null, not defaulted`() {
        val csv = "timestamp,latitude,longitude\n2026-01-01T00:00:00Z,1,2\n2026-01-01T00:01:00Z,3,4"
        val route = (parser.parse(csv, "n") as FileRouteParseResult.Success).route
        assertNull(route.points[0].altitudeMeters)
        assertNull(route.points[0].speedMs)
        assertNull(route.points[0].bearingDegrees)
    }

    @Test
    fun `missing required columns is reported`() {
        val csv = "lat,lon\n1,2\n3,4"
        val result = parser.parse(csv, "n")
        assertTrue(result is FileRouteParseResult.Error)
    }

    @Test
    fun `lat and lon header aliases are accepted`() {
        val csv = "timestamp,lat,lon\n2026-01-01T00:00:00Z,1,2\n2026-01-01T00:01:00Z,3,4"
        val result = parser.parse(csv, "n")
        assertTrue(result is FileRouteParseResult.Success)
    }

    @Test
    fun `a row with too few columns is reported with its line number`() {
        val csv = "timestamp,latitude,longitude\n2026-01-01T00:00:00Z,1,2\n2026-01-01T00:01:00Z,3"
        val result = parser.parse(csv, "n")
        assertTrue(result is FileRouteParseResult.Error)
        assertTrue((result as FileRouteParseResult.Error).message.contains("row 3"))
    }

    @Test
    fun `an invalid timestamp is reported with its row number`() {
        val csv = "timestamp,latitude,longitude\nnot-a-date,1,2\n2026-01-01T00:01:00Z,3,4"
        val result = parser.parse(csv, "n")
        assertTrue(result is FileRouteParseResult.Error)
        assertEquals("Timestamp at row 2 is invalid.", (result as FileRouteParseResult.Error).message)
    }

    @Test
    fun `an invalid latitude is reported`() {
        val csv = "timestamp,latitude,longitude\n2026-01-01T00:00:00Z,notanumber,2\n2026-01-01T00:01:00Z,3,4"
        val result = parser.parse(csv, "n")
        assertTrue(result is FileRouteParseResult.Error)
        assertEquals("Missing or invalid latitude at row 2.", (result as FileRouteParseResult.Error).message)
    }

    @Test
    fun `a file with only a header is reported as empty`() {
        val result = parser.parse("timestamp,latitude,longitude", "n")
        assertTrue(result is FileRouteParseResult.Error)
        assertEquals("Route file is empty.", (result as FileRouteParseResult.Error).message)
    }

    @Test
    fun `an entirely empty file is reported as empty`() {
        val result = parser.parse("", "n")
        assertTrue(result is FileRouteParseResult.Error)
        assertEquals("Route file is empty.", (result as FileRouteParseResult.Error).message)
    }

    @Test
    fun `a single data row is reported as fewer than 2 points`() {
        val csv = "timestamp,latitude,longitude\n2026-01-01T00:00:00Z,1,2"
        val result = parser.parse(csv, "n")
        assertTrue(result is FileRouteParseResult.Error)
        assertEquals("Route contains fewer than 2 points.", (result as FileRouteParseResult.Error).message)
    }

    @Test
    fun `CRLF line endings do not break parsing`() {
        val csv = "timestamp,latitude,longitude\r\n2026-01-01T00:00:00Z,1,2\r\n2026-01-01T00:01:00Z,3,4\r\n"
        val result = parser.parse(csv, "n")
        assertTrue(result is FileRouteParseResult.Success)
        assertEquals(2, (result as FileRouteParseResult.Success).route.points.size)
    }
}
