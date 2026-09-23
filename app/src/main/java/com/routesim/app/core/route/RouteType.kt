package com.routesim.app.core.route

/** Transport/journey type. Also doubles as the default [com.routesim.app.core.profile.TransportProfile] key. */
enum class RouteType {
    ROAD,
    RAIL,
    WALKING,
    CYCLING,
    BUS,
    TAXI,
    CAR,
    CUSTOM,
}
