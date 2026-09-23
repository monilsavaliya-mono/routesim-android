package com.routesim.app.core.schedule

/**
 * User-facing schedule request (section 9): a start time plus either an explicit target
 * duration or an arrival time to derive one from. All fields optional — an unconfigured
 * schedule just means "run at the profile's natural pace," which [ScheduleEngine] always
 * treats as feasible.
 */
data class ScheduleConfig(
    val startEpochMillis: Long? = null,
    val targetDurationSeconds: Long? = null,
    val endEpochMillis: Long? = null,
    val allowRandomDelays: Boolean = true,
    /** Fraction of the leg/journey duration random delay noise may consume, e.g. 0.1 = up to +/-10%. */
    val delayVariabilityFraction: Double = 0.1,
) {
    val effectiveTargetDurationSeconds: Long?
        get() = targetDurationSeconds ?: run {
            val start = startEpochMillis
            val end = endEpochMillis
            if (start != null && end != null && end > start) (end - start) / 1000L else null
        }
}
