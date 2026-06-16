
package ykws.android.maro.ui.map
import ykws.android.maro.config.AppConfig

import android.app.Application
import kotlin.math.abs
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
import ykws.android.maro.data.regulation.RegulatedZoneSet
import ykws.android.maro.data.regulation.RegulatedZonesRepository
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.data.regulation.SpeedZoneBuilder
import ykws.android.maro.data.regulation.SpeedZoneQuery
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.data.settings.SettingsManager
import ykws.android.maro.BuildConfig
import ykws.android.maro.spatial.SpeedZoneIndex
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.sqrt
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.atan2

/** One throttled camera target for GPS auto-follow: where to centre + which way to face. */
data class CameraTarget(val position: LatLng, val bearingDeg: Float)

/**
 * Atomic navigation state for the boat marker + cap arrow.
 * A single data class guarantees Compose reads bearing + speed from the same
 * snapshot frame — no intermediate frame where only one has updated.
 *
 * @property bearingDeg       GPS/compass heading (degrees, 0-360). Used for map orientation in GPS mode.
 * @property speedKnots       GPS speed over ground (knots).
 * @property demoSpeedKnots   Demo-mode pan speed (knots), null when map is stationary.
 * @property demoBearingDeg   Demo-mode pan direction (degrees, 0-360), null when map is stationary.
 *                            Separate from [bearingDeg] so demo panning doesn't rotate the map.
 */
