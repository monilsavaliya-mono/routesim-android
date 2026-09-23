package com.routesim.app.core.profile

import com.routesim.app.core.route.RouteType

/**
 * Built-in transport profiles (section 5). These are the values used out of the box —
 * no external data source is required — and are the baseline a user edits when they
 * "modify a profile" or create a [RouteType.CUSTOM] one.
 */
object TransportProfileDefaults {

    val WALKING = TransportProfile(
        id = "default-walking",
        name = "Walking",
        type = RouteType.WALKING,
        maxSpeedKmh = 6.5,
        cruiseSpeedKmh = 5.0,
        accelerationMps2 = 0.6,
        decelerationMps2 = 0.9,
        speedVariability = 0.15,
        stopFrequencyPerKm = 2.0,
        stopProbability = 0.5,
        minDwellSeconds = 3.0,
        maxDwellSeconds = 25.0,
        curveSpeedFactor = 0.2,
    )

    val CYCLING = TransportProfile(
        id = "default-cycling",
        name = "Cycling",
        type = RouteType.CYCLING,
        maxSpeedKmh = 28.0,
        cruiseSpeedKmh = 18.0,
        accelerationMps2 = 1.0,
        decelerationMps2 = 1.5,
        speedVariability = 0.12,
        stopFrequencyPerKm = 1.2,
        stopProbability = 0.55,
        minDwellSeconds = 5.0,
        maxDwellSeconds = 20.0,
        curveSpeedFactor = 0.45,
    )

    val TAXI = TransportProfile(
        id = "default-taxi",
        name = "Taxi",
        type = RouteType.TAXI,
        maxSpeedKmh = 75.0,
        cruiseSpeedKmh = 45.0,
        accelerationMps2 = 1.8,
        decelerationMps2 = 2.6,
        speedVariability = 0.18,
        stopFrequencyPerKm = 2.5,
        stopProbability = 0.65,
        minDwellSeconds = 5.0,
        maxDwellSeconds = 45.0,
        curveSpeedFactor = 0.8,
    )

    val CAR = TransportProfile(
        id = "default-car",
        name = "Car",
        type = RouteType.CAR,
        maxSpeedKmh = 110.0,
        cruiseSpeedKmh = 75.0,
        accelerationMps2 = 2.4,
        decelerationMps2 = 3.2,
        speedVariability = 0.07,
        stopFrequencyPerKm = 1.0,
        stopProbability = 0.5,
        minDwellSeconds = 5.0,
        maxDwellSeconds = 40.0,
        curveSpeedFactor = 0.75,
    )

    val BUS = TransportProfile(
        id = "default-bus",
        name = "Bus",
        type = RouteType.BUS,
        maxSpeedKmh = 65.0,
        cruiseSpeedKmh = 38.0,
        accelerationMps2 = 1.1,
        decelerationMps2 = 1.7,
        speedVariability = 0.06,
        stopFrequencyPerKm = 3.0,
        stopProbability = 0.85,
        minDwellSeconds = 15.0,
        maxDwellSeconds = 60.0,
        curveSpeedFactor = 0.7,
    )

    val ROAD = TransportProfile(
        id = "default-road",
        name = "Generic Road Vehicle",
        type = RouteType.ROAD,
        maxSpeedKmh = 100.0,
        cruiseSpeedKmh = 65.0,
        accelerationMps2 = 2.0,
        decelerationMps2 = 2.8,
        speedVariability = 0.08,
        stopFrequencyPerKm = 1.2,
        stopProbability = 0.5,
        minDwellSeconds = 5.0,
        maxDwellSeconds = 40.0,
        curveSpeedFactor = 0.75,
    )

    val RAIL = TransportProfile(
        id = "default-rail",
        name = "Rail",
        type = RouteType.RAIL,
        maxSpeedKmh = 130.0,
        cruiseSpeedKmh = 110.0,
        accelerationMps2 = 0.5,
        decelerationMps2 = 0.6,
        speedVariability = 0.02,
        stopFrequencyPerKm = 0.0,
        stopProbability = 0.0,
        minDwellSeconds = 0.0,
        maxDwellSeconds = 0.0,
        curveSpeedFactor = 0.55,
    )

    val CUSTOM_BASELINE = CAR.copy(
        id = "default-custom",
        name = "Custom",
        type = RouteType.CUSTOM,
        isCustom = true,
    )

    val all: List<TransportProfile> = listOf(WALKING, CYCLING, TAXI, CAR, BUS, ROAD, RAIL, CUSTOM_BASELINE)

    fun forType(type: RouteType): TransportProfile = when (type) {
        RouteType.WALKING -> WALKING
        RouteType.CYCLING -> CYCLING
        RouteType.TAXI -> TAXI
        RouteType.CAR -> CAR
        RouteType.BUS -> BUS
        RouteType.ROAD -> ROAD
        RouteType.RAIL -> RAIL
        RouteType.CUSTOM -> CUSTOM_BASELINE
    }
}
