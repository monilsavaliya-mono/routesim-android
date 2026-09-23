package com.routesim.app.core.noise

import com.routesim.app.core.geo.GeoMath
import com.routesim.app.core.geo.GeoPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.random.Random

/** The "clean" (noise-free) motion the simulation engine computed for this tick. */
data class CleanSample(
    val point: GeoPoint,
    val speedMps: Double,
    val bearingDegrees: Double,
)

/** The sample after realistic GPS noise/inaccuracy is applied — what actually gets emitted. */
data class NoisySample(
    val point: GeoPoint,
    val speedMps: Double,
    val bearingDegrees: Double,
    val accuracyMeters: Float,
)

interface GpsNoiseModel {
    fun apply(clean: CleanSample): NoisySample

    /** Re-seeds (DETERMINISTIC) or re-randomizes (RANDOMIZED) the model, e.g. when a simulation restarts. */
    fun reset()
}

/**
 * Configurable position/speed/bearing/altitude/accuracy noise plus a slow-drifting offset,
 * matching a real GNSS receiver's mix of fast jitter and a wandering fix (section 12).
 * DETERMINISTIC mode seeds a fixed [kotlin.random.Random] so a run is byte-for-byte
 * reproducible; RANDOMIZED mode seeds from the wall clock so it never repeats.
 */
class ConfigurableGpsNoiseModel(private val config: NoiseConfig) : GpsNoiseModel {

    private var random: Random = createRandom()
    private var driftNorthMeters = 0.0
    private var driftEastMeters = 0.0

    private fun createRandom(): Random = when (config.mode) {
        NoiseMode.DETERMINISTIC -> Random(config.seed)
        NoiseMode.RANDOMIZED -> Random(System.nanoTime())
    }

    override fun reset() {
        random = createRandom()
        driftNorthMeters = 0.0
        driftEastMeters = 0.0
    }

    override fun apply(clean: CleanSample): NoisySample {
        if (config.driftEnabled) {
            driftNorthMeters = (driftNorthMeters + (random.nextDouble() - 0.5) * 0.3)
                .coerceIn(-config.driftMaxMeters, config.driftMaxMeters)
            driftEastMeters = (driftEastMeters + (random.nextDouble() - 0.5) * 0.3)
                .coerceIn(-config.driftMaxMeters, config.driftMaxMeters)
        }

        val jitterNorth = (random.nextDouble() - 0.5) * 2.0 * config.positionNoiseMeters + driftNorthMeters
        val jitterEast = (random.nextDouble() - 0.5) * 2.0 * config.positionNoiseMeters + driftEastMeters
        val noisyPoint = offsetByMeters(clean.point, jitterNorth, jitterEast)

        val speed = (clean.speedMps + (random.nextDouble() - 0.5) * 2.0 * config.speedNoiseMps).coerceAtLeast(0.0)
        val bearing = GeoMath.normalizeBearing(
            clean.bearingDegrees + (random.nextDouble() - 0.5) * 2.0 * config.bearingNoiseDegrees,
        )
        val altitude = clean.point.altitude?.let { it + (random.nextDouble() - 0.5) * 2.0 * config.altitudeNoiseMeters }
        val accuracy = (config.baseAccuracyMeters + random.nextDouble() * config.accuracyVariationMeters).toFloat()

        return NoisySample(noisyPoint.copy(altitude = altitude), speed, bearing, accuracy)
    }

    private fun offsetByMeters(point: GeoPoint, northMeters: Double, eastMeters: Double): GeoPoint {
        val dLatDegrees = northMeters / GeoMath.EARTH_RADIUS_METERS * (180.0 / PI)
        val metersPerDegreeLon = GeoMath.EARTH_RADIUS_METERS * cos(Math.toRadians(point.latitude)) * (PI / 180.0)
        val dLonDegrees = if (metersPerDegreeLon > 1.0) eastMeters / metersPerDegreeLon else 0.0
        return GeoPoint(
            latitude = (point.latitude + dLatDegrees).coerceIn(-90.0, 90.0),
            longitude = GeoMath.normalizeLongitude(point.longitude + dLonDegrees),
            altitude = point.altitude,
        )
    }
}
