package com.routesim.app.core.profile

import com.routesim.app.core.route.RouteType

/**
 * Source of [TransportProfile]s. The Room-backed implementation (data layer) adds
 * user-saved/edited profiles on top of [TransportProfileDefaults]; nothing in the
 * simulation engine depends on Room directly.
 */
interface TransportProfileProvider {
    suspend fun getProfile(id: String): TransportProfile?
    suspend fun getDefaultProfile(type: RouteType): TransportProfile
    suspend fun getAllProfiles(): List<TransportProfile>
}

/** In-memory provider over the built-in defaults only; used by the core engine's tests and as a fallback. */
class StaticTransportProfileProvider(
    private val profiles: List<TransportProfile> = TransportProfileDefaults.all,
) : TransportProfileProvider {
    override suspend fun getProfile(id: String): TransportProfile? = profiles.find { it.id == id }

    override suspend fun getDefaultProfile(type: RouteType): TransportProfile =
        profiles.find { it.type == type && !it.isCustom } ?: TransportProfileDefaults.forType(type)

    override suspend fun getAllProfiles(): List<TransportProfile> = profiles
}
