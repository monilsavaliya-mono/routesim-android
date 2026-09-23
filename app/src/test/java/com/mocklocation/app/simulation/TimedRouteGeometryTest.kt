package com.mocklocation.app.simulation

import com.mocklocation.app.fileroute.FileRoute
import com.mocklocation.app.fileroute.TimestampedRoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-math coverage for the time-domain arc-length parameterisation File Route Playback drives from. */
class TimedRouteGeometryTest {

    private fun point(
        isoMinutesOffset: Long,
        lat: Double,
        lon: Double,
        speedMs: Float? = null,
        bearing: Float? = null,
        altitude: Double? = null,
        accuracy: Float? = null,
    ) = TimestampedRoutePoint(
        timestampEpochMillis = BASE_MILLIS + isoMinutesOffset * 60_000L,
        latitude = lat,
        longitude = lon,
        speedMs = speedMs,
        bearingDegrees = bearing,
        altitudeMeters = altitude,
        accuracyMeters = accuracy,
    )

    // ── The exact scenario from the spec: A=08:00, B=08:10 ─────────

    @Test
    fun `at the first timestamp position is exactly point A`() {
        val a = point(0, 45.0, 9.0)
        val b = point(10, 45.01, 9.01)
        val timed = TimedRouteGeometry(FileRoute("r", points = listOf(a, b)))

        assertEquals(0.0, timed.arcLengthAtElapsedMs(0), 1e-9)
    }

    @Test
    fun `at the halfway elapsed time the distance travelled is half the total`() {
        val a = point(0, 45.0, 9.0)
        val b = point(10, 45.01, 9.01)
        val timed = TimedRouteGeometry(FileRoute("r", points = listOf(a, b)))

        val halfway = timed.arcLengthAtElapsedMs(5 * 60_000L)
        assertEquals(timed.totalDistanceMeters / 2.0, halfway, 1e-6)
    }

    @Test
    fun `at the last timestamp the full distance has been travelled`() {
        val a = point(0, 45.0, 9.0)
        val b = point(10, 45.01, 9.01)
        val timed = TimedRouteGeometry(FileRoute("r", points = listOf(a, b)))

        assertEquals(timed.totalDistanceMeters, timed.arcLengthAtElapsedMs(10 * 60_000L), 1e-6)
    }

    @Test
    fun `elapsed time beyond the end clamps to the total, never extrapolates`() {
        val a = point(0, 45.0, 9.0)
        val b = point(10, 45.01, 9.01)
        val timed = TimedRouteGeometry(FileRoute("r", points = listOf(a, b)))

        assertEquals(timed.totalDistanceMeters, timed.arcLengthAtElapsedMs(999_999L), 1e-6)
        assertEquals(0.0, timed.arcLengthAtElapsedMs(-500L), 1e-6)
    }

    // ── Speed ────────────────────────────────────────────────────

    @Test
    fun `speed is computed from distance over time when the file omits it`() {
        val a = point(0, 45.0, 9.0)
        val b = point(1, 45.001, 9.0) // 1 minute, ~111 m north
        val timed = TimedRouteGeometry(FileRoute("r", points = listOf(a, b)))

        val speed = timed.speedAtElapsedMs(30_000L)
        val expected = timed.totalDistanceMeters / 60.0
        assertEquals(expected, speed, 0.05)
    }

    @Test
    fun `explicit speed is preserved rather than recomputed`() {
        val a = point(0, 45.0, 9.0, speedMs = 12.5f)
        val b = point(10, 45.01, 9.01)
        val timed = TimedRouteGeometry(FileRoute("r", points = listOf(a, b)))

        assertEquals(12.5, timed.speedAtElapsedMs(60_000L), 1e-6)
    }

    @Test
    fun `two coincident points over a time gap report zero speed — a dwell, not an error`() {
        val a = point(0, 45.0, 9.0)
        val b = point(5, 45.0, 9.0) // same coordinates, 5 minutes later
        val c = point(10, 45.02, 9.0)
        val timed = TimedRouteGeometry(FileRoute("r", points = listOf(a, b, c)))

        assertEquals(0.0, timed.speedAtElapsedMs(2 * 60_000L), 1e-9)
        assertEquals(0.0, timed.arcLengthAtElapsedMs(2 * 60_000L), 1e-9)
    }