data class NavigationState(
    val bearingDeg: Float = 0f,
    val speedKnots: Float? = null,
    val demoSpeedKnots: Float? = null,
    val demoBearingDeg: Float? = null
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
        SettingsManager(application, AppConfig.zoneAutoRevealDistanceM, AppConfig.zoneAutoRevealTimeS, AppConfig.overlayLowDepthMinOpacity)

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
        // Also load regulated zones (best-effort — gracefully degrades if no asset)
        regulatedZonesRepository.load(context)
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

    /** Raw map center — emits at scroll rate (~60 fps) for internal pipeline use. */
    val mapCenter: StateFlow<LatLng> = _mapCenter.asStateFlow()

    /** Throttled map center (~3 Hz) for Compose UI consumption — prevents 60 fps recomposition during drag. */
    val uiMapCenter: StateFlow<LatLng> = _mapCenter
        .sample(333L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _mapCenter.value)

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

    // ── Regulated zones / speed zone state ─────────────────────────────

    /** Repository for prebaked regulated zone data (loaded from APK assets). */
    private val regulatedZonesRepository: RegulatedZonesRepository = RegulatedZonesRepository()

    /** Speed zone spatial index — built once when both data sources are ready. */
    private val _speedZoneIndex = MutableStateFlow<SpeedZoneIndex?>(null)

    /** Unified zone situation: current zone, heading-ahead zone, and nearby zones — replaces 7 individual StateFlows. */
    private val _zoneSituation = MutableStateFlow<ZoneSituation?>(null)
    val zoneSituation: StateFlow<ZoneSituation?> = _zoneSituation.asStateFlow()

    /** Elapsed realtime of the last settings persist during drag — throttles SharedPreferences writes + Compose recomposition from ~60 fps to ~1 Hz. */
    private var lastPersistMs = 0L

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
    /** Last scroll event timestamp — replaces launched coroutine stop-detector to avoid 60 coroutine creations/s. */
    private var lastScrollMs = 0L

    // ── Zone 300 m proximity auto-reveal state machine ─────────────────────────
    /** True when the user manually hid the 300m zone → auto-reveal is armed. */
    private var zone300ManuallyHidden = false
    /** True when the system auto-revealed the zone — gates the re-hide checks. */
    private var zone300AutoRevealed = false
    /** True once the boat crossed into the band since the last auto-reveal (distinguishes exit vs turn-away). */
    private var bandEnteredSinceReveal = false
    /** Previous [distanceToZone] sample — used to detect "getting closer" vs "moving away". */
    private var lastDistToZone: Double? = null

    // ── Speed zone proximity auto-reveal state machine ────────────────────────
    /** True when the user manually hid the speed zones → auto-reveal is armed. */
    private var speedZoneManuallyHidden = false
    /** True when the system auto-revealed speed zones — gates the re-hide checks. */
    private var speedZoneAutoRevealed = false
    /** Previous distance to speed zone — used to detect "getting closer" vs "moving away". */
    private var lastDistToSpeedZone: Double? = null

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

                // Speed zone query
                val szIndex = _speedZoneIndex.value
                val szQuery = if (szIndex != null && water) {
                    val q = szIndex.query(center.latitude, center.longitude)
                    Log.d(TAG, "SZI query at (${"%.4f".format(center.latitude)}, ${"%.4f".format(center.longitude)}): " +
                            "insideAny=${q.insideAnyZone} nearest=${q.nearestZone?.name ?: "null"} " +
                            "distToBoundary=${q.distanceToBoundaryM?.let { "%.1f".format(it) } ?: "null"} " +
                            "mostRestrictive=${q.mostRestrictiveSpeedKn?.let { "%.1f".format(it) } ?: "null"} " +
                            "nInside=${q.allInsideZones.size} hasData=${szIndex.hasData}")
                    q
                } else {
                    Log.d(TAG, "SZI query SKIPPED: index=${szIndex != null} water=$water")
                    SpeedZoneQuery()
                }

                // ── Unified zone situation (computed on background via flowOn) ──
                val navH = _navigationState.value
                val cfgH = settings.value
                val sogKnH = if (cfgH.gpsMode) navH.speedKnots
                    else navH.demoSpeedKnots ?: if (water) 0f else null
                val headingDegH = if (cfgH.gpsMode) navH.bearingDeg.toDouble()
                    else navH.demoBearingDeg?.toDouble() ?: 0.0

                val maxSearchM = BuildConfig.SPEED_ZONE_MAX_SEARCH_M
                val szIdx = _speedZoneIndex.value
                val zoneSituation = if (distance != null) {
                    val currentZone = if (szQuery.insideAnyZone && szQuery.mostRestrictiveSpeedKn != null) {
                        // INSIDE a SHOM speed zone — prefer it as currentZone over the 300m band
                        // so the distance tile shows exit info for the regulatory zone, not the
                        // 300m band (which may have a different beyond-type).
                        val zone = szQuery.allInsideZones.firstOrNull()
                        if (zone != null) {
                            val dist = abs(szQuery.distanceToBoundaryM ?: 0.0)
                            ZoneBoundaryInfo(
                                distanceM = dist,
                                zoneName = zone.name,
                                speedLimitKn = szQuery.mostRestrictiveSpeedKn!!,
                                currentSpeedKnots = sogKnH,
                                isCompliant = sogKnH == null || sogKnH < szQuery.mostRestrictiveSpeedKn!!.toFloat(),
                                beyondType = BeyondType.OPEN_SEA,
                            )
                        } else null
                    } else if (inZone) {
                        // Inside the 300m band (no SHOM zone) — compute exit via ray-march.
                        infoToZoneExitAlongHeading(
                            center.latitude, center.longitude,
                            headingDegH, distance, sogKnH, szIdx, maxSearchM
                        ) ?: run {
                            // Fallback: exit not found along heading — create from query data.
                            val q = szQuery
                            if (q.insideAnyZone && q.mostRestrictiveSpeedKn != null) {
                                val z = q.allInsideZones.firstOrNull()
                                if (z != null) {
                                    val d = abs(q.distanceToBoundaryM ?: 0.0)
                                    ZoneBoundaryInfo(
                                        distanceM = d, zoneName = z.name,
                                        speedLimitKn = q.mostRestrictiveSpeedKn!!,
                                        currentSpeedKnots = sogKnH,
                                        isCompliant = sogKnH == null || sogKnH < q.mostRestrictiveSpeedKn!!.toFloat(),
                                        beyondType = BeyondType.OPEN_SEA,
                                    )
                                } else null
                            } else null
                        }
                    } else null

                    val zones = zonesAroundBoat(
                        center.latitude, center.longitude,
                        headingDegH, distance, sogKnH, szIdx, maxSearchM,
                        excludeZoneName = currentZone?.zoneName
                    )
                    val zonesAround = zones
                    ZoneSituation(currentZone = currentZone, zonesAround = zonesAround)
                } else null

                ShoreState(
                    distanceMeters = distance,
                    isWater = water,
                    inZone = inZone,
                    distToZone = distToZone,
                    speedZoneQuery = szQuery,
                    zoneSituation = zoneSituation
                )
            }
            .flowOn(Dispatchers.Default)
            .onEach { shore ->
                // Only update when we have valid distance data — don't overwrite
                // the persisted initial value with null while coastline loads.
                if (shore.distanceMeters != null) {
                    _distanceToShore.value = shore.distanceMeters
                    _isWater.value = shore.isWater
                    // ── Zone situation — only update alongside valid distance ──
                    _zoneSituation.value = shore.zoneSituation
                }
                _inZone300.value = shore.inZone
                _distanceToZone.value = shore.distToZone

                val cfg = settings.value

                // ── SOG source (shared by auto-show) ─────────────────────────
                val sogKn = if (cfg.gpsMode) {
                    _navigationState.value.speedKnots
                } else {
                    _navigationState.value.demoSpeedKnots ?: if (shore.inZone) 0f else null
                }

                // ── 300m zone auto-show (uses generalized decision) ─────────
                val autoShowEnabled = if (cfg.gpsMode) cfg.zone300AutoShowGps else cfg.zone300AutoShowDemo
                if (!autoShowEnabled) {
                    zone300AutoRevealed = false
                    bandEnteredSinceReveal = false
                    lastDistToZone = shore.distToZone
                    return@onEach
                }
                val bandDecision = zoneAutoShowDecision(
                    dist = shore.distToZone,
                    prevDist = lastDistToZone,
                    insideZone = shore.inZone,
                    sogKn = sogKn,
                    armed = zone300ManuallyHidden,
                    autoRevealed = zone300AutoRevealed,
                    zoneEntered = bandEnteredSinceReveal,
                    revealDistM = cfg.zoneAutoRevealDistanceM.toDouble(),
                    revealTimeS = cfg.zoneAutoRevealTimeS.toDouble(),
                    config = ZoneAutoShowConfig(
                        hideOnCompliantInside = true,
                        regulatorySpeedKn = AppConfig.zoneRegulatorySpeedKn.toDouble()
                    )
                )
                zone300AutoRevealed = bandDecision.autoRevealed
                bandEnteredSinceReveal = bandDecision.zoneEntered
                when (bandDecision.action) {
                    AutoShowAction.REVEAL -> settingsManager.update { it.copy(zone300Visible = true) }
                    AutoShowAction.HIDE -> settingsManager.update { it.copy(zone300Visible = false) }
                    AutoShowAction.NONE -> {}
                }
                lastDistToZone = shore.distToZone

                // ── Speed zone auto-show (uses generalized decision) ────────
                val szAutoShowEnabled = if (cfg.gpsMode) cfg.speedZoneAutoShowGps else cfg.speedZoneAutoShowDemo
                if (!szAutoShowEnabled) {
                    speedZoneAutoRevealed = false
                    lastDistToSpeedZone = shore.speedZoneQuery.distanceToBoundaryM
                } else {
                    val szDecision = zoneAutoShowDecision(
                        dist = shore.speedZoneQuery.distanceToBoundaryM,
                        prevDist = lastDistToSpeedZone,
                        insideZone = shore.speedZoneQuery.insideAnyZone,
                        sogKn = sogKn,
                        armed = speedZoneManuallyHidden,
                        autoRevealed = speedZoneAutoRevealed,
                        zoneEntered = false,
                        revealDistM = cfg.zoneAutoRevealDistanceM.toDouble(),
                        revealTimeS = cfg.zoneAutoRevealTimeS.toDouble(),
                        config = ZoneAutoShowConfig(
                            hideOnCompliantInside = false,
                            hysteresisM = AppConfig.speedZoneHysteresisM
                        )
                    )
                    speedZoneAutoRevealed = szDecision.autoRevealed
                    when (szDecision.action) {
                        AutoShowAction.REVEAL -> settingsManager.update { it.copy(speedZonesVisible = true) }
                        AutoShowAction.HIDE -> settingsManager.update { it.copy(speedZonesVisible = false) }
                        AutoShowAction.NONE -> {}
                    }
                    lastDistToSpeedZone = shore.speedZoneQuery.distanceToBoundaryM
                }

                // ── Demo speed stop-detection (replaces panStopJob coroutine — no 60 Hz launch) ──
                val nowMs = SystemClock.elapsedRealtime()
                if (lastScrollMs > 0 && nowMs - lastScrollMs > PAN_STOP_DELAY_MS) {
                    _navigationState.update { it.copy(demoSpeedKnots = null) }
                    lastScrollMs = 0
                }
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

        // ── Speed zone index: build once when both data sources are ready ─────
        combine(
            regulatedZonesRepository.zoneSet,
            repository.state.map { (it as? CoastlineState.Ready)?.data?.zone300 }
                .distinctUntilChanged()
        ) { zoneSet, _ ->
            val zones = SpeedZoneBuilder.build(zoneSet)
            val zoneCount = zoneSet?.zones?.size ?: 0
            val speedCount = zones.size
            Log.d(TAG, "SpeedZoneIndex: zoneSet has $zoneCount total zones, $speedCount speed zones")
            if (zoneSet != null) {
                Log.d(TAG, "SpeedZoneIndex: sample raw zones: ${zoneSet.zones.take(5).joinToString { "name='${it.name}' speed=${it.speedLimitKn} source=${it.source}" }}")
            }
            if (zones.isNotEmpty()) {
                Log.d(TAG, "SpeedZoneIndex: building index with ${zones.size} zones")
                zones.forEachIndexed { i, sz ->
                    Log.d(TAG, "SpeedZoneIndex:   zone[$i] id='${sz.id}' name='${sz.name}' speed=${sz.speedLimitKn}kn vertices=${sz.outerRing.size} source=${sz.source}")
                }
                Log.d(TAG, "SpeedZoneIndex: total outer vertices: ${zones.sumOf { it.outerRing.size }}")
                SpeedZoneIndex(zones)
            } else {
                Log.w(TAG, "SpeedZoneIndex: no speed zones found (zoneSet=${zoneSet != null})")
                null
            }
        }
            .distinctUntilChanged()
            .onEach { idx -> _speedZoneIndex.value = idx }
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
     * Toggles the speed zone overlay visibility. Manages both the manual-hide flag
     * and the auto-reveal flag so the state machine stays consistent.
     */
    fun toggleSpeedZonesVisibility() {
        val current = settings.value.speedZonesVisible
        settingsManager.update { it.copy(speedZonesVisible = !current) }
        if (current) { // was visible → now hiding (user manually hid it)
            speedZoneManuallyHidden = true
            speedZoneAutoRevealed = false
        } else { // was hidden → now showing (user manually toggled back on)
            speedZoneManuallyHidden = false
            speedZoneAutoRevealed = false
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
        // Throttle settings persistence to ~1 Hz — every scroll event (60 fps) would trigger
        // appSettings StateFlow → Compose recomposition at 60 fps, bypassing uiMapCenter throttle.
        // Full position is persisted via savePosition() at ON_PAUSE for crash recovery.
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastPersistMs >= 1_000L) {
            lastPersistMs = nowMs
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

                // Heading is NOT extrapolated from panning — always 0° (north) in demo mode.
                // Only speed is derived from pan velocity.
                _navigationState.update { it.copy(
                    demoSpeedKnots = knots.toFloat()
                ) }
                lastPanLat = lat
                lastPanLon = lon
                lastPanMs = now
            }
        } else {
            lastPanLat = lat
            lastPanLon = lon
            lastPanMs = now
        }
        // Track last scroll for stop-detection (checked in pipeline onEach — no coroutine churn).
        lastScrollMs = now
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

    // ── Heading-aware speed zone ahead ────────────────────────────────────────

    /**
     * Distance (meters) from (lat, lon) to the 300m band along [headingDeg],
     * using ray-march in 10m increments with binary-search termination.
     *
     * Returns 0.0 if already inside the 300m band (currentDistToCoast <= 300),
     * or null if no band intersection within [maxSearch] meters.
     */
    private fun distanceTo300mAlongHeading(
        lat: Double, lon: Double,
        headingDeg: Double,
        currentDistToCoast: Double,
        maxSearchM: Double = 500.0
    ): Double? {
        // Already inside the band
        if (currentDistToCoast <= CoastlineRepository.ZONE_DISTANCE_M) return 0.0

        // Ray-march in 10m steps
        val stepM = 10.0
        val maxSteps = (maxSearchM / stepM).toInt()
        var prevDist = currentDistToCoast
        var prevStepDist = 0.0

        for (i in 1..maxSteps) {
            val stepDist = i * stepM
            val pt = SpatialOperations.pointAlongBearing(lat, lon, headingDeg, stepDist)
            val coastDist = repository.distanceToCoastMeters(pt.latitude, pt.longitude)

            if (coastDist <= CoastlineRepository.ZONE_DISTANCE_M) {
                // Crossed into the band — binary search between prevStepDist and stepDist
                var lo = prevStepDist
                var hi = stepDist
                repeat(10) { // 10 iterations = ~1cm precision
                    val mid = (lo + hi) / 2.0
                    val midPt = SpatialOperations.pointAlongBearing(lat, lon, headingDeg, mid)
                    val midCoastDist = repository.distanceToCoastMeters(midPt.latitude, midPt.longitude)
                    if (midCoastDist <= CoastlineRepository.ZONE_DISTANCE_M) {
                        hi = mid
                    } else {
                        lo = mid
                    }
                }
                return hi
            }
            prevDist = coastDist
            prevStepDist = stepDist
        }
        return null // No intersection within maxSearchM
    }

    /**
     * Unified entry query: distance + ETA to the nearest zone boundary along heading.
     * Directly queries the 300m band ray-march and SHOM firstSpeedZoneAhead,
     * picking the closest result. Returns null when no zone is ahead.
     */
    /**
     * Unified zone query: returns all relevant zones within [radiusM] of the boat,
     * sorted by heading-priority (zones directly ahead first).
     *
     * Combines three sources:
     * 1. Direct heading ray-march for 300m band entry
     * 2. Direct heading ray for SHOM zones (firstSpeedZoneAhead)
     * 3. Radial scan for remaining zones within radius
     * 4. 300m band virtual zone
     */
    private fun zonesAroundBoat(
        lat: Double, lon: Double,
        headingDeg: Double,
        currentDistToCoast: Double,
        currentSpeedKnots: Float?,
        speedZoneIndex: SpeedZoneIndex?,
        radiusM: Double = 500.0,
        excludeZoneName: String? = null,
        onlyAhead: Boolean = true
    ): List<ZoneBoundaryInfo> {
        val results = mutableListOf<ZoneBoundaryInfo>()
        val pos = LatLng(lat, lon)

        // Helper to compute ETA for heading-ray results
        fun eta(distM: Double): Double? = if (currentSpeedKnots != null && currentSpeedKnots > 0f)
            (distM / (currentSpeedKnots * 0.514444)).coerceAtLeast(0.0) else null

        // 1. Direct heading ray-march for 300m band entry
        val bandDist = distanceTo300mAlongHeading(lat, lon, headingDeg, currentDistToCoast, radiusM)
        if (bandDist != null && bandDist > 0.0 && bandDist <= radiusM && "BANDE 300M" != excludeZoneName) {
            val pt = SpatialOperations.pointAlongBearing(lat, lon, headingDeg, bandDist)
            val bearing = SpatialOperations.initialBearing(pos, pt)
            results.add(ZoneBoundaryInfo(
                distanceM = bandDist, zoneName = "BANDE 300M",
                speedLimitKn = 5.0, beyondType = BeyondType.ZONE, beyondName = "BANDE 300M",
                directionArrow = computeArrow(bearing, headingDeg),
                etaSeconds = eta(bandDist),
                currentSpeedKnots = currentSpeedKnots,
                isCompliant = currentSpeedKnots == null || currentSpeedKnots < 5f,
                boundaryPosition = pt
            ))
        }

        // 2. Direct heading ray for SHOM zones
        val szHit = speedZoneIndex?.firstSpeedZoneAhead(lat, lon, headingDeg, radiusM)
        if (szHit != null && szHit.second > 0.0 && szHit.second <= radiusM && szHit.first.name != excludeZoneName) {
            val (zone, dist) = szHit
            val pt = SpatialOperations.pointAlongBearing(lat, lon, headingDeg, dist)
            val bearing = SpatialOperations.initialBearing(pos, pt)
            val limitKn = zone.speedLimitKn.toDouble()
            results.add(ZoneBoundaryInfo(
                distanceM = dist, zoneName = zone.name,
                speedLimitKn = limitKn,
                beyondType = BeyondType.ZONE, beyondName = zone.name,
                directionArrow = computeArrow(bearing, headingDeg),
                etaSeconds = eta(dist),
                currentSpeedKnots = currentSpeedKnots,
                isCompliant = currentSpeedKnots == null || currentSpeedKnots < limitKn.toFloat(),
                boundaryPosition = pt
            ))
        }

        // 3. Radial scan (only when !onlyAhead — heading-ray callers skip this)
        if (!onlyAhead) {
        val query = speedZoneIndex?.query(lat, lon)
        query?.let { q ->
            val foundNames = results.map { it.zoneName }.toSet()
            // nearest zone (if not already in results and not excluded)
            if (q.distanceToBoundaryM != null && abs(q.distanceToBoundaryM) <= radiusM) {
                q.nearestZone?.let { zone ->
                    if (zone.name !in foundNames && zone.name != excludeZoneName) {
                        val ring = zone.outerRing
                        val nearestPt = ring.minByOrNull { SpatialOperations.haversine(pos, it) }
                        val edgeDist = nearestPt?.let { SpatialOperations.haversine(pos, it) }
                            ?: abs(q.distanceToBoundaryM)
                        if (edgeDist <= radiusM) {
                            val bearing = nearestPt?.let { SpatialOperations.initialBearing(pos, it) } ?: headingDeg
                            results.add(ZoneBoundaryInfo(
                                distanceM = edgeDist, zoneName = zone.name,
                                speedLimitKn = zone.speedLimitKn.toDouble(),
                                beyondType = BeyondType.ZONE, beyondName = zone.name,
                                directionArrow = computeArrow(bearing, headingDeg)
                            ))
                        }
                    }
                }
            }
            // inside zones (not already in results and not excluded)
            for (zone in q.allInsideZones) {
                if (zone.name !in foundNames && results.none { it.zoneName == zone.name } && zone.name != excludeZoneName) {
                    val ring = zone.outerRing
                    val nearestPt = ring.minByOrNull { SpatialOperations.haversine(pos, it) }
                    val dist = nearestPt?.let { SpatialOperations.haversine(pos, it) } ?: 0.0
                    if (dist <= radiusM) {
                        val bearing = nearestPt?.let { SpatialOperations.initialBearing(pos, it) } ?: headingDeg
                        results.add(ZoneBoundaryInfo(
                            distanceM = dist, zoneName = zone.name,
                            speedLimitKn = zone.speedLimitKn.toDouble(),
                            beyondType = BeyondType.ZONE, beyondName = zone.name,
                            directionArrow = computeArrow(bearing, headingDeg)
                        ))
                    }
                }
            }
        }
        } // end if (!onlyAhead)

        // 4. 300m band virtual zone (only when !onlyAhead — heading-ray already has it from step 1)
        if (!onlyAhead && "BANDE 300M" !in results.map { it.zoneName }) {
            if (currentDistToCoast <= CoastlineRepository.ZONE_DISTANCE_M + radiusM) {
                val bandDist = maxOf(0.0, currentDistToCoast - CoastlineRepository.ZONE_DISTANCE_M)
                results.add(ZoneBoundaryInfo(
                    distanceM = bandDist, zoneName = "BANDE 300M",
                    speedLimitKn = 5.0, beyondType = BeyondType.ZONE, beyondName = "BANDE 300M"
                ))
            }
        }

        // 5. Sort by heading priority: ahead (↑) first, then side (→/←), then behind (↓)
        return results.sortedBy { info ->
            when (info.directionArrow) {
                "\u2191" -> 0
                "\u2192", "\u2190" -> 1
                else -> 2
            }
        }
    }

    /**
     * Computes a direction arrow (↑ → ← ↓) based on the bearing to a zone
     * relative to the boat's heading.
     */
    private fun computeArrow(zoneBearingDeg: Double, headingDeg: Double): String {
        if (headingDeg < 0.0) return "\u2191"
        val diff = ((zoneBearingDeg - headingDeg + 540.0) % 360.0) - 180.0
        return when {
            diff > 150.0 || diff < -150.0 -> "\u2193"
            diff > CONE_HALF_ANGLE -> "\u2192"
            diff < -CONE_HALF_ANGLE -> "\u2190"
            else -> "\u2191"
        }
    }

    /**
     * Unified exit query: distance + ETA to exit the current zone along heading.
     * Returns null when exit can't be determined.
     *
     * For the 300m band: inverts the ray-march — searches forward until coastDist > ZONE_DISTANCE_M.
     * For SHOM zones: queries the spatial index for the nearest boundary.
     */
    private fun infoToZoneExitAlongHeading(
        lat: Double, lon: Double,
        headingDeg: Double,
        currentDistToCoast: Double,
        currentSpeedKnots: Float?,
        speedZoneIndex: SpeedZoneIndex?,
        maxSearchM: Double = 500.0
    ): ZoneBoundaryInfo? {
        // 300m band exit: inverted ray-march
        val bandExit = if (currentDistToCoast <= CoastlineRepository.ZONE_DISTANCE_M) {
            findBandExitAlongHeading(lat, lon, headingDeg, currentDistToCoast, maxSearchM)
        } else null

        // SHOM zone exit: query the index for the current zone boundary
        val szQuery = speedZoneIndex?.query(lat, lon)
        val szExit = szQuery?.let { q ->
            val nz = q.nearestZone ?: q.allInsideZones.firstOrNull()
            if (nz != null && q.distanceToBoundaryM != null) {
                val rawDist = abs(q.distanceToBoundaryM!!)
                val ring = nz.outerRing
                val nearestPt = ring.minByOrNull { pt ->
                    SpatialOperations.haversine(LatLng(lat, lon), pt)
                }
                val bearing = nearestPt?.let { SpatialOperations.initialBearing(LatLng(lat, lon), it) }
                // Project shortest exit distance onto heading direction
                val headingDiff = if (bearing != null) {
                    abs(((bearing - headingDeg + 540.0) % 360.0) - 180.0)
                } else 0.0
                val projectedDist = if (headingDiff <= 90.0) {
                    (rawDist / cos(Math.toRadians(headingDiff))).coerceAtMost(rawDist * 3.0)
                } else null
                if (projectedDist != null) {
                    Triple(nz.name, nz.speedLimitKn, projectedDist)
                } else null
            } else null
        }

        // Pick closest exit
        val bestExit = when {
            bandExit != null && szExit != null ->
                if (bandExit <= szExit.third) Triple("BANDE 300M", 5.0, bandExit) else szExit
            bandExit != null -> Triple("BANDE 300M", 5.0, bandExit)
            szExit != null -> szExit
            else -> return null
        }

        val (zoneName, limitKn, exitDistM) = bestExit
        val etaSeconds = if (currentSpeedKnots != null && currentSpeedKnots > 0f) {
            (exitDistM / (currentSpeedKnots * 0.514444)).coerceAtLeast(0.0)
        } else null
        val compliant = currentSpeedKnots == null || currentSpeedKnots < limitKn.toFloat()
        val boundaryPos = SpatialOperations.pointAlongBearing(lat, lon, headingDeg, exitDistM)
        val beyond = determineBeyondType(boundaryPos, headingDeg, speedZoneIndex)

        return ZoneBoundaryInfo(
            distanceM = exitDistM,
            etaSeconds = etaSeconds,
            directionArrow = "\u2191",
            zoneName = zoneName,
            speedLimitKn = limitKn,
            currentSpeedKnots = currentSpeedKnots,
            isCompliant = compliant,
            beyondType = beyond.first,
            beyondName = beyond.second,
            boundaryPosition = boundaryPos
        )
    }

    /**
     * Inverted ray-march for 300m band: from inside the band, walk forward along heading
     * until [coastDist] exceeds [ZONE_DISTANCE_M], then binary-search the exact exit boundary.
     * Returns distance (m) to exit, or null if no exit within 2km.
     */
    private fun findBandExitAlongHeading(
        lat: Double, lon: Double,
        headingDeg: Double,
        currentDistToCoast: Double,
        maxSearchM: Double = 500.0
    ): Double? {
        // Already outside the band — can't be exiting
        if (currentDistToCoast > CoastlineRepository.ZONE_DISTANCE_M) return null

        val stepM = 10.0
        val maxSteps = (maxSearchM / stepM).toInt()
        var prevStepDist = 0.0
        var prevCoastDist = currentDistToCoast

        for (i in 1..maxSteps) {
            val stepDist = i * stepM
            val pt = SpatialOperations.pointAlongBearing(lat, lon, headingDeg, stepDist)
            val coastDist = repository.distanceToCoastMeters(pt.latitude, pt.longitude)

            if (coastDist > CoastlineRepository.ZONE_DISTANCE_M) {
                // Crossed out of the band — binary search between prevStepDist and stepDist
                var lo = prevStepDist
                var hi = stepDist
                var loDist = prevCoastDist
                repeat(10) {
                    val mid = (lo + hi) / 2.0
                    val midPt = SpatialOperations.pointAlongBearing(lat, lon, headingDeg, mid)
                    val midCoastDist = repository.distanceToCoastMeters(midPt.latitude, midPt.longitude)
                    if (midCoastDist <= CoastlineRepository.ZONE_DISTANCE_M) {
                        lo = mid
                        loDist = midCoastDist
                    } else {
                        hi = mid
                    }
                }
                return hi
            }
            prevStepDist = stepDist
            prevCoastDist = coastDist
        }
        return null // No exit within maxSearch
    }

    /**
     * Determines what lies beyond a [boundaryPos] along [headingDeg].
     * Checks zone first (instant), then exponential land probe up to 500m.
     * @return Pair(beyondType, zoneName if ZONE, null otherwise)
     */
    private fun determineBeyondType(
        boundaryPos: LatLng, headingDeg: Double,
        speedZoneIndex: SpeedZoneIndex?
    ): Pair<BeyondType, String?> {
        val blt = boundaryPos.latitude
        val blo = boundaryPos.longitude
        // 1. Check for another zone ahead (instant, independent of distance)
        val nextZone = speedZoneIndex?.firstSpeedZoneAhead(blt, blo, headingDeg)
        if (nextZone != null) return BeyondType.ZONE to nextZone.first.name

        // 2. Exponential land probe: 25 → 50 → 100 → 200 → 400 → 500 (cap)
        var probe = 25.0
        while (probe <= 500.0) {
            val pt = SpatialOperations.pointAlongBearing(blt, blo, headingDeg, probe)
            if (!repository.isOnWater(pt.latitude, pt.longitude)) return BeyondType.LAND to null
            probe *= 2
        }
        return BeyondType.OPEN_SEA to null
    }

    /** GPS subscription parameters — flatMapLatest re-subscribes the listener when any field changes. */
    private data class GpsParams(val on: Boolean, val intervalMs: Long, val minDistanceM: Float)

    /** Result of one throttled recompute: shore distance + water flag + 300 m zone + speed zone data + unified zone situation. */
    private data class ShoreState(
        val distanceMeters: Double?,
        val isWater: Boolean,
        val inZone: Boolean,
        val distToZone: Double?,
        val speedZoneQuery: SpeedZoneQuery = SpeedZoneQuery(),
        val zoneSituation: ZoneSituation? = null
    )

    companion object {
        /** Sampling interval for the map-center recompute pipeline (~3 Hz). */
        private const val SHORE_SAMPLE_INTERVAL_MS = 333L

        /** Time without a GPS course before the compass takes over heading-up (rule 3). */
        private const val HEADING_FALLBACK_MS = 3_000L

        /** Minimum heading change (deg) before the map re-orients — suppresses sensor jitter. */
        private const val MIN_BEARING_DELTA_DEG = 1.0f

        /** Rate-limit for the compass heading (~5 Hz) so turning doesn't churn the map. */
        private const val HEADING_SAMPLE_MS = 200L

        /** Minimum interval (ms) between demo speed computations — throttles the 60 fps scroll stream. */
        private const val MIN_DEMO_SPEED_INTERVAL_MS = 333L

        /** Inactivity timeout (ms) before demo pan speed resets to null. */
        private const val PAN_STOP_DELAY_MS = 500L

        /** Metres-per-second → knots. */
        private const val MPS_TO_KNOTS = 1.943844f

        /** Scale factor for sqrt-compressed demo speed: knots = sqrt(rawMps × this). */
        private const val DEMO_SPEED_SCALE = 0.2
        /** Cone half-angle (degrees) — zones within ±this of heading get priority and show as "ahead". */
        private const val CONE_HALF_ANGLE = 15.0
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

/** What's beyond a zone boundary along the current heading. */
enum class BeyondType { LAND, ZONE, OPEN_SEA }

/**
 * Unified result for both entry (approaching) and exit (inside) zone boundary info.
 *
 * @property distanceM        Distance (m) to the zone boundary along heading.
 * @property etaSeconds       ETA to the boundary at current SOG, or null if speed unknown.
 * @property directionArrow   Unicode arrow: ↑ ahead, → starboard, ← port, ↓ behind.
 * @property zoneName         Name of the zone (e.g. "BANDE 300M" or SHOM zone).
 * @property speedLimitKn     Speed limit in the zone.
 * @property currentSpeedKnots Boat's current SOG (for caller to compare vs limit).
 * @property isCompliant      true when currentSpeedKnots <= speedLimitKn.
 * @property beyondType       What's past the boundary — LAND, ZONE, or OPEN_SEA.
 * @property beyondName       Zone name if beyondType==ZONE, null otherwise.
 * @property boundaryPosition Geographic intersection point on the zone boundary.
 */
data class ZoneBoundaryInfo(
    val distanceM: Double,
    val etaSeconds: Double? = null,
    val directionArrow: String = "\u2191",
    val zoneName: String,
    val speedLimitKn: Double,
    val currentSpeedKnots: Float? = null,
    val isCompliant: Boolean = true,
    val beyondType: BeyondType,
    val beyondName: String? = null,
    val boundaryPosition: LatLng? = null
)

/**
 * Unified zone situation at the boat position — replaces 7 individual StateFlows.
 *
 * @property currentZone   The zone the boat is currently inside (exit boundary info),
 *                         or null if not inside any zone.
 * @property headingAhead  The next zone boundary along the boat's heading (entry info),
 *                         or null if nothing ahead or already inside a zone.
 * @property nearbyZones   All zones (SHOM + 300m band virtual zone) within the search radius,
 *                         sorted by distance.
 */
data class ZoneSituation(
    val currentZone: ZoneBoundaryInfo?,
    val zonesAround: List<ZoneBoundaryInfo>,
)

/** Outcome of one generalized zone auto-show evaluation. */
internal enum class AutoShowAction { NONE, REVEAL, HIDE }

/**
 * Action + next flag state from one auto-reveal evaluation (immutable, side-effect-free).
 *
 * Replaces both [Zone300Decision] and [SpeedZoneDecision] with a single result type.
 * @property zoneEntered Tracks whether the boat has crossed into the zone since last auto-reveal
 *                       (only meaningful for 300m band behavior; always false for speed zones).
 */
internal data class AutoShowDecision(
    val action: AutoShowAction,
    val autoRevealed: Boolean,
    val zoneEntered: Boolean = false
)

/** Configuration for [zoneAutoShowDecision] behavioral differences between zone types. */
internal data class ZoneAutoShowConfig(
    /** True = hide when compliant inside (300m band behavior). False = stay visible while inside (speed zone behavior). */
    val hideOnCompliantInside: Boolean = true,
    /** Regulatory speed limit (kn) for compliance check (only when [hideOnCompliantInside]). */
    val regulatorySpeedKn: Double = 5.0,
    /** Hysteresis deadband (m) for boundary detection (only for speed zone behavior). */
    val hysteresisM: Double = 5.0
)

// Backward-compat typealiases so existing tests and toggle functions still compile.
@Deprecated("Use AutoShowAction", ReplaceWith("AutoShowAction"))
internal typealias Zone300Action = AutoShowAction
@Deprecated("Use AutoShowAction", ReplaceWith("AutoShowAction"))
internal typealias SpeedZoneAction = AutoShowAction
@Deprecated("Use AutoShowDecision", ReplaceWith("AutoShowDecision"))
internal typealias Zone300Decision = AutoShowDecision
@Deprecated("Use AutoShowDecision", ReplaceWith("AutoShowDecision"))
internal typealias SpeedZoneDecision = AutoShowDecision

private const val KNOTS_TO_MPS = 0.514444      // 1 knot = 0.514444 m/s
private const val CLOSING_EPS_MPS = 0.05       // ignore closing speeds below ~0.1 kn for time-to-band
private const val STOPPED_SPEED_KN = 1.0f      // at/below this SOG the boat is "stopped" → auto-hide, never reveal

/**
 * Generalized pure function for zone proximity auto-reveal — replaces both [zone300Decision]
 * and [speedZoneDecision] with a single parameterized implementation.
 *
 * Handles two zone types via [config]:
 * - **300m band** ([hideOnCompliantInside] = true): hides when compliant inside the band (boat at or
 *   below regulatory speed). Tracks [zoneEntered] for seaward-exit detection.
 * - **Speed zone** ([hideOnCompliantInside] = false): stays visible while navigating through;
 *   only hides when stopped+idle or retreated past the reveal margin. No compliance-inside hide.
 *
 * Reveal (only while [armed], not already shown, still OUTSIDE): approach-gated hybrid — the boat
 * is closing AND is either within [revealDistM] of the boundary or within [revealTimeS] at SOG.
 * [armed] persists through auto-hide, so re-approaching re-reveals.
 *
 * @param dist         signed distance to zone boundary (+ outside, − inside); null if unknown.
 * @param prevDist     previous [dist] sample for direction; null on first tick.
 * @param insideZone   true when currently inside the zone.
 * @param sogKn        speed over ground (kn); null in demo mode / before first GPS fix.
 * @param armed        true when the user manually hid the zone.
 * @param autoRevealed true when the system previously auto-revealed.
 * @param zoneEntered  true if the boat has crossed into the zone since the last auto-reveal (300m only).
 * @param revealDistM  distance (m) outside boundary at which to reveal.
 * @param revealTimeS  time (s) to boundary at SOG at which to reveal.
 * @param config       behavioral configuration ([ZoneAutoShowConfig]).
 */
internal fun zoneAutoShowDecision(
    dist: Double?,
    prevDist: Double?,
    insideZone: Boolean,
    sogKn: Float?,
    armed: Boolean,
    autoRevealed: Boolean,
    zoneEntered: Boolean,
    revealDistM: Double,
    revealTimeS: Double,
    config: ZoneAutoShowConfig = ZoneAutoShowConfig()
): AutoShowDecision {
    val approaching = dist != null && prevDist != null && dist < prevDist
    val movingAway = dist != null && prevDist != null && dist > prevDist
    val stopped = sogKn != null && sogKn <= STOPPED_SPEED_KN
    val entered = zoneEntered || (autoRevealed && dist != null && dist <= 0.0)

    if (!autoRevealed) {
        val sogMps = (sogKn ?: 0f) * KNOTS_TO_MPS
        val timeToZoneS =
            if (approaching && dist != null && dist > 0.0 && sogMps > CLOSING_EPS_MPS) dist / sogMps
            else Double.POSITIVE_INFINITY
        val reveal = armed && approaching && dist != null && dist > 0.0 &&
            (dist <= revealDistM || timeToZoneS <= revealTimeS)
        return AutoShowDecision(
            action = if (reveal) AutoShowAction.REVEAL else AutoShowAction.NONE,
            autoRevealed = reveal,
            zoneEntered = false
        )
    }

    val stoppedAndIdle = stopped && !approaching

    if (config.hideOnCompliantInside) {
        // 300m band behavior: hide on compliant-inside, exited seaward, or retreated
        val compliantInside = insideZone && sogKn != null && sogKn <= config.regulatorySpeedKn
        val exitedSeaward = entered && dist != null && dist > 0.0 && movingAway
        val retreatedPastMargin = !entered && dist != null && dist > revealDistM && movingAway
        val hide = stoppedAndIdle || compliantInside || exitedSeaward || retreatedPastMargin
        return AutoShowDecision(
            action = if (hide) AutoShowAction.HIDE else AutoShowAction.NONE,
            autoRevealed = !hide,
            zoneEntered = if (hide) false else entered
        )
    } else {
        // Speed zone behavior: hide only when stopped+idle or retreated past margin
        val effectivelyOutside = dist != null && dist > config.hysteresisM
        val retreatedPastMargin = effectivelyOutside && movingAway && dist != null && dist > revealDistM
        val hide = stoppedAndIdle || retreatedPastMargin
        return AutoShowDecision(
            action = if (hide) AutoShowAction.HIDE else AutoShowAction.NONE,
            autoRevealed = !hide
        )
    }
}

/**
 * Delegates to [zoneAutoShowDecision] with 300m-band config — kept for backward compatibility.
 * @see zoneAutoShowDecision
 */
@Deprecated("Use zoneAutoShowDecision() with ZoneAutoShowConfig(hideOnCompliantInside=true)",
    ReplaceWith("zoneAutoShowDecision(dist, prevDist, inZone, sogKn, armed, autoRevealed, bandEntered, revealDistM, revealTimeS, ZoneAutoShowConfig(hideOnCompliantInside = true, regulatorySpeedKn = regKn))"))
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
): AutoShowDecision = zoneAutoShowDecision(
    dist = dist, prevDist = prevDist, insideZone = inZone, sogKn = sogKn,
    armed = armed, autoRevealed = autoRevealed, zoneEntered = bandEntered,
    revealDistM = revealDistM, revealTimeS = revealTimeS,
    config = ZoneAutoShowConfig(hideOnCompliantInside = true, regulatorySpeedKn = regKn)
)

/**
 * Delegates to [zoneAutoShowDecision] with speed-zone config — kept for backward compatibility.
 * @see zoneAutoShowDecision
 */
@Deprecated("Use zoneAutoShowDecision() with ZoneAutoShowConfig(hideOnCompliantInside=false, hysteresisM=...)",
    ReplaceWith("zoneAutoShowDecision(distToBoundary, prevDist, insideAnyZone, sogKn, armed, autoRevealed, false, revealDistM, revealTimeS, ZoneAutoShowConfig(hideOnCompliantInside = false, hysteresisM = hysteresisM))"))
internal fun speedZoneDecision(
    distToBoundary: Double?,
    prevDist: Double?,
    insideAnyZone: Boolean,
    sogKn: Float?,
    armed: Boolean,
    autoRevealed: Boolean,
    revealDistM: Double,
    revealTimeS: Double,
    hysteresisM: Double
): AutoShowDecision = zoneAutoShowDecision(
    dist = distToBoundary, prevDist = prevDist, insideZone = insideAnyZone, sogKn = sogKn,
    armed = armed, autoRevealed = autoRevealed, zoneEntered = false,
    revealDistM = revealDistM, revealTimeS = revealTimeS,
    config = ZoneAutoShowConfig(hideOnCompliantInside = false, hysteresisM = hysteresisM)
)
