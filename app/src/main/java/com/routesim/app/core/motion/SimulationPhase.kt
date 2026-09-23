package com.routesim.app.core.motion

/** What the simulated vehicle is doing right now — surfaced directly as the sample's `state` field. */
enum class SimulationPhase {
    IDLE,
    ACCELERATING,
    CRUISING,
    BRAKING,
    STOPPED,
    DWELLING,
    PAUSED,
    COMPLETED,
}
