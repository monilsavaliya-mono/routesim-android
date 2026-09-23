package com.mocklocation.app.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mocklocation.app.MockLocationApp
import com.mocklocation.app.data.Preferences
import com.mocklocation.app.fileroute.FileRoute
import com.mocklocation.app.fileroute.FileRouteAdapter
import com.mocklocation.app.fileroute.FileRouteExporter
import com.mocklocation.app.fileroute.FileRouteImporter
import com.mocklocation.app.fileroute.FileRouteParseResult
import com.mocklocation.app.fileroute.FileRouteSummary
import com.mocklocation.app.location.MockLocationEngine
import com.mocklocation.app.model.LatLng
import com.mocklocation.app.model.MapTheme
import com.mocklocation.app.model.MarkerMode
import com.mocklocation.app.model.Route
import com.mocklocation.app.model.SearchSuggestion
import com.mocklocation.app.model.SimulationConfig
import com.mocklocation.app.model.SimulationStatus
import com.mocklocation.app.model.Waypoint
import com.mocklocation.app.network.OsrmRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Owns map/route/search state and forwards transport commands to the
 * process-wide `SimulationEngine`. The engine deliberately lives outside the
 * ViewModel so a run is not tied to this screen's lifetime.
 */
class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = MockLocationApp.engineOf(application)
    private val preferences = Preferences(application)
    private val repository = OsrmRepository()

    // ── Simulation (delegated) ──────────────────────────────────
    val simulation = engine.state
    val config: StateFlow<SimulationConfig> = engine.config
    val providerStatus: StateFlow<MockLocationEngine.Status> = engine.providerStatus

    val elevationProfile: List<Double>? get() = engine.elevationProfile

    /** Where the stops fall along the route, for the progress rail. */
    val stopFractions: List<Float> get() = engine.stopFractions

    // ── Map / route state ───────────────────────────────────────
    private val _startPoint = MutableStateFlow<LatLng?>(null)
    val startPoint: StateFlow<LatLng?> = _startPoint.asStateFlow()

    private val _endPoint = MutableStateFlow<LatLng?>(null)
    val endPoint: StateFlow<LatLng?> = _endPoint.asStateFlow()

    private val _markerMode = MutableStateFlow(MarkerMode.START)
    val markerMode: StateFlow<MarkerMode> = _markerMode.asStateFlow()

    private val _waypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val waypoints: StateFlow<List<Waypoint>> = _waypoints.asStateFlow()

    private val _route = MutableStateFlow<Route?>(null)
    val route: StateFlow<Route?> = _route.asStateFlow()

    private val _isCalculatingRoute = MutableStateFlow(false)
    val isCalculatingRoute: StateFlow<Boolean> = _isCalculatingRoute.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ── File Route ───────────────────────────────────────────────
    private val _fileRouteSummary = MutableStateFlow<FileRouteSummary?>(null)
    val fileRouteSummary: StateFlow<FileRouteSummary?> = _fileRouteSummary.asStateFlow()

    /** The validated import itself, kept only for [exportFileRoute] — everything else drives off [_route]/the engine. */
    private var currentFileRoute: FileRoute? = null

    // ── Camera ──────────────────────────────────────────────────

    /** One-shot camera instruction; [token] makes repeats of the same move fire. */
    sealed interface CameraCommand {
        val token: Long

        data class MoveTo(
            val point: LatLng,
            val zoom: Double?,
            override val token: Long
        ) : CameraCommand

        data class FitRoute(override val token: Long) : CameraCommand
    }

    private val _cameraCommand = MutableStateFlow<CameraCommand?>(null)
    val cameraCommand: StateFlow<CameraCommand?> = _cameraCommand.asStateFlow()

    private val _followVehicle = MutableStateFlow(true)
    val followVehicle: StateFlow<Boolean> = _followVehicle.asStateFlow()

    /** Persisted basemap preference: light, dark, or follow the system. */
    val mapTheme: StateFlow<MapTheme> = preferences.mapTheme

    fun setMapTheme(theme: MapTheme) = preferences.setMapTheme(theme)

    // ── Search ──────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<SearchSuggestion>>(emptyList())
    val searchSuggestions: StateFlow<List<SearchSuggestion>> = _searchSuggestions.asStateFlow()

    private var searchJob: Job? = null
    private var routeJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // ═══ Transport ═════════════════════════════════════════════

    /** Play/pause. Surfaces the precise reason when the platform refuses. */
    fun togglePlay() {
        if (_route.value == null) {
            _errorMessage.value = "Set two points and calculate a route first"
            return
        }
        when (val status = engine.toggle()) {
            is MockLocationEngine.Status.Ready,
            is MockLocationEngine.Status.Idle -> _errorMessage.value = null
            is MockLocationEngine.Status.MissingLocationPermission ->
                _errorMessage.value = "Location permission is required to inject fixes"
            is MockLocationEngine.Status.NotSelectedAsMockApp ->
                _errorMessage.value = "Not selected as mock location app"
            is MockLocationEngine.Status.Failed ->
                _errorMessage.value = "Mock provider failed: ${status.reason}"
        }
    }

    fun stopSimulation() = engine.stop()

    fun seekTo(fraction: Float) = engine.seekTo(fraction)

    fun updateConfig(transform: (SimulationConfig) -> SimulationConfig) =
        engine.updateConfig(transform)

    /**
     * Re-arms the providers after the user fixes permissions in Settings.
     *
     * PLAYING only. isActive also covers PAUSED, and play() resumes — so
     * granting a permission while deliberately paused restarted playback the
     * user had not asked for.
     */
    fun refreshProviderStatus() {
        if (simulation.value.status != SimulationStatus.PLAYING) return
        engine.play()
    }

    // ═══ Map interaction ═══════════════════════════════════════

    fun onMapLongPress(latLng: LatLng) {
        when (_markerMode.value) {
            MarkerMode.START -> _startPoint.value = latLng
            MarkerMode.END -> _endPoint.value = latLng
            MarkerMode.STOP -> _waypoints.value = _waypoints.value + Waypoint(latLng)
        }
        // Advance the mode so a route can be built with repeated long-presses:
        // start → end → stop, stop, stop…
        _markerMode.value = _markerMode.value.next()
        _errorMessage.value = null
        recalculate()
    }

    fun setMarkerMode(mode: MarkerMode) {
        _markerMode.value = mode
    }

    fun cycleMarkerMode() {
        _markerMode.value = when (_markerMode.value) {
            MarkerMode.START -> MarkerMode.END
            MarkerMode.END -> MarkerMode.STOP
            MarkerMode.STOP -> MarkerMode.START
        }
    }

    // ── Intermediate stops ─────────────────────────────────────

    fun removeWaypoint(index: Int) {
        val current = _waypoints.value
        if (index !in current.indices) return
        _waypoints.value = current.filterIndexed { i, _ -> i != index }
        recalculate()
    }

    /** Steps the dwell time through the presets; no re-routing needed. */
    fun cycleWaypointDwell(index: Int) {
        val current = _waypoints.value
        if (index !in current.indices) return
        _waypoints.value = current.mapIndexed { i, wp ->
            if (i == index) wp.cycleDwell() else wp
        }
        // Applied in place. Going through setRoute() would call stop() on the
        // engine, tearing down the providers and rewinding a run in progress —
        // for an edit that does not move the route at all.
        engine.updateStopDwells(_waypoints.value)
    }

    fun clearWaypoints() {
        if (_waypoints.value.isEmpty()) return
        _waypoints.value = emptyList()
        recalculate()
    }

    fun calculateRoute() {
        if (_startPoint.value == null || _endPoint.value == null) {
            _errorMessage.value = "Long-press the map to drop both points"
            return
        }
        recalculate()
    }

    fun swapEndpoints() {
        val start = _startPoint.value
        val end = _endPoint.value
        if (start == null || end == null) return
        _startPoint.value = end
        _endPoint.value = start
        // Stops keep their positions but are met in the opposite order; the
        // engine re-sorts them by arc length, so just reverse for the UI list.
        _waypoints.value = _waypoints.value.reversed()
        recalculate()
    }

    fun clearAll() {
        // Cancel first: an in-flight OSRM response would otherwise land after
        // the clear and reinstate the route, complete with stops for waypoints
        // that no longer exist.
        routeJob?.cancel()
        _isCalculatingRoute.value = false
        engine.stop()
        engine.setRoute(null)
        _startPoint.value = null
        _endPoint.value = null
        _waypoints.value = emptyList()
        _route.value = null
        _errorMessage.value = null
        _fileRouteSummary.value = null
        currentFileRoute = null
        _markerMode.value = MarkerMode.START
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun setFollowVehicle(enabled: Boolean) {
        _followVehicle.value = enabled
    }

    /** Rail shortcut: flips between light and dark, leaving SYSTEM behind. */
    fun toggleMapTheme(systemInDarkMode: Boolean) {
        setMapTheme(
            if (mapTheme.value.isDark(systemInDarkMode)) MapTheme.LIGHT else MapTheme.DARK
        )
    }

    /** Frames the whole route; used by the "fit" control and after routing. */
    fun requestFitRoute() {
        if (_route.value?.points.isNullOrEmpty()) return
        _followVehicle.value = false
        _cameraCommand.value = CameraCommand.FitRoute(System.nanoTime())
    }

    fun recenterOnVehicle() {
        val pos = simulation.value.position ?: _startPoint.value ?: return
        _followVehicle.value = true
        moveCamera(pos, zoom = 17.0)
    }

    private fun moveCamera(point: LatLng, zoom: Double?) {
        _cameraCommand.value = CameraCommand.MoveTo(point, zoom, System.nanoTime())
    }

    // ═══ Routing ═══════════════════════════════════════════════

    /**
     * Re-routes through start → stops → end. A no-op until both endpoints
     * exist, so it is safe to call after every edit.
     */
    private fun recalculate() {
        val start = _startPoint.value ?: return
        val end = _endPoint.value ?: return
        val stops = _waypoints.value
        val points = buildList {
            add(start)
            addAll(stops.map { it.latLng })
            add(end)
        }

        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            _isCalculatingRoute.value = true
            _errorMessage.value = null
            repository.getRoute(points).fold(
                onSuccess = { route ->
                    _route.value = route
                    // A calculated route replaces any file route that was loaded.
                    _fileRouteSummary.value = null
                    currentFileRoute = null
                    // setRoute() stops any run, because the geometry it was
                    // driving no longer exists. Say so rather than letting the
                    // simulation vanish without explanation.
                    val interrupted = simulation.value.isActive
                    engine.setRoute(route, stops)
                    if (interrupted) {
                        _errorMessage.value = "Route changed — simulation stopped"
                    }
                    requestFitRoute()
                },
                onFailure = { e ->
                    _route.value = null
                    engine.setRoute(null)
                    _errorMessage.value = "Routing failed: ${e.localizedMessage ?: "unknown error"}"
                }
            )
            _isCalculatingRoute.value = false
        }
    }

    // ═══ File Route ════════════════════════════════════════════

    /**
     * Reads, parses, validates and installs a route picked via the system document picker.
     * Reuses the exact same pipeline [loadSampleFileRoute] does for a bundled sample, and
     * the exact same [Route]/engine plumbing OSRM routing does — a file route is just a
     * different way to arrive at those two things (section: THIS IS THE KEY DESIGN
     * PRINCIPLE).
     */
    fun importFileRoute(uri: Uri) {
        routeJob?.cancel()
        val resolver = getApplication<Application>().contentResolver
        routeJob = viewModelScope.launch {
            _isCalculatingRoute.value = true
            _errorMessage.value = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: return@runCatching FileRouteParseResult.Error("Could not open the selected file.")
                    val fileName = queryDisplayName(resolver, uri) ?: uri.lastPathSegment ?: "route"
                    FileRouteImporter.parse(fileName, resolver.getType(uri), text)
                }.getOrElse {
                    FileRouteParseResult.Error("Could not read the selected file: ${it.localizedMessage ?: it::class.simpleName}")
                }
            }
            applyFileRouteResult(result)
            _isCalculatingRoute.value = false
        }
    }

    /** Loads one of the bundled demo files under assets/sample_routes/ through the same pipeline. */
    fun loadSampleFileRoute(assetPath: String, displayName: String) {
        routeJob?.cancel()
        val assets = getApplication<Application>().assets
        routeJob = viewModelScope.launch {
            _isCalculatingRoute.value = true
            _errorMessage.value = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = assets.open(assetPath).bufferedReader().use { it.readText() }
                    FileRouteImporter.parse(displayName, mimeType = null, text = text)
                }.getOrElse {
                    FileRouteParseResult.Error("Could not read the sample route: ${it.localizedMessage ?: it::class.simpleName}")
                }
            }
            applyFileRouteResult(result)
            _isCalculatingRoute.value = false
        }
    }

    private fun applyFileRouteResult(result: FileRouteParseResult) {
        when (result) {
            is FileRouteParseResult.Success -> {
                val adapted = FileRouteAdapter.adapt(result.route)
                _route.value = adapted.route
                _fileRouteSummary.value = FileRouteSummary.from(result.route)
                currentFileRoute = result.route
                // The file is now the authoritative trajectory; manual start/end/stop
                // editing state belongs to the OSRM planner, not to this route.
                _startPoint.value = null
                _endPoint.value = null
                _waypoints.value = emptyList()
                engine.setTimedRoute(adapted.route, adapted.timedGeometry)
                requestFitRoute()
            }
            is FileRouteParseResult.Error -> {
                _errorMessage.value = result.message
            }
        }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        val cursor = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        }.getOrNull() ?: return null
        cursor.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return it.getString(index)
            }
        }
        return null
    }

    /**
     * Writes the currently-loaded file route's normalized trajectory (section: EXPORT) to a
     * location the user picked via `CreateDocument`. Works on any loaded file route, not
     * just one mid-playback — this exports the source trajectory, not a live sample log.
     */
    fun exportFileRoute(uri: Uri, asJson: Boolean) {
        val fileRoute = currentFileRoute
        if (fileRoute == null) {
            _errorMessage.value = "No file route loaded to export."
            return
        }
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    val text = if (asJson) FileRouteExporter.toJson(fileRoute) else FileRouteExporter.toCsv(fileRoute)
                    resolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("Could not open the destination file.")
                }.exceptionOrNull()
            }
            if (error != null) {
                _errorMessage.value = "Export failed: ${error.localizedMessage ?: error::class.simpleName}"
            }
        }
    }

    // ═══ Search ════════════════════════════════════════════════

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchSuggestions.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(320)
            _isSearching.value = true
            runCatching { geocode(query.trim(), limit = 6) }
                .onSuccess { _searchSuggestions.value = it }
            _isSearching.value = false
        }
    }

    fun searchExplicit(query: String) {
        if (query.isBlank()) return
        searchJob?.cancel()
        viewModelScope.launch {
            _isSearching.value = true
            _errorMessage.value = null
            runCatching { geocode(query.trim(), limit = 1) }
                .onSuccess { results ->
                    val hit = results.firstOrNull()
                    if (hit == null) {
                        _errorMessage.value = "No match for \"$query\""
                    } else {
                        selectSuggestion(hit)
                    }
                }
                .onFailure { _errorMessage.value = "Search failed: ${it.localizedMessage}" }
            _isSearching.value = false
        }
    }

    fun selectSuggestion(suggestion: SearchSuggestion) {
        _followVehicle.value = false
        moveCamera(LatLng(suggestion.latitude, suggestion.longitude), zoom = 15.5)
        _searchQuery.value = suggestion.shortName
        _searchSuggestions.value = emptyList()
    }

    fun clearSuggestions() {
        _searchSuggestions.value = emptyList()
    }

    private suspend fun geocode(query: String, limit: Int): List<SearchSuggestion> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=$limit")
                .header("User-Agent", "MockLocation/2.0 (Android)")
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) return@use emptyList()
                val arr = JSONArray(body)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    SearchSuggestion(
                        displayName = obj.getString("display_name"),
                        latitude = obj.getDouble("lat"),
                        longitude = obj.getDouble("lon")
                    )
                }
            }
        }
}