    // ── Bearing / altitude / accuracy overrides ─────────────────────

    @Test
    fun `bearing override is absent when the file supplies none`() {
        val a = point(0, 45.0, 9.0)
        val b = point(10, 45.01, 9.01)
        val timed = TimedRouteGeometry(FileRoute("r", points = listOf(a, b)))
        assertNull(timed.bearingOverrideAtElapsedMs(0L))
    }

    @Test
    fun `explicit bearing is surfaced as an override`() {
        val a = point(0, 45.0, 9.0, bearing = 271.5f)
        val b = point(10, 45.01, 9.01)
        val timed = TimedRouteGeometry(FileRoute("r", points = listOf(a, b)))
        assertEquals(271.5f, timed.bearingOverrideAtElapsedMs(60_000L)!!, 1e-6f)
    }

    @Test
    fun `altitude interpolates linearly when both bracketing points supply it`() {
        val a = point(0, 45.0, 9.0, altitude = 100.0)
        val b = point(10, 45.01, 9.01, altitude = 200.0)
        val timed = TimedRouteGeometry(FileRoute("r", points = listOf(a, b)))
        assertEquals(150.0, timed.altitudeAtElapsedMs(5 * 60_000L)!!, 1e-6)
    }

    @Test
    fun `altitude is absent when neither bracketing point supplies it`() {
        val a = point(0, 45.0, 9.0)
        val b = point(10, 45.01, 9.01)
        val timed = TimedRouteGeometry(FileRoute("r", points = listOf(a, b)))
        assertNull(timed.altitudeAtElapsedMs(5 * 60_000L))
    }

    @Test
    fun `accuracy override reads the current keyframe's explicit value`() {
        val a = point(0, 45.0, 9.0, accuracy = 4.2f)
        val b = point(10, 45.01, 9.01)
        val timed = TimedRouteGeometry(FileRoute("r", points = listOf(a, b)))
        assertEquals(4.2f, timed.accuracyOverrideAtElapsedMs(0L)!!, 1e-6f)
    }

    // ── Seeking (inverse lookup) ─────────────────────────────────

    @Test
    fun `elapsedMsAtArcLength inverts arcLengthAtElapsedMs`() {
        val a = point(0, 45.0, 9.0)
        val b = point(20, 45.05, 9.05)
        val timed = TimedRouteGeometry(FileRoute("r", points = listOf(a, b)))

        val targetDistance = timed.totalDistanceMeters * 0.5
        val elapsed = timed.elapsedMsAtArcLength(targetDistance)
        val roundTrip = timed.arcLengthAtElapsedMs(elapsed)
        assertEquals(targetDistance, roundTrip, 1.0)
    }

    // ── Larger routes: monotonicity and lookup correctness ────────

    @Test
    fun `distance is monotonically non-decreasing as elapsed time increases across many points`() {
        val points = (0 until 500).map { i -> point(i.toLong(), 45.0 + i * 0.0001, 9.0 + i * 0.0001) }
        val timed = TimedRouteGeometry(FileRoute("r", points = points))

        var previous = -1.0
        var t = 0L
        while (t <= timed.totalDurationMs) {
            val d = timed.arcLengthAtElapsedMs(t)
            assertTrue("distance went backwards at t=$t", d >= previous - 1e-9)
            previous = d
            t += 37_000L // an interval that does not line up with the 1-minute keyframe spacing
        }
    }

    @Test
    fun `a very short two-point route (sub-second) does not divide by zero`() {
        val a = point(0, 45.0, 9.0)
        val b = TimestampedRoutePoint(BASE_MILLIS + 500, 45.001, 9.001)
        val timed = TimedRouteGeometry(FileRoute("r", points = listOf(a, b)))

        assertEquals(0.0, timed.arcLengthAtElapsedMs(0L), 1e-6)
        assertEquals(timed.totalDistanceMeters, timed.arcLengthAtElapsedMs(500L), 1e-6)
    }

    private companion object {
        const val BASE_MILLIS = 1_790_000_000_000L
    }
}
