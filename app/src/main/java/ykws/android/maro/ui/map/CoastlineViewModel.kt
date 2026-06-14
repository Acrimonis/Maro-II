package ykws.android.maro.ui.map

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ykws.android.maro.data.coastline.CoastlineRepository
import ykws.android.maro.data.location.AcquisitionMode
import ykws.android.maro.data.location.AdaptiveGpsPolicy
import ykws.android.maro.data.location.CompassSource
import ykws.android.maro.data.location.GpsLocationSource
import ykws.android.maro.data.model.CoastlineState
import ykws.android.maro.data.model.GenerationProgress
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.Zone300Data
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.data.settings.SettingsManager
import kotlin.math.sqrt
import ykws.android.maro.spatial.SpatialOperations

/** One throttled camera target for GPS auto-follow: where to centre + which way to face. */
data class CameraTarget(val position: LatLng, val bearingDeg: Float)

/**
 * Atomic navigation state for the boat marker + cap arrow.
 * A single data class guarantees Compose reads bearing + speed from the same
 * snapshot frame — no intermediate frame where only one has updated.
 */
data class NavigationState(
    val bearingDeg: Float = 0f,
    val speedKnots: Float? = null,
    val demoSpeedKnots: Float? = null
)

/**
 * ViewModel for the coastline map screen.
 *
 * Bridges [CoastlineRepository] to the Compose UI layer.
 * Call [initCache] once at startup to load coastline (from cache or OSM).
 *
 * Persisted settings (map center, zoom) are loaded synchronously in [init] so that
 * the Compose UI sees the correct position **before** the MapView is created — fixing
 * the race where `LaunchedEffect`-initiated restore ran after the MapView factory.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CoastlineViewModel(
    application: Application,
    private val repository: CoastlineRepository = CoastlineRepository()
) : AndroidViewModel(application) {

    /** Persisted settings — initialised eagerly so StateFlows are seeded directly. */
    private val settingsManager: SettingsManager =
        SettingsManager(application, ZoneConfig.zoneAutoRevealDistanceM, ZoneConfig.zoneAutoRevealTimeS, ZoneConfig.lowDepthWarningMinOpacityPct)

    /** Device GPS + compass sources (framework-only, no Google Play Services) for GPS mode. */
    private val gpsSource: GpsLocationSource = GpsLocationSource(application)
    private val compass: CompassSource = CompassSource(application)

    /** Initial settings snapshot — used to seed StateFlow initial values. */
    private val initialAppSettings: AppSettings = settingsManager.settings.value

    /** Reactive settings StateFlow — bridged to [SettingsManager.settings] after init. */
    val settings: StateFlow<AppSettings> = MutableStateFlow(AppSettings())

    /** Initialise the cache directory and load coastline via cache-aside pattern. */
    fun initCache(context: Context) {
        // Bridge: forward every emission from the manager's flow to our public flow.
        viewModelScope.launch {
            settingsManager.settings.collect { updated ->
                (settings as MutableStateFlow<AppSettings>).value = updated
            }
        }

        repository.setCacheDir(context)
        viewModelScope.launch {
            repository.loadCoastline()

            // Only fall back to coastline centroid if no persisted position was ever saved
            if (initialAppSettings.mapCenterLat.isNaN()) {
                val data = repository.getCoastlineData()
                if (data != null) {
                    val allPoints = data.allSegments.flatMap { it.points }
                    if (allPoints.isNotEmpty()) {
                        val avgLat = allPoints.sumOf { it.lat.toDouble() } / allPoints.size
                        val avgLon = allPoints.sumOf { it.lon.toDouble() } / allPoints.size
                        _mapCenter.value = LatLng(avgLat, avgLon)
                        _isWater.value = repository.isOnWater(avgLat, avgLon)
                    }
                }
            }
        }
    }

    /**
     * Apply a settings change: persist to disk and update the reactive [settings] flow.
     */
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settingsManager.update(transform) ?: run {
            // Fallback: update the local StateFlow even without persistence available
            val current = settings.value
            (settings as MutableStateFlow<AppSettings>).value = transform(current)
        }
    }

    /** UI state: Idle → Loading → Ready | Error. */
    val state: StateFlow<CoastlineState> = repository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoastlineState.Idle)

    /** Progress (phase name + 0–100) during generation. */
    val progress: StateFlow<GenerationProgress> = repository.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GenerationProgress("", 0))

    /** Center point of the map — seeded from persisted settings or default (Cannes). */
    private val _mapCenter: MutableStateFlow<LatLng> = MutableStateFlow(
        if (initialAppSettings.mapCenterLat.isNaN().not())
            LatLng(initialAppSettings.mapCenterLat, initialAppSettings.mapCenterLon)
        else
            LatLng(43.55, 7.00)
    )
    val mapCenter: StateFlow<LatLng> = _mapCenter.asStateFlow()

    /** True if the current map center is on the water side of the coastline. */
    private val _isWater: MutableStateFlow<Boolean> = MutableStateFlow(initialAppSettings.isWater)
    val isWater: StateFlow<Boolean> = _isWater.asStateFlow()

    /** Distance (meters) from the current map center to the nearest coastline point.
     * `null` when no coastline is loaded or distance cannot be computed. */
    private val _distanceToShore: MutableStateFlow<Double?> = MutableStateFlow(
        if (initialAppSettings.distanceToShore.isNaN()) null
        else initialAppSettings.distanceToShore
    )
    val distanceToShore: StateFlow<Double?> = _distanceToShore.asStateFlow()

    /** Current map zoom level (8.0–18.0) — seeded from persisted settings or default 11.0. */
    private val _zoomLevel: MutableStateFlow<Double> = MutableStateFlow(
        if (initialAppSettings.zoomLevel > 0.0) initialAppSettings.zoomLevel else 11.0
    )
    val zoomLevel: StateFlow<Double> = _zoomLevel.asStateFlow()

    /** True when the current map center is inside the 300 m regulatory band. */
    private val _inZone300 = MutableStateFlow(false)
    val inZone300: StateFlow<Boolean> = _inZone300.asStateFlow()

    /** Signed distance (m) to the 300 m boundary (+ outside, − inside); null if unknown. */
    private val _distanceToZone = MutableStateFlow<Double?>(null)
    val distanceToZone: StateFlow<Double?> = _distanceToZone.asStateFlow()

    /** Precomputed 300 m band geometry for the overlay (null until built). */
    val zone300: StateFlow<Zone300Data?> = repository.state
        .map { (it as? CoastlineState.Ready)?.data?.zone300 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ── GPS mode state ──────────────────────────────────────────────────────
    /** Latest GPS fix position (null until first fix). Drives the MapView recenter in GPS mode. */
    private val _gpsPosition = MutableStateFlow<LatLng?>(null)
    val gpsPosition: StateFlow<LatLng?> = _gpsPosition.asStateFlow()

    /**
     * Atomic navigation state (bearing + speed) for the boat marker and cap arrow.
     * Exposed as a single StateFlow so Compose reads all values from the same
     * snapshot frame — eliminates intermediate frames where bearing updated
     * but speed hasn't yet (or vice versa).
     */
    private val _navigationState = MutableStateFlow(NavigationState())
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    /**
     * True when the GPS fix is stale: no fix received within [GPS_STALE_TIMEOUT_MS] or
     * [GpsFix.hasLock] is false (GNSS satellite count below threshold or provider unavailable).
     * The UI uses this to show a "GPS perdu" indicator.
     */
    private val _gpsStale = MutableStateFlow(false)
    val gpsStale: StateFlow<Boolean> = _gpsStale.asStateFlow()

    /** Foreground gate: GPS/compass collectors run only while true (set by the screen lifecycle). */
    private val _gpsActive = MutableStateFlow(false)

    /** elapsedRealtime() of the last valid GPS course — seeds the 3 s compass fallback. */
    private var lastGpsBearingMs = 0L

    /** Stale-fix watchdog: monotonic clock time of the last fix received. 0 = never received. */
    private var lastFixMs = 0L
    /** Stale-fix watchdog: the timeout job — cancelled and re-armed on each valid fix. */
    private var staleWatchdogJob: Job? = null

    // ── Demo-mode pan speed tracking ──────────────────────────────────────────
    private var lastPanLat = 0.0
    private var lastPanLon = 0.0
    private var lastPanMs = 0L
    private var panStopJob: Job? = null

    // ── Zone 300 m proximity auto-reveal state machine ─────────────────────────
    /** True when the user manually hid the 300m zone → auto-reveal is armed. */
    private var zone300ManuallyHidden = false
    /** True when the system auto-revealed the zone — gates the re-hide checks. */
    private var zone300AutoRevealed = false
    /** True once the boat crossed into the band since the last auto-reveal (distinguishes exit vs turn-away). */
    private var bandEnteredSinceReveal = false
    /** Previous [distanceToZone] sample — used to detect "getting closer" vs "moving away". */
    private var lastDistToZone: Double? = null

    /** Called from the screen lifecycle: enable GPS/compass on resume, disable on pause. */
    fun setGpsActive(active: Boolean) {
        _gpsActive.value = active
    }

    /** True while the user is manually panning in GPS mode → auto-follow/-orient paused. */
    private val _autoFollowSuppressed = MutableStateFlow(false)
    val autoFollowSuppressed: StateFlow<Boolean> = _autoFollowSuppressed.asStateFlow()
    private var resumeJob: Job? = null

    // ── Adaptive acquisition + compass gating ───────────────────────────────
    /** Stationary detector (always on): decides ACTIVE vs IDLE fix cadence from movement. */
    private val adaptivePolicy = AdaptiveGpsPolicy()

    /** Current GPS acquisition cadence — folded into the subscription params (rebuilds on change). */
    private val _acquisitionMode = MutableStateFlow(AcquisitionMode.ACTIVE)

    /** Current GPS acquisition mode — exposed for the GPS status icon on the map. */
    val acquisitionMode: StateFlow<AcquisitionMode> = _acquisitionMode.asStateFlow()

    /** True when the compass is needed for heading (no valid GPS course) — gates its registration. */
    private val _needsCompass = MutableStateFlow(true)

    /**
     * Throttled camera stream for GPS auto-follow: latest (position, heading) coalesced to at most
     * [AppSettings.mapRefreshFps] updates/s. Replaces the per-fix `animateTo` (~60 fps burst) — the
     * screen collector applies each tick with one `setCenter` + `mapOrientation` (a single repaint).
     * `sample()` emits nothing while neither position nor heading changes (steady boat ⇒ zero
     * repaints). Gating to GPS mode / pan-suppression stays in the screen collector so manual
     * gestures keep osmdroid's full native rate.
     */
    val cameraUpdates: Flow<CameraTarget> =
        settings
            .map { (1_000L / it.mapRefreshFps.coerceIn(5, 50)).coerceAtLeast(1L) }
            .distinctUntilChanged()
            .flatMapLatest { periodMs ->
                combine(_gpsPosition.filterNotNull(), _navigationState) { p, nav -> CameraTarget(p, nav.bearingDeg) }
                    .sample(periodMs)
            }

    /**
     * Called on each user map touch. Pauses GPS auto-follow + auto-orientation, then resumes the
     * user-configured recenter delay (settings.recenterDelaySeconds, 1–10 s) after the last touch
     * (snaps back to the GPS position, heading-up).
     */
    fun notifyUserInteraction() {
        _autoFollowSuppressed.value = true
        resumeJob?.cancel()
        resumeJob = viewModelScope.launch {
            delay(settings.value.recenterDelaySeconds.coerceIn(1, 10).toLong() * 1_000L)
            _autoFollowSuppressed.value = false
        }
    }

    /** Update heading only past a small threshold so sensor jitter doesn't churn the map. */
    private fun setMapBearing(deg: Float) {
        _navigationState.update { current ->
            val delta = kotlin.math.abs(((deg - current.bearingDeg + 540f) % 360f) - 180f)
            if (delta >= MIN_BEARING_DELTA_DEG) current.copy(bearingDeg = deg)
            else current
        }
    }

    init {
        // ── Shore recompute pipeline (throttled) ───────────────────────────
        // osmdroid fires a scroll event on every frame of a pan/fling (30–60/s);
        // recomputing and emitting on each one floods Compose with recompositions
        // and runs CPU work on the UI thread, causing visible map jank.
        // sample() collapses the stream to ~6–7 updates/s — imperceptible for
        // on-screen text — flowOn moves the work off the main thread, and
        // mapLatest cancels a stale computation when the center moves again.
        _mapCenter
            .sample(SHORE_SAMPLE_INTERVAL_MS)
            .mapLatest { center ->
                val result = repository.distanceToCoast(center.latitude, center.longitude)
                val hasDist = result.distanceMeters != Double.MAX_VALUE
                val distance = if (hasDist) result.distanceMeters else null
                // Reuse the distance just computed instead of querying again.
                val water = repository.isOnWater(
                    center.latitude, center.longitude, result.distanceMeters
                )
                // Zone status derives from the SAME distance — no extra spatial query.
                val inZone = water && hasDist &&
                    result.distanceMeters <= CoastlineRepository.ZONE_DISTANCE_M
                val distToZone = if (hasDist) result.distanceMeters - CoastlineRepository.ZONE_DISTANCE_M else null
                ShoreState(distance, water, inZone, distToZone)
            }
            .flowOn(Dispatchers.Default)
            .onEach { shore ->
                // Only update when we have valid distance data — don't overwrite
                // the persisted initial value with null while coastline loads.
                if (shore.distanceMeters != null) {
                    _distanceToShore.value = shore.distanceMeters
                    _isWater.value = shore.isWater
                }
                _inZone300.value = shore.inZone
                _distanceToZone.value = shore.distToZone
                // Hybrid proximity auto-reveal (distance OR time-to-band) — pure logic in zone300Decision().
                val cfg = settings.value
                // Per-mode opt-out: when auto-show is off for the active mode, leave the band under
                // manual control and reset the decision state so a later re-enable starts clean.
                val autoShowEnabled = if (cfg.gpsMode) cfg.zone300AutoShowGps else cfg.zone300AutoShowDemo
                if (!autoShowEnabled) {
                    zone300AutoRevealed = false
                    bandEnteredSinceReveal = false
                    lastDistToZone = shore.distToZone
                    return@onEach
                }
                // SOG source. GPS: real speed (null before the first fix = unknown). Demo: pan-derived
                // speed; a *paused* map (null pan speed) reads as 0 kn when INSIDE the zone (you've
                // parked there → declutter) but as unknown (null) when OUTSIDE, so pausing to observe
                // the approach never hides the band. Unknown is never treated as stopped/compliant.
                val sogKn = if (cfg.gpsMode) {
                    _navigationState.value.speedKnots
                } else {
                    _navigationState.value.demoSpeedKnots ?: if (shore.inZone) 0f else null
                }
                val decision = zone300Decision(
                    dist = shore.distToZone,
                    prevDist = lastDistToZone,
                    inZone = shore.inZone,
                    sogKn = sogKn,
                    armed = zone300ManuallyHidden,
                    autoRevealed = zone300AutoRevealed,
                    bandEntered = bandEnteredSinceReveal,
                    revealDistM = cfg.zoneAutoRevealDistanceM.toDouble(),
                    revealTimeS = cfg.zoneAutoRevealTimeS.toDouble(),
                    regKn = ZoneConfig.zoneRegulatorySpeedKn.toDouble()
                )
                zone300AutoRevealed = decision.autoRevealed
                bandEnteredSinceReveal = decision.bandEntered
                when (decision.action) {
                    Zone300Action.REVEAL -> settingsManager.update { it.copy(zone300Visible = true) }
                    Zone300Action.HIDE -> settingsManager.update { it.copy(zone300Visible = false) }
                    Zone300Action.NONE -> {}
                }
                lastDistToZone = shore.distToZone
            }
            .launchIn(viewModelScope)

        // ── GPS mode collectors (rules 1–3) ─────────────────────────────────
        // enabled = GPS toggle AND app foreground. flatMapLatest tears down the
        // location/compass listeners the instant either turns off.
        val enabled = combine(
            settings.map { it.gpsMode }.distinctUntilChanged(),
            _gpsActive
        ) { mode, active -> mode && active }.distinctUntilChanged()

        // GPS: keep the fix centered (rule 1) + course-up heading while moving (rule 2).
        // Acquisition cadence = the active preset/sliders, or the idle interval once the adaptive
        // policy reports we're effectively stationary. flatMapLatest re-subscribes the listener
        // (removeUpdates + requestLocationUpdates) whenever on/interval/distance changes.
        val gpsParams = combine(
            enabled,
            settings.distinctUntilChangedBy {
                Triple(it.gpsActiveIntervalSec, it.gpsActiveMinDistanceM, it.adaptiveIdleIntervalSec)
            },
            _acquisitionMode
        ) { on, s, mode ->
            val intervalMs =
                if (mode == AcquisitionMode.IDLE) s.adaptiveIdleIntervalSec * 1_000L
                else s.gpsActiveIntervalSec * 1_000L
            // Use gpsIdleMinDistanceM (default 0) when idle so tiny drifts update position.
            val distM = if (mode == AcquisitionMode.IDLE) s.gpsIdleMinDistanceM else s.gpsActiveMinDistanceM
            GpsParams(on, intervalMs, distM)
        }.distinctUntilChanged()

        gpsParams
            // Debounce rapid settings changes (e.g. slider drag) → coalesce into one re-subscription.
            .debounce(100)
            .flatMapLatest { p ->
                if (p.on) gpsSource.locationUpdates(p.intervalMs, p.minDistanceM) else emptyFlow()
            }
            .onEach { fix ->
                val now = SystemClock.elapsedRealtime()
                _gpsPosition.value = fix.position
                updateMapCenter(fix.position.latitude, fix.position.longitude)

                // ── Stale-fix watchdog ─────────────────────────────────
                // Every valid fix: clear stale flag, arm a timeout that sets stale if no
                // further fix arrives within GPS_STALE_TIMEOUT_MS. Also reset on hasLock = true.
                if (fix.hasLock) {
                    _gpsStale.value = false
                    lastFixMs = now
                    staleWatchdogJob?.cancel()
                    staleWatchdogJob = viewModelScope.launch {
                        delay(GPS_STALE_TIMEOUT_MS)
                        _gpsStale.value = true
                    }
                } else {
                    // No satellite lock → immediately stale
                    _gpsStale.value = true
                }

                // Atomic update: bearing + speed written in a single snapshot so
                // Compose never sees an intermediate frame with mismatched values.
                _navigationState.update { current ->
                    val newSpeed = fix.speedMps?.let { it * MPS_TO_KNOTS }
                    val newBearing = if (fix.hasCourse && fix.bearingDeg != null) {
                        val delta = kotlin.math.abs(((fix.bearingDeg - current.bearingDeg + 540f) % 360f) - 180f)
                        if (delta >= MIN_BEARING_DELTA_DEG) fix.bearingDeg else current.bearingDeg
                    } else {
                        current.bearingDeg
                    }
                    current.copy(bearingDeg = newBearing, speedKnots = newSpeed)
                }
                if (fix.hasCourse && fix.bearingDeg != null) {
                    lastGpsBearingMs = now
                    _needsCompass.value = false                 // GPS course present → compass off
                } else if (now - lastGpsBearingMs > HEADING_FALLBACK_MS) {
                    _needsCompass.value = true                  // no course for 3 s → need compass
                }
                // Always-on adaptive cadence: drop to idle when effectively stationary.
                val s = settings.value
                _acquisitionMode.value = adaptivePolicy.onFix(
                    now, fix.position, fix.speedMps,
                    s.adaptiveWindowSec * 1_000L, s.adaptiveDistanceM.toDouble()
                )
            }
            .catch { e ->
                if (e is SecurityException) {
                    // Permission revoked mid-stream — stop silently
                } else {
                    Log.w(TAG, "GPS flow error", e)
                    _gpsStale.value = true
                }
            }
            .launchIn(viewModelScope)

        // Compass: orient the boat only after HEADING_FALLBACK_MS without a GPS course (rule 3).
        // Registered ONLY while actually needed (_needsCompass) so the magnetometer path is
        // unpowered whenever GPS course is good; the 3 s fallback gives the on→off hysteresis.
        // azimuthUpdates() now samples at SENSOR_DELAY_NORMAL; sample() further caps it to ~5 Hz.
        combine(enabled, _needsCompass) { on, needs -> on && needs }
            .distinctUntilChanged()
            .flatMapLatest { on -> if (on) compass.azimuthUpdates().sample(HEADING_SAMPLE_MS) else emptyFlow() }
            .onEach { azimuth ->
                if (SystemClock.elapsedRealtime() - lastGpsBearingMs > HEADING_FALLBACK_MS) {
                    setMapBearing(azimuth)
                }
            }
            .launchIn(viewModelScope)

        // Leaving GPS mode → clear GPS-derived state so demo pans freely (north-up) and a
        // later re-enable recenters even if the first fix repeats the last coordinates.
        settings.map { it.gpsMode }
            .distinctUntilChanged()
            .onEach { on ->
                if (!on) {
                    _gpsPosition.value = null
                    _navigationState.value = NavigationState()
                    lastGpsBearingMs = 0L
                    adaptivePolicy.reset()
                    _acquisitionMode.value = AcquisitionMode.ACTIVE
                    _needsCompass.value = true
                    _gpsStale.value = false
                    lastFixMs = 0L
                    staleWatchdogJob?.cancel()
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * "Régénérer" button handler.
     * Forces a fresh OSM fetch by deleting the cache file first, then loading.
     */
    fun loadCoastline() {
        viewModelScope.launch {
            repository.refreshCoastline()

            // After loading, recompute water/land for the current map center
            val data = repository.getCoastlineData()
            if (data != null) {
                val allPoints = data.allSegments.flatMap { it.points }
                if (allPoints.isNotEmpty()) {
                    val avgLat = allPoints.sumOf { it.lat.toDouble() } / allPoints.size
                    val avgLon = allPoints.sumOf { it.lon.toDouble() } / allPoints.size
                    _mapCenter.value = LatLng(avgLat, avgLon)
                    _isWater.value = repository.isOnWater(avgLat, avgLon)
                }
            }
        }
    }

    /**
     * "Bande 300 m" button handler. Rebuilds only the 300 m band from the already
     * loaded coastline (no OSM refetch) — fast iteration on band tuning.
     */
    fun regenerateBand() {
        viewModelScope.launch { repository.regenerateBand() }
    }

    /**
     * Toggles the 300m zone overlay visibility. Manages both the manual-hide flag
     * and the auto-reveal flag so the state machine stays consistent.
     */
    fun toggleZone300Visibility() {
        val current = settings.value.zone300Visible
        settingsManager.update { it.copy(zone300Visible = !current) }
        bandEnteredSinceReveal = false
        if (current) { // was visible → now hiding (user manually hid it)
            zone300ManuallyHidden = true
            zone300AutoRevealed = false  // fresh manual hide, reset auto state
        } else { // was hidden → now showing (user manually toggled back on)
            zone300ManuallyHidden = false
            zone300AutoRevealed = false
        }
    }

    /**
     * Cycles the merged zone layer button through None → 300m → Both → Reg Zones.
     * Manages auto-reveal flags when zone300 visibility changes, mirroring the logic
     * in [toggleZone300Visibility].
     */
    fun cycleZoneLayers() {
        val s = settings.value
        val zone300On = s.zone300Visible
        val regOn = s.regulatedZonesVisible
        // Compute next state: None → 300m → Both → Reg → None
        data class LayerState(val z: Boolean, val r: Boolean)
        val current = LayerState(zone300On, regOn)
        val next = when (current) {
            LayerState(false, false) -> LayerState(true, false)  // None → 300m
            LayerState(true, false)  -> LayerState(true, true)   // 300m → Both
            LayerState(true, true)   -> LayerState(false, true)  // Both → Reg
            LayerState(false, true)  -> LayerState(false, false) // Reg → None
            else                     -> LayerState(false, false) // fallback → None
        }
        settingsManager.update { it.copy(zone300Visible = next.z, regulatedZonesVisible = next.r) }
        // Track manual hide/reveal for zone300 auto-reveal state machine
        if (zone300On != next.z) {
            if (next.z) { // was off → now on
                zone300ManuallyHidden = false
                zone300AutoRevealed = false
            } else { // was on → now off (user manually hid it)
                zone300ManuallyHidden = true
                zone300AutoRevealed = false
            }
        }
    }

    /**
     * Toggles the low-depth (<threshold) pink grounding-hazard overlay visibility.
     * Plain on/off — unlike the 300 m band there is no auto-reveal state to manage.
     */
    fun toggleLowDepthWarningVisibility() {
        settingsManager.update { it.copy(lowDepthWarningVisible = !it.lowDepthWarningVisible) }
    }

    /**
     * Toggles the depth colour map + isobath contour overlay visibility.
     */
    fun toggleDepthLayerVisibility() {
        settingsManager.update { it.copy(depthLayerVisible = !it.depthLayerVisible) }
    }

    /**
     * Called whenever the user pans the map or GPS delivers a fix.
     *
     * Records the new center cheaply on the UI thread and persists it so the
     * position survives rotation / app restart; the water/distance recompute is
     * driven by the throttled pipeline in [init] to keep heavy work off the
     * high-frequency scroll path.
     *
     * In demo mode (!gpsMode), also extrapolates pan velocity → simulated speed in knots.
     */
    fun updateMapCenter(latitude: Double, longitude: Double) {
        _mapCenter.value = LatLng(latitude, longitude)
        // Persist only for user-driven moves. GPS auto-follow would write ~1×/s, so it relies on
        // savePosition() at ON_PAUSE (exit) instead. Demo (no gpsMode) persists per pan, as before.
        val userDriven = !settings.value.gpsMode || _autoFollowSuppressed.value
        if (userDriven) {
            settingsManager.update { it.copy(mapCenterLat = latitude, mapCenterLon = longitude) }
        }
        // Demo mode: extrapolate pan velocity → simulated speed in knots (and heading if enabled).
        if (!settings.value.gpsMode) {
            computeDemoSpeed(latitude, longitude)
        } else {
            _navigationState.update { it.copy(demoSpeedKnots = null) }
        }
    }

    /**
     * Computes simulated speed in knots from map pan velocity in demo mode.
     * Uses Haversine distance ÷ elapsed wall-clock time between successive
     * [updateMapCenter] calls. A stop-detection timer clears the speed 500 ms
     * after the last pan event.
     *
     * Bearing/heading in demo mode is set independently via two-finger rotation
     * gesture (see [setDemoBearing]) — this function only computes speed.
     */
    private fun computeDemoSpeed(lat: Double, lon: Double) {
        val now = SystemClock.elapsedRealtime()
        if (lastPanMs != 0L) {
            val elapsed = now - lastPanMs
            // Throttle computation to ~10 Hz to avoid churn from 60 fps scroll events.
            if (elapsed >= MIN_DEMO_SPEED_INTERVAL_MS) {
                val dist = SpatialOperations.haversine(
                    LatLng(lastPanLat, lastPanLon), LatLng(lat, lon)
                )
                // sqrt compression: dampens extreme pan-to-ground ratios while keeping
                // low-speed drags responsive. Slow drag → ~3-6 kn, brisk → ~20-50 kn.
                val rawMps = dist / (elapsed / 1_000.0)
                val knots = sqrt(rawMps * DEMO_SPEED_SCALE).coerceIn(0.0, MAX_DEMO_KNOTS)
                _navigationState.update { it.copy(demoSpeedKnots = knots.toFloat()) }
                lastPanLat = lat
                lastPanLon = lon
                lastPanMs = now
            }
        } else {
            lastPanLat = lat
            lastPanLon = lon
            lastPanMs = now
        }
        // Restart stop-detection: clear speed 500 ms after the last pan event.
        panStopJob?.cancel()
        panStopJob = viewModelScope.launch {
            delay(PAN_STOP_DELAY_MS)
            if (isActive) _navigationState.update { it.copy(demoSpeedKnots = null) }
        }
    }

    /**
     * Sets the bearing from a two-finger rotation gesture in demo mode.
     * Called by [MapScreen]'s touch handler when a rotation is detected.
     * Only effective when [AppSettings.demoHeadingUp] is enabled.
     */
    fun setDemoBearing(deg: Float) {
        if (!settings.value.demoHeadingUp) return
        setMapBearing(deg)
    }

    /**
     * Called whenever the user zooms the map.
     * Captures the zoom level and persists it.
     */
    fun updateZoomLevel(zoom: Double) {
        _zoomLevel.value = zoom
        settingsManager.update { it.copy(zoomLevel = zoom) }
    }

    /**
     * Persists the current map center and zoom to SharedPreferences.
     *
     * Called automatically on every pan/zoom, but also exposed as a
     * lifecycle-aware safety net — call from [androidx.lifecycle.Lifecycle.Event.ON_PAUSE]
     * to guarantee the position survives process kill.
     */
    fun savePosition() {
        val center = _mapCenter.value
        val zoom = _zoomLevel.value
        val water = _isWater.value
        val dist = _distanceToShore.value
        settingsManager.update {
            it.copy(
                mapCenterLat = center.latitude,
                mapCenterLon = center.longitude,
                zoomLevel = zoom,
                isWater = water,
                distanceToShore = dist ?: Double.NaN
            )
        }
    }

    /**
     * Checks if a GPS position is on the water side of the coastline.
     */
    fun isOnWater(latitude: Double, longitude: Double): Boolean =
        repository.isOnWater(latitude, longitude)

    /**
     * Distance and closest point from a GPS position to the coastline.
     */
    fun distanceToCoast(latitude: Double, longitude: Double) =
        repository.distanceToCoast(latitude, longitude)

    /**
     * Distance from a GPS position to the nearest coastline point (meters).
     */
    fun distanceToCoastMeters(latitude: Double, longitude: Double): Double =
        repository.distanceToCoastMeters(latitude, longitude)

    /** GPS subscription parameters — flatMapLatest re-subscribes the listener when any field changes. */
    private data class GpsParams(val on: Boolean, val intervalMs: Long, val minDistanceM: Float)

    /** Result of one throttled recompute: shore distance + water flag + 300 m zone status. */
    private data class ShoreState(
        val distanceMeters: Double?,
        val isWater: Boolean,
        val inZone: Boolean,
        val distToZone: Double?
    )

    companion object {
        /** Sampling interval for the map-center recompute pipeline (~6–7 Hz). */
        private const val SHORE_SAMPLE_INTERVAL_MS = 150L

        /** Time without a GPS course before the compass takes over heading-up (rule 3). */
        private const val HEADING_FALLBACK_MS = 3_000L

        /** Minimum heading change (deg) before the map re-orients — suppresses sensor jitter. */
        private const val MIN_BEARING_DELTA_DEG = 1.0f

        /** Rate-limit for the compass heading (~5 Hz) so turning doesn't churn the map. */
        private const val HEADING_SAMPLE_MS = 200L

        /** Minimum interval (ms) between demo speed computations — throttles the 60 fps scroll stream. */
        private const val MIN_DEMO_SPEED_INTERVAL_MS = 100L

        /** Inactivity timeout (ms) before demo pan speed resets to null. */
        private const val PAN_STOP_DELAY_MS = 500L

        /** Metres-per-second → knots. */
        private const val MPS_TO_KNOTS = 1.943844f

        /** Scale factor for sqrt-compressed demo speed: knots = sqrt(rawMps × this). */
        private const val DEMO_SPEED_SCALE = 0.2
        /** Maximum demo speed in knots — prevents extreme swipes from going off-scale. */
        private const val MAX_DEMO_KNOTS = 50.0

        /** Log tag for this ViewModel. */
        private const val TAG = "CoastlineVM"

        /**
         * Stale-fix watchdog timeout (ms): if no GPS fix arrives within this window, the position
         * is considered stale. Set to max(5s, interval × 3) — 5 s covers the typical idle cadence
         * (6 s) with a small buffer, without being so short that a slow fix rate triggers false alerts.
         */
        private const val GPS_STALE_TIMEOUT_MS = 5_000L

        /**
         * Factory for [CoastlineViewModel] — required because the primary constructor
         * has two parameters (`Application`, `CoastlineRepository` with default), but
         * [AndroidViewModelFactory] only matches single-`Application`-param constructors.
         */
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: error("Cannot create CoastlineViewModel without APPLICATION_KEY")
                return CoastlineViewModel(application as Application) as T
            }
        }
    }
}

/** Outcome of one [zone300Decision] evaluation. */
internal enum class Zone300Action { NONE, REVEAL, HIDE }

/** Action + next flag state from one auto-reveal evaluation (immutable, side-effect-free). */
internal data class Zone300Decision(
    val action: Zone300Action,
    val autoRevealed: Boolean,
    val bandEntered: Boolean
)

private const val KNOTS_TO_MPS = 0.514444      // 1 knot = 0.514444 m/s
private const val CLOSING_EPS_MPS = 0.05       // ignore closing speeds below ~0.1 kn for time-to-band
private const val STOPPED_SPEED_KN = 1.0f      // at/below this SOG the boat is "stopped" → auto-hide, never reveal

/**
 * Pure decision for the 300 m zone proximity auto-reveal — no side effects, unit-testable.
 *
 * Reveal (only while [armed], not already shown, and still OUTSIDE the band): approach-gated
 * **hybrid** — the boat is closing on the band AND is either within [revealDistM] of the band
 * edge OR within [revealTimeS] of it at the current SOG. Re-hide (only while [autoRevealed]) on
 * any of: stopped and no longer closing (SOG ≤ STOPPED_SPEED_KN), compliant inside the band
 * (SOG ≤ [regKn]), exited the band seaward after entering, or retreated past [revealDistM]
 * without ever entering. [armed] persists through an auto-hide, so re-approaching re-reveals.
 *
 * @param dist     signed distance to the 300 m band edge (+ outside, − inside); null if unknown.
 * @param prevDist previous [dist] sample for direction; null on the first tick.
 * @param inZone   true when inside the 300 m band on the water side.
 * @param sogKn    speed over ground (kn); null in demo mode / before the first GPS fix.
 */
internal fun zone300Decision(
    dist: Double?,
    prevDist: Double?,
    inZone: Boolean,
    sogKn: Float?,
    armed: Boolean,
    autoRevealed: Boolean,
    bandEntered: Boolean,
    revealDistM: Double,
    revealTimeS: Double,
    regKn: Double
): Zone300Decision {
    val approaching = dist != null && prevDist != null && dist < prevDist
    val movingAway = dist != null && prevDist != null && dist > prevDist
    val stopped = sogKn != null && sogKn <= STOPPED_SPEED_KN
    val entered = bandEntered || (autoRevealed && dist != null && dist <= 0.0)

    if (!autoRevealed) {
        val sogMps = (sogKn ?: 0f) * KNOTS_TO_MPS
        val timeToBandS =
            if (approaching && dist > 0.0 && sogMps > CLOSING_EPS_MPS) dist / sogMps
            else Double.POSITIVE_INFINITY
        // Reveal while OUTSIDE the band (dist > 0) and closing on it. Speed gates the *time* arm
        // only — a slowly-but-genuinely-approaching boat still reveals via the distance arm.
        // Already inside + manually hidden → respect the hide, the approach warning is moot.
        val reveal = armed && approaching && dist > 0.0 &&
            (dist <= revealDistM || timeToBandS <= revealTimeS)
        return Zone300Decision(
            action = if (reveal) Zone300Action.REVEAL else Zone300Action.NONE,
            autoRevealed = reveal,
            bandEntered = false
        )
    }

    // Stopped hides only once the boat is no longer closing — an approaching boat keeps the alert,
    // which also prevents reveal/hide flapping at very low speed.
    val stoppedAndIdle = stopped && !approaching
    val compliantInside = inZone && sogKn != null && sogKn <= regKn
    val exitedSeaward = entered && dist != null && dist > 0.0 && movingAway
    val retreatedPastMargin = !entered && dist != null && dist > revealDistM && movingAway
    val hide = stoppedAndIdle || compliantInside || exitedSeaward || retreatedPastMargin
    return Zone300Decision(
        action = if (hide) Zone300Action.HIDE else Zone300Action.NONE,
        autoRevealed = !hide,
        bandEntered = if (hide) false else entered
    )
}
