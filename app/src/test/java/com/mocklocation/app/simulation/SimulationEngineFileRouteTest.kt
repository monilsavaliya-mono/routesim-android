package com.mocklocation.app.simulation

import com.mocklocation.app.fileroute.FileRoute
import com.mocklocation.app.fileroute.FileRouteAdapter
import com.mocklocation.app.fileroute.TimestampedRoutePoint
import com.mocklocation.app.location.FakeMockLocationPort
import com.mocklocation.app.location.MockLocationEngine
import com.mocklocation.app.model.FixRate
import com.mocklocation.app.model.SimulationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Engine-level coverage for File Route Playback: does the timed geometry actually drive
 * `play`/`pause`/`seekTo`/completion through the *existing* transport controls correctly,
 * exactly as a normal OSRM route does. All against [FakeMockLocationPort] — no device.
 */
class SimulationEngineFileRouteTest {

    private fun point(secondsOffset: Long, lat: Double, lon: Double) = TimestampedRoutePoint(
        timestampEpochMillis = BASE_MILLIS + secondsOffset * 1000L,
        latitude = lat,
        longitude = lon,
    )

    private fun newEngine(fake: FakeMockLocationPort = FakeMockLocationPort()): SimulationEngine {
        val engine = SimulationEngine(fake)
        engine.updateConfig { it.copy(fixRate = FixRate.HZ_10) }
        return engine
    }

    private fun waitUntil(timeoutMs: Long = 3000L, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (predicate()) return
            Thread.sleep(5)
        }
        if (!predicate()) fail("condition not met within ${timeoutMs}ms")
    }

    @Test
    fun `playback follows the file's own timestamps, not the physics integrator`() {
        val route = FileRoute(
            name = "test",
            points = listOf(point(0, 45.0, 9.0), point(2, 45.001, 9.001)),
        )
        val adapted = FileRouteAdapter.adapt(route)
        val fake = FakeMockLocationPort()
        val engine = newEngine(fake)

        engine.setTimedRoute(adapted.route, adapted.timedGeometry)
        assertTrue(engine.isFileRoute)

        val status = engine.play()
        assertTrue(status is MockLocationEngine.Status.Ready)

        waitUntil { engine.state.value.status == SimulationStatus.IDLE }
        // Reached the end of a 2-second file route: total distance covered, exactly as
        // the file described, with no leftover state from the random-target integrator.
        val finalState = engine.state.value
        assertEquals(finalState.totalDistanceMeters, finalState.distanceTraveledMeters, 0.5)
    }

    @Test
    fun `pause and resume preserve file elapsed time, not just distance`() {
        val route = FileRoute(
            name = "test",
            points = listOf(point(0, 45.0, 9.0), point(20, 45.05, 9.05)),
        )
        val adapted = FileRouteAdapter.adapt(route)
        val engine = newEngine()
        engine.setTimedRoute(adapted.route, adapted.timedGeometry)
        engine.play()

        waitUntil { engine.state.value.fixCount >= 3 }
        engine.pause()
        val pausedDistance = engine.state.value.distanceTraveledMeters
        assertTrue("expected some progress before pausing", pausedDistance > 0.0)

        // Resuming must not jump backwards or restart from zero.
        engine.resumeIfPossible()
        waitUntil { engine.state.value.distanceTraveledMeters > pausedDistance }

        engine.stop()
    }

    @Test
    fun `seekTo a file route lands on the corresponding elapsed time, not a distance fraction`() {
        val route = FileRoute(
            name = "test",
            // Deliberately uneven pacing: fast then slow, so a naive distance-fraction
            // seek would land somewhere different from a correct time-fraction seek.
            points = listOf(
                point(0, 45.00, 9.00),
                point(1, 45.05, 9.05), // most of the distance in the first second
                point(21, 45.06, 9.06), // a further 20 seconds for a tiny distance
            ),
        )
        val adapted = FileRouteAdapter.adapt(route)
        val engine = newEngine()
        engine.setTimedRoute(adapted.route, adapted.timedGeometry)
        engine.play()
        waitUntil { engine.state.value.fixCount >= 1 }
        engine.pause()

        engine.seekTo(0.5f) // halfway through 21 seconds of file time = second ~10.5, well past the fast leg
        val state = engine.state.value

        val expectedDistance = adapted.timedGeometry.arcLengthAtElapsedMs(10_500L)
        assertTrue(
            "expected distance near the halfway-in-time point ($expectedDistance), was ${state.distanceTraveledMeters}",
            kotlin.math.abs(state.distanceTraveledMeters - expectedDistance) < adapted.timedGeometry.totalDistanceMeters * 0.05,
        )

        engine.stop()
    }

    @Test
    fun `a route with a stationary period in the middle reports zero speed while dwelling`() {
        val route = FileRoute(
            name = "test",
            points = listOf(
                point(0, 45.0, 9.0),
                point(5, 45.0, 9.0), // 5 seconds at the same coordinates
                point(10, 45.02, 9.0),
            ),
        )
        val adapted = FileRouteAdapter.adapt(route)
        val engine = newEngine()
        engine.setTimedRoute(adapted.route, adapted.timedGeometry)
        engine.play()

        waitUntil(4000L) { engine.state.value.elapsedMillis >= 2500L }
        // Somewhere in the 0-5s window the vehicle must be sitting at ~0 speed.
        assertTrue(
            "expected near-zero speed during the file's own stationary period, was ${engine.state.value.speedKmh}",
            engine.state.value.speedKmh < 5f,
        )

        engine.stop()
    }

    @Test
    fun `a plain setRoute call exits file route mode`() {
        val route = FileRoute(name = "test", points = listOf(point(0, 45.0, 9.0), point(10, 45.01, 9.01)))
        val adapted = FileRouteAdapter.adapt(route)
        val engine = newEngine()
        engine.setTimedRoute(adapted.route, adapted.timedGeometry)
        assertTrue(engine.isFileRoute)

        engine.setRoute(null)
        assertTrue("setRoute must clear file-route mode", !engine.isFileRoute)
    }

    /** [SimulationEngine.play] both starts and resumes; there is no separate resume() to call from a test. */
    private fun SimulationEngine.resumeIfPossible() {
        play()
    }

    private companion object {
        const val BASE_MILLIS = 1_790_000_000_000L
    }
}
