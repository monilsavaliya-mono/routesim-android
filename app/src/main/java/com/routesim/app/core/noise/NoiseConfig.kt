package com.routesim.app.core.noise

import kotlinx.serialization.Serializable

/** DETERMINISTIC reproduces the exact same noise pattern for a given seed; RANDOMIZED never repeats (section 12). */
@Serializable
enum class NoiseMode { DETERMINISTIC, RANDOMIZED }

@Serializable
data class NoiseConfig(
    val mode: NoiseMode = NoiseMode.DETERMINISTIC,
    val seed: Long = 42L,
    val positionNoiseMeters: Double = 3.0,
    val speedNoiseMps: Double = 0.3,
    val bearingNoiseDegrees: Double = 2.0,
    val altitudeNoiseMeters: Double = 1.5,
    val baseAccuracyMeters: Double = 5.0,
    val accuracyVariationMeters: Double = 3.0,
    val driftEnabled: Boolean = true,
    val driftMaxMeters: Double = 4.0,
)
