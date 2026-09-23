package com.mocklocation.app.fileroute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileRouteValidatorTest {

    private fun point(
        millis: Long,
        lat: Double = 45.0,
        lon: Double = 9.0,
    ) = TimestampedRoutePoint(timestampEpochMillis = millis, latitude = lat, longitude = lon)

    @Test
    fun `an empty point list is rejected as an empty file`() {
        val result = FileRouteValidator.validate("r", null, null, emptyList())
        assertTrue(result is FileRouteParseResult.Error)
        assertEquals("Route file is empty.", (result as FileRouteParseResult.Error).message)
    }

    @Test
    fun `a single point is rejected`() {
        val result = FileRouteValidator.validate("r", null, null, listOf(point(0L)))
        assertTrue(result is FileRouteParseResult.Error)
        assertEquals("Route contains fewer than 2 points.", (result as FileRouteParseResult.Error).message)
    }

    @Test
    fun `two points with valid strictly increasing timestamps succeed`() {
        val result = FileRouteValidator.validate("r", "car", null, listOf(point(0L), point(60_000L)))
        assertTrue(result is FileRouteParseResult.Success)
        assertEquals(2, (result as FileRouteParseResult.Success).route.points.size)
    }

    @Test
    fun `duplicate timestamps are rejected as non-increasing`() {
        val result = FileRouteValidator.validate("r", null, null, listOf(point(0L), point(0L)))
        assertTrue(result is FileRouteParseResult.Error)
        assertEquals("Route timestamps must be strictly increasing.", (result as FileRouteParseResult.Error).message)
    }

    @Test
    fun `out-of-order timestamps are rejected`() {
        val result = FileRouteValidator.validate("r", null, null, listOf(point(60_000L), point(0L)))
        assertTrue(result is FileRouteParseResult.Error)
        assertEquals("Route timestamps must be strictly increasing.", (result as FileRouteParseResult.Error).message)
    }

    @Test
    fun `a non-increasing timestamp deep in a longer list is still caught`() {
        val points = listOf(point(0L), point(10_000L), point(20_000L), point(15_000L), point(30_000L))
        val result = FileRouteValidator.validate("r", null, null, points)
        assertTrue(result is FileRouteParseResult.Error)
    }

    @Test
    fun `latitude outside range is rejected with the offending value in the message`() {
        val result = FileRouteValidator.validate("r", null, null, listOf(point(0L, lat = 128.34), point(1000L)))
        assertTrue(result is FileRouteParseResult.Error)
        assertTrue((result as FileRouteParseResult.Error).message.contains("128.34"))
        assertTrue(result.message.contains("Latitude"))
    }

    @Test
    fun `longitude outside range is rejected`() {
        val result = FileRouteValidator.validate("r", null, null, listOf(point(0L, lon = 200.0), point(1000L)))
        assertTrue(result is FileRouteParseResult.Error)
        assertTrue((result as FileRouteParseResult.Error).message.contains("Longitude"))
    }

    @Test
    fun `NaN coordinates are rejected, not silently accepted`() {
        val result = FileRouteValidator.validate(
            "r", null, null,
            listOf(point(0L, lat = Double.NaN), point(1000L)),
        )
        assertTrue(result is FileRouteParseResult.Error)
    }

    @Test
    fun `infinite coordinates are rejected`() {
        val result = FileRouteValidator.validate(
            "r", null, null,
            listOf(point(0L, lon = Double.POSITIVE_INFINITY), point(1000L)),
        )
        assertTrue(result is FileRouteParseResult.Error)
    }

    @Test
    fun `two coincident points with distinct timestamps are a valid zero-distance route`() {
        // A stationary "route" — nothing in the validator should forbid this; only
        // the geometry/timing layer decides what to do with a zero-length path.
        val result = FileRouteValidator.validate("r", null, null, listOf(point(0L), point(60_000L)))
        assertTrue(result is FileRouteParseResult.Success)
    }

    @Test
    fun `blank name falls back to a default`() {
        val result = FileRouteValidator.validate("   ", null, null, listOf(point(0L), point(1000L)))
        assertTrue(result is FileRouteParseResult.Success)
        assertEquals("Imported route", (result as FileRouteParseResult.Success).route.name)
    }

    @Test
    fun `a very large point count validates without incident`() {
        val points = (0 until 100_000).map { i -> point(i * 1000L, lat = 45.0 + i * 0.00001) }
        val result = FileRouteValidator.validate("big", null, null, points)
        assertTrue(result is FileRouteParseResult.Success)
        assertEquals(100_000, (result as FileRouteParseResult.Success).route.points.size)
    }
}
