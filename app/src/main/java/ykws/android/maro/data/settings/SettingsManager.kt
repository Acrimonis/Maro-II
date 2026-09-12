package ykws.android.maro.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ykws.android.maro.BuildConfig
import ykws.android.maro.data.depth.DepthConstants
import ykws.android.maro.data.regulation.ZoneDisplayCategory

/**
 * User-facing settings persisted via SharedPreferences.
 *
 * Exposes a reactive [settings] [StateFlow] so the Compose UI observes changes
 * without polling. Call [update] to apply a partial or full mutation.
 *
 * @property defaultLatitude   Initial map center latitude  (WGS84, °N) — used when
 *                             no persisted position exists.
 * @property defaultLongitude  Initial map center longitude (WGS84, °E).
 * @property coastlineVisible  Whether the coastline polyline overlay is drawn.
 * @property zone300Visible    Whether the 300 m regulatory band overlay is drawn.
 * @property zone300AutoShowGps   GPS mode: auto-reveal the hidden 300 m band on approach.
 *                             When off, the band stays under manual control in GPS mode.
 * @property zone300AutoShowDemo  Demo mode: same approach auto-reveal, driven by pan speed.
 * @property mapCenterLat      Persisted map center latitude  (NaN = not yet saved).
 * @property mapCenterLon      Persisted map center longitude (NaN = not yet saved).
 * @property zoomLevel         Persisted map zoom level (0.0 = not yet saved).
 * @property isWater           Last known water/land status — persisted so the
 *                             boat marker renders at the correct size on restart.
 * @property distanceToShore   Last known distance to coast (m, NaN = unknown) —
 *                             persisted so the boat marker renders at the correct
 *                             size on restart without waiting for the shore pipeline.
 * @property gpsMode           When true, the device GPS drives the map center and the map
 *                             rotates heading-up; when false, free-pan "demo" mode.
 * @property recenterDelaySeconds  GPS mode: seconds of no user pan before auto-follow/-orient
 *                             resumes (1–10). Default 5.
 * @property gpsActiveIntervalSec   GPS mode (moving): minimum seconds between fixes (1–10). The
 *                             acquisition presets write this + [gpsActiveMinDistanceM]. Default 2.
 * @property gpsActiveMinDistanceM  GPS mode (moving): minimum metres of movement between fixes
 *                             (1–25). Default 5.
 * @property stopDetectionEnabled   Master toggle for stop detection. When off, policy always ACTIVE.
 * @property stopDetectionTimeSec   Seconds of sub-threshold movement before isStill() returns true
 *                             (10–90). Default 45.
 * @property stopDetectionDistanceM Max displacement (m) still counted as "stationary" (10–30).
 *                             Default 15.
 * @property stopDetectionDelayGps  When true, GPS fixes space out when isStill() (battery saving).
 * @property mapRefreshFps     GPS auto-follow re-render ceiling in frames/s (5–50). Lower = fewer
 *                             whole-map repaints = less battery. Default 25.
 * @property emodnetShallowCutoffM  EMODnet shallow cutoff (m): EMODnet point readings shallower
 *                             than this are coarse (115 m cell over rocks/coast) and unreliable →
 *                             presented as no-data. 0 disables the gate. Default 2.0.
 */
data class AppSettings(
    val defaultLatitude: Double = 43.55,
    val defaultLongitude: Double = 7.00,
    val coastlineVisible: Boolean = BuildConfig.LAYER_COASTLINE_DEFAULT,
    val zone300Visible: Boolean = BuildConfig.LAYER_ZONE300_DEFAULT,
    val zoneAutoRevealDistanceM: Float = 100f,
    val zoneAutoRevealTimeS: Int = 10,
    val approachAutoShowGps: Boolean = true,
    val approachAutoShowDemo: Boolean = true,
    val zone300AutoShow: Boolean = true,
    val speedZoneAutoShow: Boolean = true,
    val regulatedZoneAutoShow: Boolean = true,
    val autoShowMasterOverride: Boolean = true,
    val gpsMode: Boolean = false,
    val recenterDelaySeconds: Int = 5,
    val gpsActiveIntervalSec: Int = 2,
    val gpsActiveMinDistanceM: Float = 5f,
    val stopDetectionEnabled: Boolean = true,
    val stopDetectionTimeSec: Int = 45,
    val stopDetectionDistanceM: Int = 15,
    val stopDetectionDelayGps: Boolean = true,
    val mapRefreshFps: Int = 25,
    val mapCenterLat: Double = Double.NaN,
    val mapCenterLon: Double = Double.NaN,
    val zoomLevel: Double = 0.0,
    val isWater: Boolean = true,
    val distanceToShore: Double = Double.NaN,
    /** App language: "system" (device locale, English fallback), "en", or "fr". */
    val languageCode: String = "system",
    /**
     * Master "don't lock the phone while the app is open" setting: holds `FLAG_KEEP_SCREEN_ON` on
     * the window while the app is in front. Default `false`, matching the read fallback below and
     * the shipped behaviour — the screen may sleep unless the user asks otherwise.
     *
     * With [keepScreenOnMovementGate] off the screen is held the whole time the app is in front.
     * With it on, the hold follows [keepScreenOnSpeedThresholdKn] and [keepScreenOnGraceMinutes].
     * See `xTrack/Performance/FEAT_DSC_Performance.md` (power management).
     */
    val keepScreenOn: Boolean = false,
    /** Hold the screen only while moving, or shortly after the last touch, instead of always. */
    val keepScreenOnMovementGate: Boolean = false,
    /** Movement gate: speed over ground (knots) above which the boat counts as moving. */
    val keepScreenOnSpeedThresholdKn: Float =
        ykws.android.maro.config.AppConfig.powerScreenMovementThresholdKn,
    /** Movement gate: minutes (1–15, default from `maro.properties`) without movement or interaction. */
    val keepScreenOnGraceMinutes: Int =
        ykws.android.maro.config.AppConfig.powerScreenGraceDefaultMinutes,
    /**
     * True once the battery-optimization prompt has been shown and answered, so it never reappears.
     * Absorbed from the legacy `maro_battery_prefs` store on first run (see the migration in `init`).
     */
    val batteryOptimizationPrompted: Boolean = false,
    /** Highlight charted shallow water as a bright grounding-hazard overlay. */
    val lowDepthWarningVisible: Boolean = true,
    /** Crash depth (m): the overlay is fully opaque from the surface down to this depth. */
    val lowDepthCrashDepthM: Float = DepthConstants.LOW_DEPTH_CRASH_DEPTH_M.toFloat(),
    /** Start-warning depth (m): the warning begins here and is transparent at/beyond this depth. */
    val lowDepthStartWarningM: Float = DepthConstants.LOW_DEPTH_START_WARNING_M.toFloat(),
    /** 300 m band fill + boundary colour (opaque ARGB). Fill alpha is derived from [zone300FillTransparencyPct]. */
    val zone300Color: Int = 0xFFE53935.toInt(),
    /** 300 m band fill transparency % (0–100, higher = more invisible). Default 80 ≈ today's 0x30 fill alpha. */
    val zone300FillTransparencyPct: Int = 80,
    /** 300 m band seaward boundary transparency % (0–100). */
    val zone300BoundaryTransparencyPct: Int = 20,
    /** Idle threshold (s) before a BoatMarker snapshot + auto-marker is captured. */
    val boatMarkerIdleThresholdSec: Long = ykws.android.maro.config.AppConfig.boatMarkerIdleThresholdSec,
    /** Minimum idle duration (s) before an auto-marker becomes permanent. */
    val boatMarkerAutoMarkerMinDurationSec: Long = ykws.android.maro.config.AppConfig.boatMarkerAutoMarkerMinDurationSec,
    /** Dedup radius (m) — skip auto-marker creation when an existing IDLE_AUTO marker is within this distance. */
    val boatMarkerAutoMarkerDedupRadiusM: Double = ykws.android.maro.config.AppConfig.boatMarkerAutoMarkerDedupRadiusM,
    /** EMODnet shallow cutoff (m): EMODnet point readings shallower than this are coarse
     *  (115 m cell over rocks/coast) and unreliable → presented as no-data. 0 disables the gate. */
    val emodnetShallowCutoffM: Float = 2.0f,
    /** Regenerate: reload depth grid from assets. */
    val regenGrid: Boolean = true,
    /** Regenerate: re-derive isobath contours. */
    val regenIsobaths: Boolean = true,
    /** Regenerate: rebuild + re-cache depth colour raster. */
    val regenColour: Boolean = true,
    /** Regenerate: rebuild + re-cache shallow warning raster. */
    val regenWarning: Boolean = true,
    /** Show the dashed heading direction line from the boat marker to the map edge. */
    val headingLineVisible: Boolean = true,
    /** Show the speed-proportional cap arrow projecting from the boat marker. */
    val capArrowVisible: Boolean = false,
    /** Show the hypsometric depth colour map and isobath contour overlays. */
    val depthLayerVisible: Boolean = true,
    /** Whether the regulated zones overlay (speed limits, anchoring, access, …) is drawn. */
    val regulatedZonesVisible: Boolean = BuildConfig.LAYER_REGULATED_ZONES_DEFAULT,
    /** Show zone info text panel beside the icon stack. */
    val regulationInfoVisible: Boolean = false,
    /** Boat length in metres — used to filter out zones with vessel size exemptions. */
    val boatSizeM: Double = BuildConfig.REGULATED_ZONES_DEFAULT_VESSEL_LENGTH_M,
    /** Per-category visibility toggles for the regulated zone warning strip. */
    val showCategoryNoAnchor: Boolean = true,
    val showCategoryMooring: Boolean = false,
    val showCategorySpeedLimit: Boolean = true,
    val showCategoryNoDiving: Boolean = true,
    val showCategorySeaplane: Boolean = false,
    val showCategoryNoAccess: Boolean = true,
    val showCategoryFishingProhibited: Boolean = false,
    val showCategoryEnvironmental: Boolean = true,
    val showCategoryInformation: Boolean = false,
    /**
     * GPS idle mode: minimum metres of movement between fixes when the adaptive policy
     * has switched to [AcquisitionMode.IDLE] (device stationary). Default 0 so even tiny
     * drifts update the position at the idle cadence — prevents the perception of a
     * "stuck" position when anchored or drifting slowly.
     */
    /** Demo mode: rotate map heading-up (pan-direction-derived bearing) instead of north-up. */
    val demoHeadingUp: Boolean = false,
    val gpsIdleMinDistanceM: Float = 0f,
    /** Enable track recording. */
    val trackEnabled: Boolean = BuildConfig.TRACK_ENABLED_DEFAULT,
    /** Geofence origin latitude for auto start/stop (Port Salis). */
    val trackOriginLat: Double = BuildConfig.TRACK_ORIGIN_LAT,
    /** Geofence origin longitude for auto start/stop (Port Salis). */
    val trackOriginLon: Double = BuildConfig.TRACK_ORIGIN_LON,
    /** Geofence radius in metres for auto start/stop. */
    val trackGeofenceRadiusM: Double = BuildConfig.TRACK_GEOFENCE_RADIUS_M,
    /** When false, recording starts on movement alone (no geofence check). */
    val trackGeofenceEnabled: Boolean = true,
    /** Maximum GPS self-reported accuracy (m) for a fix to be recorded. Fixes with worse accuracy are rejected. */
    val maxRecordingAccuracyM: Float = 30f,
    /** Whether the tracks overlay layer is visible on the map. */
    /** Binary marker layer visibility: HIDDEN or SHOW_ALL. */
    val markerLayerState: ykws.android.maro.ui.map.MarkerLayerState = ykws.android.maro.ui.map.MarkerLayerState.SHOW_ALL,
    /** Whether marker zone shapes (circle outlines, corridor parallels) render.
     *  When false, only center dots are drawn. Proximity previews follow this toggle. */
    val markerZonesVisible: Boolean = true,
    /** Halo size % (0-100) — scales the halo ring radius relative to its anchor
     *  (dot or icon). Default 50 = medium. */
    val markerHaloSize: Int = 50,
    /** Marker point/icon rendering zoom % (50-150). 100 = current size. Scales the
     *  rendered dot radius and icon glyph; the halo ring follows via the same factor. */
    val markerPointIconZoom: Int = 100,
    /** Pinned halo colour (opaque ARGB) drawn as a static ring behind each confirmed
     *  pinned marker's centre dot/icon. */
    val markerHaloPinnedColor: Int = 0xFFFFFFFF.toInt(),
    /** Unpinned halo colour (opaque ARGB) — distinct from pinned so the two states
     *  are visually separable. */
    val markerHaloUnpinnedColor: Int = 0xFF81D4FA.toInt(),
    /** Pinned halo inside-fill ("zone") transparency % (0-100). Default 75 = subtle disc. */
    val markerHaloPinnedFillTransparencyPct: Int = 75,
    /** Pinned halo border/stroke transparency % (0-100). Default 20 = strong ring. */
    val markerHaloPinnedBorderTransparencyPct: Int = 20,
    /** Unpinned halo inside-fill ("zone") transparency % (0-100). Default 90 = faint disc. */
    val markerHaloUnpinnedFillTransparencyPct: Int = 90,
    /** Unpinned halo border/stroke transparency % (0-100). Default 60 = faint ring. */
    val markerHaloUnpinnedBorderTransparencyPct: Int = 60,
    val tracksVisible: Boolean = true,
    /** Draw direction arrows along rendered tracks (history + pinned). */
    val tracksDirectionVisible: Boolean = false,
    /** Direction-arrow density mode: uniform on-screen spacing or speed-based. */
    val trackDirectionDensity: ykws.android.maro.ui.map.TrackDirectionDensity = ykws.android.maro.ui.map.TrackDirectionDensity.UNIFORM,
    /** Speed (kn) below which direction arrows use minimum spacing. */
    val trackDirectionSpeedFloorKn: Float = ykws.android.maro.config.AppConfig.trackDirectionSpeedFloorKn,
    /** Speed (kn) above which direction arrows use maximum spacing. */
    val trackDirectionSpeedCeilingKn: Float = ykws.android.maro.config.AppConfig.trackDirectionSpeedCeilingKn,
    /** Minimum on-screen spacing (dp) between direction arrows. */
    val trackDirectionMinSpacingDp: Int = ykws.android.maro.config.AppConfig.trackDirectionMinSpacingDp,
    /** Maximum on-screen spacing (dp) between direction arrows. */
    val trackDirectionMaxSpacingDp: Int = ykws.android.maro.config.AppConfig.trackDirectionMaxSpacingDp,
    /** Number of historical tracks to render on the map (0-20). */
    val trackingRenderNb: Int = BuildConfig.TRACKING_RENDER_NB,
    /** ARGB color for the active recording track. */
    val trackingColorActive: Int = BuildConfig.TRACKING_COLOR_ACTIVE,
    /** ARGB color for historical tracks. */
    val trackingColorHistory: Int = BuildConfig.TRACKING_COLOR_HISTORY,
    /** ARGB end color for history track gradient (oldest track). */
    val trackingColorHistoryEnd: Int = BuildConfig.TRACKING_COLOR_HISTORY_END,
    /** ARGB color for pinned tracks (reserved for future use). */
    val trackingColorPinned: Int = BuildConfig.TRACKING_COLOR_PINNED,
    /**
     * ARGB start color for past track gradient (newest track).
     * Interpolates toward [trackingColorPastTo] for older tracks.
     */
    val trackingColorPastFrom: Int = BuildConfig.TRACKING_COLOR_PAST_FROM,
    /**
     * ARGB end color for past track gradient (oldest track).
     * Interpolated from [trackingColorPastFrom] for newer tracks.
     */
    val trackingColorPastTo: Int = BuildConfig.TRACKING_COLOR_PAST_TO,
    /**
     * Transparency % (0-100) for the NEWEST past (history) track.
     * 0 = fully opaque, 100 = fully invisible.
     * Lower value = newest track more visible.
     */
    val trackingTransparencyNewest: Int = BuildConfig.TRACKING_TRANSPARENCY_FROM,
    /**
     * Transparency % (0-100) for the OLDEST past (history) track.
     * 0 = fully opaque, 100 = fully invisible.
     * Higher value = oldest track more faded.
     */
    val trackingTransparencyOldest: Int = BuildConfig.TRACKING_TRANSPARENCY_TO,
    /**
     * Transparency % (0-100) for the NEWEST pinned track.
     * 0 = fully opaque, 100 = fully invisible.
     */
    val trackingTransparencyPinnedNewest: Int = BuildConfig.TRACKING_TRANSPARENCY_PINNED_FROM,
    /**
     * Transparency % (0-100) for the OLDEST pinned track.
     * 0 = fully opaque, 100 = fully invisible.
     */
    val trackingTransparencyPinnedOldest: Int = BuildConfig.TRACKING_TRANSPARENCY_PINNED_TO,
    /**
     * ARGB start color for pinned track gradient.
     */
    val trackingColorPinnedFrom: Int = BuildConfig.TRACKING_COLOR_PINNED_FROM,
    /**
     * ARGB end color for pinned track gradient.
     */
    val trackingColorPinnedTo: Int = BuildConfig.TRACKING_COLOR_PINNED_TO,
    /** Enable track point simplification at finalize (Douglas-Peucker + speed-aware). */
    val trackSimplifyEnabled: Boolean = true,
    /** Douglas-Peucker spatial tolerance (metres). */
    val trackSimplifyEpsilonM: Double = 3.0,
    /** Speed deviation threshold for reinsertion during simplification (knots). */
    val trackSimplifySpeedDeltaKn: Double = 3.0,
    /** Enable WhereAmI debug rays (green/red line-of-sight segments) on the map. */
    val markerDebugRays: Boolean = false,
    /** Sort state for the track history list (field + direction). */
    val trackListSort: ykws.android.maro.data.model.ListSortState = ykws.android.maro.data.model.ListSortState(),
    /** Sort state for the marker management list (field + direction). */
    val markerListSort: ykws.android.maro.data.model.ListSortState = ykws.android.maro.data.model.ListSortState(),
    /** Filter state for the track history list. */
    val trackListFilter: ykws.android.maro.data.model.ListFilter = ykws.android.maro.data.model.ListFilter(),
    /** Filter state for the marker management list. */
    val markerListFilter: ykws.android.maro.data.model.ListFilter = ykws.android.maro.data.model.ListFilter(),
    /** Filter state applied to the MAP overlay for tracks (decoupled from the list via [trackFilterLinked]). */
    val trackMapFilter: ykws.android.maro.data.model.ListFilter = ykws.android.maro.data.model.ListFilter(),
    /** Filter state applied to the MAP overlay for markers (decoupled from the list via [markerFilterLinked]). */
    val markerMapFilter: ykws.android.maro.data.model.ListFilter = ykws.android.maro.data.model.ListFilter(),
    /** When true the track list and map filters stay identical (editing one writes both). */
    val trackFilterLinked: Boolean = true,
    /** When true the marker list and map filters stay identical (editing one writes both). */
    val markerFilterLinked: Boolean = true,
    /** Enable automatic map offset in GPS navigation mode. Default true. */
    val mapOffsetGps: Boolean = true,
    /** Enable automatic map offset in demo/manual mode. Default false. */
    val mapOffsetDemo: Boolean = false,
    /** Boat position from screen bottom at full offset (%). 50=centered, 33=default, 5=extreme forward. */
    val mapOffsetBoatFromBottomPct: Int = 33,
) {
    /** Check whether a [ZoneDisplayCategory] is enabled in the current settings. */
    fun isCategoryVisible(cat: ZoneDisplayCategory): Boolean = when (cat) {
        ZoneDisplayCategory.NO_ANCHOR -> showCategoryNoAnchor
        ZoneDisplayCategory.MOORING -> showCategoryMooring
        ZoneDisplayCategory.SPEED_LIMIT -> showCategorySpeedLimit
        ZoneDisplayCategory.NO_DIVING -> showCategoryNoDiving
        ZoneDisplayCategory.SEAPLANE -> showCategorySeaplane
        ZoneDisplayCategory.NO_ACCESS -> showCategoryNoAccess
        ZoneDisplayCategory.FISHING_PROHIBITED -> showCategoryFishingProhibited
        ZoneDisplayCategory.ENVIRONMENTAL -> showCategoryEnvironmental
        ZoneDisplayCategory.INFORMATION -> showCategoryInformation
    }
}

class SettingsManager(
    context: Context,
    private val defaultAutoRevealDistM: Float = 200f,
    private val defaultAutoRevealTimeS: Int = 20
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // No settings versioning. Missing or outdated values fall back to defaults at read time, so
        // basic functionality is always preserved. Idempotent cleanup of keys the code no longer reads
        // (legacy keys replaced by later settings; harmless to remove on every start).
        // One-time absorb of the legacy battery-prompt flag (it used to live in its own prefs file),
        // so nobody is prompted a second time now that the flag is part of AppSettings.
        if (!prefs.contains(KEY_BATTERY_OPT_PROMPTED)) {
            val legacy = context.getSharedPreferences(LEGACY_BATTERY_PREFS, Context.MODE_PRIVATE)
                .getBoolean(LEGACY_BATTERY_OPT_PROMPTED, false)
            if (legacy) prefs.edit().putBoolean(KEY_BATTERY_OPT_PROMPTED, true).apply()
        }

        prefs.edit()
            .remove("user_markers_visible")          // replaced by marker_layer_state (v3)
            .remove("zone300_autoshow_gps")          // approach re-display rework (v6)
            .remove("zone300_autoshow_demo")
            .remove("speed_zones_visible")
            .remove("speed_zone_autoshow_gps")
            .remove("speed_zone_autoshow_demo")
            .remove("regulated_zone_autoshow_gps")
            .remove("regulated_zone_autoshow_demo")
            .remove("prefs_version")                 // versioning mechanism removed
            .apply()
    }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings = AppSettings(
        defaultLatitude  = prefs.getFloat(KEY_DEFAULT_LAT, 43.55f).toDouble(),
        defaultLongitude = prefs.getFloat(KEY_DEFAULT_LON, 7.00f).toDouble(),
        coastlineVisible = prefs.getBoolean(KEY_COASTLINE_VISIBLE, BuildConfig.LAYER_COASTLINE_DEFAULT),
        zone300Visible   = prefs.getBoolean(KEY_ZONE300_VISIBLE, BuildConfig.LAYER_ZONE300_DEFAULT),
        zoneAutoRevealDistanceM = prefs.getFloat(KEY_ZONE_AUTOREVEAL_DIST_M, defaultAutoRevealDistM),
        zoneAutoRevealTimeS     = prefs.getInt(KEY_ZONE_AUTOREVEAL_TIME_S, defaultAutoRevealTimeS),
        approachAutoShowGps = prefs.getBoolean(KEY_APPROACH_AUTOSHOW_GPS, true),
        approachAutoShowDemo = prefs.getBoolean(KEY_APPROACH_AUTOSHOW_DEMO, true),
        zone300AutoShow = prefs.getBoolean(KEY_ZONE300_AUTOSHOW, true),
        speedZoneAutoShow = prefs.getBoolean(KEY_SPEED_ZONE_AUTOSHOW, true),
        regulatedZoneAutoShow = prefs.getBoolean(KEY_REGULATED_ZONE_AUTOSHOW, true),
        autoShowMasterOverride = prefs.getBoolean(KEY_AUTO_SHOW_MASTER_OVERRIDE, true),
        gpsMode          = prefs.getBoolean(KEY_GPS_MODE, false),
        recenterDelaySeconds = prefs.getInt(KEY_RECENTER_DELAY_S, 5),
        gpsActiveIntervalSec = prefs.getInt(KEY_GPS_INTERVAL_S, 1),
        gpsActiveMinDistanceM = prefs.getFloat(KEY_GPS_MIN_DISTANCE_M, 0f),
        stopDetectionEnabled     = prefs.getBoolean(KEY_STOP_DETECTION_ENABLED, true),
        stopDetectionTimeSec     = prefs.getInt(KEY_STOP_DETECTION_TIME_S, 45),
        stopDetectionDistanceM   = prefs.getInt(KEY_STOP_DETECTION_DISTANCE_M, 15),
        stopDetectionDelayGps    = prefs.getBoolean(KEY_STOP_DETECTION_DELAY_GPS, true),
        mapRefreshFps    = prefs.getInt(KEY_MAP_REFRESH_FPS, 25),
        mapCenterLat     = prefs.getFloat(KEY_MAP_CENTER_LAT, Float.NaN).toDouble(),
        mapCenterLon     = prefs.getFloat(KEY_MAP_CENTER_LON, Float.NaN).toDouble(),
        zoomLevel        = prefs.getFloat(KEY_ZOOM_LEVEL, 0f).toDouble(),
        isWater          = prefs.getBoolean(KEY_IS_WATER, true),
        distanceToShore  = prefs.getFloat(KEY_DISTANCE_TO_SHORE, Float.NaN).toDouble(),
        languageCode     = prefs.getString(KEY_LANGUAGE_CODE, "system") ?: "system",
        keepScreenOn     = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false),
        keepScreenOnMovementGate = prefs.getBoolean(KEY_KEEP_SCREEN_ON_MOVEMENT_GATE, false),
        keepScreenOnSpeedThresholdKn = prefs.getFloat(
            KEY_KEEP_SCREEN_ON_SPEED_THRESHOLD_KN,
            ykws.android.maro.config.AppConfig.powerScreenMovementThresholdKn
        ),
        keepScreenOnGraceMinutes = prefs.getInt(
            KEY_KEEP_SCREEN_ON_GRACE_MIN,
            ykws.android.maro.config.AppConfig.powerScreenGraceDefaultMinutes
        ).coerceIn(
            ykws.android.maro.config.AppConfig.powerScreenGraceMinMinutes,
            ykws.android.maro.config.AppConfig.powerScreenGraceMaxMinutes
        ),
        batteryOptimizationPrompted = prefs.getBoolean(KEY_BATTERY_OPT_PROMPTED, false),
        lowDepthWarningVisible = prefs.getBoolean(KEY_LOW_DEPTH_WARNING_VISIBLE, true),
        lowDepthCrashDepthM = prefs.getFloat(KEY_LOW_DEPTH_CRASH_DEPTH_M, DepthConstants.LOW_DEPTH_CRASH_DEPTH_M.toFloat()),
        lowDepthStartWarningM = prefs.getFloat(KEY_LOW_DEPTH_START_WARNING_M, DepthConstants.LOW_DEPTH_START_WARNING_M.toFloat()),
        zone300Color = prefs.getInt(KEY_ZONE300_COLOR, 0xFFE53935.toInt()),
        zone300FillTransparencyPct = prefs.getInt(KEY_ZONE300_FILL_TRANSPARENCY_PCT, 80),
        zone300BoundaryTransparencyPct = prefs.getInt(KEY_ZONE300_BOUNDARY_TRANSPARENCY_PCT, 20),
        boatMarkerIdleThresholdSec = prefs.getLong(KEY_BOAT_MARKER_IDLE_THRESHOLD_S, ykws.android.maro.config.AppConfig.boatMarkerIdleThresholdSec),
        boatMarkerAutoMarkerMinDurationSec = prefs.getLong(KEY_BOAT_MARKER_AUTO_MIN_DURATION_S, ykws.android.maro.config.AppConfig.boatMarkerAutoMarkerMinDurationSec),
        boatMarkerAutoMarkerDedupRadiusM = prefs.getFloat(KEY_BOAT_MARKER_AUTO_DEDUP_RADIUS_M, ykws.android.maro.config.AppConfig.boatMarkerAutoMarkerDedupRadiusM.toFloat()).toDouble(),
        emodnetShallowCutoffM = prefs.getFloat(KEY_EMODNET_SHALLOW_CUTOFF_M, 2.0f),
        regenGrid    = prefs.getBoolean(KEY_REGEN_GRID, true),
        regenIsobaths = prefs.getBoolean(KEY_REGEN_ISOBATHS, true),
        regenColour  = prefs.getBoolean(KEY_REGEN_COLOUR, true),
        regenWarning = prefs.getBoolean(KEY_REGEN_WARNING, true),
        headingLineVisible = prefs.getBoolean(KEY_HEADING_LINE_VISIBLE, true),
        capArrowVisible   = prefs.getBoolean(KEY_CAP_ARROW_VISIBLE, false),
        depthLayerVisible = prefs.getBoolean(KEY_DEPTH_LAYER_VISIBLE, true),
        regulatedZonesVisible = prefs.getBoolean(KEY_REGULATED_ZONES_VISIBLE, BuildConfig.LAYER_REGULATED_ZONES_DEFAULT),
        regulationInfoVisible = prefs.getBoolean(KEY_REGULATION_INFO_VISIBLE, false),
        boatSizeM = prefs.getFloat(KEY_BOAT_SIZE_M, BuildConfig.REGULATED_ZONES_DEFAULT_VESSEL_LENGTH_M.toFloat()).toDouble(),
        showCategoryNoAnchor = prefs.getBoolean(KEY_SHOW_CATEGORY_NO_ANCHOR, true),
        showCategoryMooring = prefs.getBoolean(KEY_SHOW_CATEGORY_MOORING, false),
        showCategorySpeedLimit = prefs.getBoolean(KEY_SHOW_CATEGORY_SPEED_LIMIT, true),
        showCategoryNoDiving = prefs.getBoolean(KEY_SHOW_CATEGORY_NO_DIVING, true),
        showCategorySeaplane = prefs.getBoolean(KEY_SHOW_CATEGORY_SEAPLANE, false),
        showCategoryNoAccess = prefs.getBoolean(KEY_SHOW_CATEGORY_NO_ACCESS, true),
        showCategoryFishingProhibited = prefs.getBoolean(KEY_SHOW_CATEGORY_FISHING_PROHIBITED, false),
        showCategoryEnvironmental = prefs.getBoolean(KEY_SHOW_CATEGORY_ENVIRONMENTAL, true),
        showCategoryInformation = prefs.getBoolean(KEY_SHOW_CATEGORY_INFORMATION, false),
        demoHeadingUp = prefs.getBoolean(KEY_DEMO_HEADING_UP, false),
        gpsIdleMinDistanceM = prefs.getFloat(KEY_GPS_IDLE_MIN_DISTANCE_M, 0f),
        trackEnabled = prefs.getBoolean(KEY_TRACK_ENABLED, BuildConfig.TRACK_ENABLED_DEFAULT),
        trackOriginLat = prefs.getFloat(KEY_TRACK_ORIGIN_LAT, BuildConfig.TRACK_ORIGIN_LAT.toFloat()).toDouble(),
        trackOriginLon = prefs.getFloat(KEY_TRACK_ORIGIN_LON, BuildConfig.TRACK_ORIGIN_LON.toFloat()).toDouble(),
        trackGeofenceRadiusM = prefs.getFloat(KEY_TRACK_GEOFENCE_RADIUS_M, BuildConfig.TRACK_GEOFENCE_RADIUS_M.toFloat()).toDouble(),
        trackGeofenceEnabled = prefs.getBoolean(KEY_TRACK_GEOFENCE_ENABLED, true),
        markerLayerState = try {
            ykws.android.maro.ui.map.MarkerLayerState.valueOf(
                prefs.getString(KEY_MARKER_LAYER_STATE, "SHOW_ALL") ?: "SHOW_ALL")
        } catch (_: Exception) { ykws.android.maro.ui.map.MarkerLayerState.SHOW_ALL },
        markerZonesVisible = prefs.getBoolean(KEY_MARKER_ZONES_VISIBLE, true),
        markerHaloSize = prefs.getInt(KEY_MARKER_HALO_SIZE, 50),
        markerPointIconZoom = prefs.getInt(KEY_MARKER_POINT_ICON_ZOOM, 100),
        markerHaloPinnedColor = prefs.getInt(KEY_MARKER_HALO_PINNED_COLOR, 0xFFFFFFFF.toInt()),
        markerHaloUnpinnedColor = prefs.getInt(KEY_MARKER_HALO_UNPINNED_COLOR, 0xFF81D4FA.toInt()),
        markerHaloPinnedFillTransparencyPct = prefs.getInt(KEY_MARKER_HALO_PINNED_FILL_TRANSPARENCY_PCT, 75),
        markerHaloPinnedBorderTransparencyPct = prefs.getInt(KEY_MARKER_HALO_PINNED_BORDER_TRANSPARENCY_PCT, 20),
        markerHaloUnpinnedFillTransparencyPct = prefs.getInt(KEY_MARKER_HALO_UNPINNED_FILL_TRANSPARENCY_PCT, 90),
        markerHaloUnpinnedBorderTransparencyPct = prefs.getInt(KEY_MARKER_HALO_UNPINNED_BORDER_TRANSPARENCY_PCT, 60),
        tracksVisible = prefs.getBoolean(KEY_TRACKS_VISIBLE, true),
        tracksDirectionVisible = prefs.getBoolean(KEY_TRACKS_DIRECTION_VISIBLE, false),
        trackDirectionDensity = try {
            ykws.android.maro.ui.map.TrackDirectionDensity.valueOf(
                prefs.getString(KEY_TRACK_DIRECTION_DENSITY, "UNIFORM") ?: "UNIFORM")
        } catch (_: Exception) { ykws.android.maro.ui.map.TrackDirectionDensity.UNIFORM },
        trackDirectionSpeedFloorKn = prefs.getFloat(KEY_TRACK_DIRECTION_SPEED_FLOOR_KN, ykws.android.maro.config.AppConfig.trackDirectionSpeedFloorKn),
        trackDirectionSpeedCeilingKn = prefs.getFloat(KEY_TRACK_DIRECTION_SPEED_CEILING_KN, ykws.android.maro.config.AppConfig.trackDirectionSpeedCeilingKn),
        trackDirectionMinSpacingDp = prefs.getInt(KEY_TRACK_DIRECTION_MIN_SPACING_DP, ykws.android.maro.config.AppConfig.trackDirectionMinSpacingDp),
        trackDirectionMaxSpacingDp = prefs.getInt(KEY_TRACK_DIRECTION_MAX_SPACING_DP, ykws.android.maro.config.AppConfig.trackDirectionMaxSpacingDp),
        trackingRenderNb = prefs.getInt(KEY_TRACKING_RENDER_NB, BuildConfig.TRACKING_RENDER_NB).coerceIn(0, 20),
        trackingColorActive = prefs.getInt(KEY_TRACKING_COLOR_ACTIVE, BuildConfig.TRACKING_COLOR_ACTIVE),
        trackingColorHistory = prefs.getInt(KEY_TRACKING_COLOR_HISTORY, BuildConfig.TRACKING_COLOR_HISTORY),
        trackingColorHistoryEnd = prefs.getInt(KEY_TRACKING_COLOR_HISTORY_END, BuildConfig.TRACKING_COLOR_HISTORY_END),
        trackingColorPinned = prefs.getInt(KEY_TRACKING_COLOR_PINNED, BuildConfig.TRACKING_COLOR_PINNED),
        trackingColorPastFrom = prefs.getInt(KEY_TRACKING_COLOR_PAST_FROM, BuildConfig.TRACKING_COLOR_PAST_FROM),
        trackingColorPastTo = prefs.getInt(KEY_TRACKING_COLOR_PAST_TO, BuildConfig.TRACKING_COLOR_PAST_TO),
        trackingTransparencyNewest = prefs.getInt(KEY_TRACKING_TRANSPARENCY_NEWEST, BuildConfig.TRACKING_TRANSPARENCY_FROM),
        trackingTransparencyOldest = prefs.getInt(KEY_TRACKING_TRANSPARENCY_OLDEST, BuildConfig.TRACKING_TRANSPARENCY_TO),
        trackingTransparencyPinnedNewest = prefs.getInt(KEY_TRACKING_TRANSPARENCY_PINNED_NEWEST, BuildConfig.TRACKING_TRANSPARENCY_PINNED_FROM),
        trackingTransparencyPinnedOldest = prefs.getInt(KEY_TRACKING_TRANSPARENCY_PINNED_OLDEST, BuildConfig.TRACKING_TRANSPARENCY_PINNED_TO),
        trackingColorPinnedFrom = prefs.getInt(KEY_TRACKING_COLOR_PINNED_FROM, BuildConfig.TRACKING_COLOR_PINNED_FROM),
        trackingColorPinnedTo = prefs.getInt(KEY_TRACKING_COLOR_PINNED_TO, BuildConfig.TRACKING_COLOR_PINNED_TO),
        trackSimplifyEnabled = prefs.getBoolean(KEY_TRACK_SIMPLIFY_ENABLED, true),
        trackSimplifyEpsilonM = prefs.getFloat(KEY_TRACK_SIMPLIFY_EPSILON_M, 3.0f).toDouble(),
        trackSimplifySpeedDeltaKn = prefs.getFloat(KEY_TRACK_SIMPLIFY_SPEED_DELTA_KN, 3.0f).toDouble(),
        markerDebugRays = prefs.getBoolean(KEY_MARKER_DEBUG_RAYS, false),
        trackListSort = ykws.android.maro.data.model.ListSortState.parse(prefs.getString(KEY_TRACK_LIST_SORT, null)),
        markerListSort = ykws.android.maro.data.model.ListSortState.parse(prefs.getString(KEY_MARKER_LIST_SORT, null)),
        trackListFilter = ykws.android.maro.data.model.ListFilter.parse(prefs.getString(KEY_TRACK_LIST_FILTER, null)),
        markerListFilter = ykws.android.maro.data.model.ListFilter.parse(prefs.getString(KEY_MARKER_LIST_FILTER, null)),
        trackMapFilter = ykws.android.maro.data.model.ListFilter.parse(prefs.getString(KEY_TRACK_MAP_FILTER, null)),
        markerMapFilter = ykws.android.maro.data.model.ListFilter.parse(prefs.getString(KEY_MARKER_MAP_FILTER, null)),
        trackFilterLinked = prefs.getBoolean(KEY_TRACK_FILTER_LINKED, true),
        markerFilterLinked = prefs.getBoolean(KEY_MARKER_FILTER_LINKED, true),
        mapOffsetGps = prefs.getBoolean(KEY_MAP_OFFSET_GPS, true),
        mapOffsetDemo = prefs.getBoolean(KEY_MAP_OFFSET_DEMO, false),
        mapOffsetBoatFromBottomPct = prefs.getInt(KEY_MAP_OFFSET_BOAT_FROM_BOTTOM_PCT, 33).coerceIn(5, 50),
        maxRecordingAccuracyM = prefs.getFloat(KEY_MAX_RECORDING_ACCURACY_M, 30f),
    )

    /**
     * Atomically update one or more settings fields and persist to disk.
     *
     * Usage: `settingsManager.update { it.copy(coastlineVisible = false) }`
     */
    fun update(transform: (AppSettings) -> AppSettings) {
        val current = _settings.value
        val updated = transform(current)

        // Skip disk write + emission when nothing actually changed
        if (updated == current) return

        _settings.value = updated
        prefs.edit()
            .putFloat(KEY_DEFAULT_LAT, updated.defaultLatitude.toFloat())
            .putFloat(KEY_DEFAULT_LON, updated.defaultLongitude.toFloat())
            .putBoolean(KEY_COASTLINE_VISIBLE, updated.coastlineVisible)
            .putBoolean(KEY_ZONE300_VISIBLE, updated.zone300Visible)
            .putFloat(KEY_ZONE_AUTOREVEAL_DIST_M, updated.zoneAutoRevealDistanceM)
            .putInt(KEY_ZONE_AUTOREVEAL_TIME_S, updated.zoneAutoRevealTimeS)
            .putBoolean(KEY_APPROACH_AUTOSHOW_GPS, updated.approachAutoShowGps)
            .putBoolean(KEY_APPROACH_AUTOSHOW_DEMO, updated.approachAutoShowDemo)
            .putBoolean(KEY_ZONE300_AUTOSHOW, updated.zone300AutoShow)
            .putBoolean(KEY_SPEED_ZONE_AUTOSHOW, updated.speedZoneAutoShow)
            .putBoolean(KEY_REGULATED_ZONE_AUTOSHOW, updated.regulatedZoneAutoShow)
            .putBoolean(KEY_AUTO_SHOW_MASTER_OVERRIDE, updated.autoShowMasterOverride)
            .putBoolean(KEY_GPS_MODE, updated.gpsMode)
            .putInt(KEY_RECENTER_DELAY_S, updated.recenterDelaySeconds)
            .putInt(KEY_GPS_INTERVAL_S, updated.gpsActiveIntervalSec)
            .putFloat(KEY_GPS_MIN_DISTANCE_M, updated.gpsActiveMinDistanceM)
            .putBoolean(KEY_STOP_DETECTION_ENABLED, updated.stopDetectionEnabled)
            .putInt(KEY_STOP_DETECTION_TIME_S, updated.stopDetectionTimeSec)
            .putInt(KEY_STOP_DETECTION_DISTANCE_M, updated.stopDetectionDistanceM)
            .putBoolean(KEY_STOP_DETECTION_DELAY_GPS, updated.stopDetectionDelayGps)
            .putInt(KEY_MAP_REFRESH_FPS, updated.mapRefreshFps)
            .putFloat(KEY_MAP_CENTER_LAT, updated.mapCenterLat.toFloat())
            .putFloat(KEY_MAP_CENTER_LON, updated.mapCenterLon.toFloat())
            .putFloat(KEY_ZOOM_LEVEL, updated.zoomLevel.toFloat())
            .putBoolean(KEY_IS_WATER, updated.isWater)
            .putFloat(KEY_DISTANCE_TO_SHORE, updated.distanceToShore.toFloat())
            .putString(KEY_LANGUAGE_CODE, updated.languageCode)
            .putBoolean(KEY_KEEP_SCREEN_ON, updated.keepScreenOn)
            .putBoolean(KEY_KEEP_SCREEN_ON_MOVEMENT_GATE, updated.keepScreenOnMovementGate)
            .putFloat(KEY_KEEP_SCREEN_ON_SPEED_THRESHOLD_KN, updated.keepScreenOnSpeedThresholdKn)
            .putInt(KEY_KEEP_SCREEN_ON_GRACE_MIN, updated.keepScreenOnGraceMinutes)
            .putBoolean(KEY_BATTERY_OPT_PROMPTED, updated.batteryOptimizationPrompted)
            .putBoolean(KEY_LOW_DEPTH_WARNING_VISIBLE, updated.lowDepthWarningVisible)
            .putFloat(KEY_LOW_DEPTH_CRASH_DEPTH_M, updated.lowDepthCrashDepthM)
            .putFloat(KEY_LOW_DEPTH_START_WARNING_M, updated.lowDepthStartWarningM)
            .putInt(KEY_ZONE300_COLOR, updated.zone300Color)
            .putInt(KEY_ZONE300_FILL_TRANSPARENCY_PCT, updated.zone300FillTransparencyPct)
            .putInt(KEY_ZONE300_BOUNDARY_TRANSPARENCY_PCT, updated.zone300BoundaryTransparencyPct)
            .putLong(KEY_BOAT_MARKER_IDLE_THRESHOLD_S, updated.boatMarkerIdleThresholdSec)
            .putLong(KEY_BOAT_MARKER_AUTO_MIN_DURATION_S, updated.boatMarkerAutoMarkerMinDurationSec)
            .putFloat(KEY_BOAT_MARKER_AUTO_DEDUP_RADIUS_M, updated.boatMarkerAutoMarkerDedupRadiusM.toFloat())
            .putFloat(KEY_EMODNET_SHALLOW_CUTOFF_M, updated.emodnetShallowCutoffM)
            .putBoolean(KEY_REGEN_GRID, updated.regenGrid)
            .putBoolean(KEY_REGEN_ISOBATHS, updated.regenIsobaths)
            .putBoolean(KEY_REGEN_COLOUR, updated.regenColour)
            .putBoolean(KEY_REGEN_WARNING, updated.regenWarning)
            .putBoolean(KEY_HEADING_LINE_VISIBLE, updated.headingLineVisible)
            .putBoolean(KEY_CAP_ARROW_VISIBLE, updated.capArrowVisible)
            .putBoolean(KEY_DEPTH_LAYER_VISIBLE, updated.depthLayerVisible)
            .putBoolean(KEY_REGULATED_ZONES_VISIBLE, updated.regulatedZonesVisible)
            .putFloat(KEY_BOAT_SIZE_M, updated.boatSizeM.toFloat())
            .putBoolean(KEY_SHOW_CATEGORY_NO_ANCHOR, updated.showCategoryNoAnchor)
            .putBoolean(KEY_SHOW_CATEGORY_MOORING, updated.showCategoryMooring)
            .putBoolean(KEY_SHOW_CATEGORY_SPEED_LIMIT, updated.showCategorySpeedLimit)
            .putBoolean(KEY_SHOW_CATEGORY_NO_DIVING, updated.showCategoryNoDiving)
            .putBoolean(KEY_SHOW_CATEGORY_SEAPLANE, updated.showCategorySeaplane)
            .putBoolean(KEY_SHOW_CATEGORY_NO_ACCESS, updated.showCategoryNoAccess)
            .putBoolean(KEY_SHOW_CATEGORY_FISHING_PROHIBITED, updated.showCategoryFishingProhibited)
            .putBoolean(KEY_SHOW_CATEGORY_ENVIRONMENTAL, updated.showCategoryEnvironmental)
            .putBoolean(KEY_SHOW_CATEGORY_INFORMATION, updated.showCategoryInformation)
            .putBoolean(KEY_REGULATION_INFO_VISIBLE, updated.regulationInfoVisible)
            .putBoolean(KEY_DEMO_HEADING_UP, updated.demoHeadingUp)
            .putFloat(KEY_GPS_IDLE_MIN_DISTANCE_M, updated.gpsIdleMinDistanceM)
            .putBoolean(KEY_TRACK_ENABLED, updated.trackEnabled)
            .putFloat(KEY_TRACK_ORIGIN_LAT, updated.trackOriginLat.toFloat())
            .putFloat(KEY_TRACK_ORIGIN_LON, updated.trackOriginLon.toFloat())
            .putFloat(KEY_TRACK_GEOFENCE_RADIUS_M, updated.trackGeofenceRadiusM.toFloat())
            .putBoolean(KEY_TRACK_GEOFENCE_ENABLED, updated.trackGeofenceEnabled)
            .putString(KEY_MARKER_LAYER_STATE, updated.markerLayerState.name)
            .putBoolean(KEY_MARKER_ZONES_VISIBLE, updated.markerZonesVisible)
            .putInt(KEY_MARKER_HALO_SIZE, updated.markerHaloSize)
            .putInt(KEY_MARKER_POINT_ICON_ZOOM, updated.markerPointIconZoom)
            .putInt(KEY_MARKER_HALO_PINNED_COLOR, updated.markerHaloPinnedColor)
            .putInt(KEY_MARKER_HALO_UNPINNED_COLOR, updated.markerHaloUnpinnedColor)
            .putInt(KEY_MARKER_HALO_PINNED_FILL_TRANSPARENCY_PCT, updated.markerHaloPinnedFillTransparencyPct)
            .putInt(KEY_MARKER_HALO_PINNED_BORDER_TRANSPARENCY_PCT, updated.markerHaloPinnedBorderTransparencyPct)
            .putInt(KEY_MARKER_HALO_UNPINNED_FILL_TRANSPARENCY_PCT, updated.markerHaloUnpinnedFillTransparencyPct)
            .putInt(KEY_MARKER_HALO_UNPINNED_BORDER_TRANSPARENCY_PCT, updated.markerHaloUnpinnedBorderTransparencyPct)
            .putBoolean(KEY_TRACKS_VISIBLE, updated.tracksVisible)
            .putBoolean(KEY_TRACKS_DIRECTION_VISIBLE, updated.tracksDirectionVisible)
            .putString(KEY_TRACK_DIRECTION_DENSITY, updated.trackDirectionDensity.name)
            .putFloat(KEY_TRACK_DIRECTION_SPEED_FLOOR_KN, updated.trackDirectionSpeedFloorKn)
            .putFloat(KEY_TRACK_DIRECTION_SPEED_CEILING_KN, updated.trackDirectionSpeedCeilingKn)
            .putInt(KEY_TRACK_DIRECTION_MIN_SPACING_DP, updated.trackDirectionMinSpacingDp)
            .putInt(KEY_TRACK_DIRECTION_MAX_SPACING_DP, updated.trackDirectionMaxSpacingDp)
            .putInt(KEY_TRACKING_RENDER_NB, updated.trackingRenderNb)
            .putInt(KEY_TRACKING_COLOR_ACTIVE, updated.trackingColorActive)
            .putInt(KEY_TRACKING_COLOR_HISTORY, updated.trackingColorHistory)
            .putInt(KEY_TRACKING_COLOR_HISTORY_END, updated.trackingColorHistoryEnd)
            .putInt(KEY_TRACKING_COLOR_PINNED, updated.trackingColorPinned)
            .putInt(KEY_TRACKING_COLOR_PAST_FROM, updated.trackingColorPastFrom)
            .putInt(KEY_TRACKING_COLOR_PAST_TO, updated.trackingColorPastTo)
            .putInt(KEY_TRACKING_TRANSPARENCY_NEWEST, updated.trackingTransparencyNewest)
            .putInt(KEY_TRACKING_TRANSPARENCY_OLDEST, updated.trackingTransparencyOldest)
            .putInt(KEY_TRACKING_TRANSPARENCY_PINNED_NEWEST, updated.trackingTransparencyPinnedNewest)
            .putInt(KEY_TRACKING_TRANSPARENCY_PINNED_OLDEST, updated.trackingTransparencyPinnedOldest)
            .putInt(KEY_TRACKING_COLOR_PINNED_FROM, updated.trackingColorPinnedFrom)
            .putInt(KEY_TRACKING_COLOR_PINNED_TO, updated.trackingColorPinnedTo)
            .putBoolean(KEY_TRACK_SIMPLIFY_ENABLED, updated.trackSimplifyEnabled)
            .putFloat(KEY_TRACK_SIMPLIFY_EPSILON_M, updated.trackSimplifyEpsilonM.toFloat())
            .putFloat(KEY_TRACK_SIMPLIFY_SPEED_DELTA_KN, updated.trackSimplifySpeedDeltaKn.toFloat())
            .putBoolean(KEY_MARKER_DEBUG_RAYS, updated.markerDebugRays)
            .putString(KEY_TRACK_LIST_SORT, ykws.android.maro.data.model.ListSortState.format(updated.trackListSort))
            .putString(KEY_MARKER_LIST_SORT, ykws.android.maro.data.model.ListSortState.format(updated.markerListSort))
            .putString(KEY_TRACK_LIST_FILTER, ykws.android.maro.data.model.ListFilter.format(updated.trackListFilter))
            .putString(KEY_MARKER_LIST_FILTER, ykws.android.maro.data.model.ListFilter.format(updated.markerListFilter))
            .putString(KEY_TRACK_MAP_FILTER, ykws.android.maro.data.model.ListFilter.format(updated.trackMapFilter))
            .putString(KEY_MARKER_MAP_FILTER, ykws.android.maro.data.model.ListFilter.format(updated.markerMapFilter))
            .putBoolean(KEY_TRACK_FILTER_LINKED, updated.trackFilterLinked)
            .putBoolean(KEY_MARKER_FILTER_LINKED, updated.markerFilterLinked)
            .putBoolean(KEY_MAP_OFFSET_GPS, updated.mapOffsetGps)
            .putBoolean(KEY_MAP_OFFSET_DEMO, updated.mapOffsetDemo)
            .putInt(KEY_MAP_OFFSET_BOAT_FROM_BOTTOM_PCT, updated.mapOffsetBoatFromBottomPct)
            .putFloat(KEY_MAX_RECORDING_ACCURACY_M, updated.maxRecordingAccuracyM)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "maro_settings"
        private const val KEY_DEFAULT_LAT = "default_lat"
        private const val KEY_DEFAULT_LON = "default_lon"
        private const val KEY_COASTLINE_VISIBLE = "coastline_visible"
        private const val KEY_ZONE300_VISIBLE = "zone300_visible"
        private const val KEY_ZONE_AUTOREVEAL_DIST_M = "zone_autoreveal_dist_m"
        private const val KEY_ZONE_AUTOREVEAL_TIME_S = "zone_autoreveal_time_s"
        private const val KEY_APPROACH_AUTOSHOW_GPS = "approach_autoshow_gps"
        private const val KEY_APPROACH_AUTOSHOW_DEMO = "approach_autoshow_demo"
        private const val KEY_ZONE300_AUTOSHOW = "zone300_autoshow"
        private const val KEY_SPEED_ZONE_AUTOSHOW = "speed_zone_autoshow"
        private const val KEY_REGULATED_ZONE_AUTOSHOW = "regulated_zone_autoshow"
        private const val KEY_AUTO_SHOW_MASTER_OVERRIDE = "auto_show_master_override"
        private const val KEY_GPS_MODE = "gps_mode"
        private const val KEY_RECENTER_DELAY_S = "recenter_delay_s"
        private const val KEY_GPS_INTERVAL_S = "gps_interval_s"
        private const val KEY_GPS_MIN_DISTANCE_M = "gps_min_distance_m"
        private const val KEY_STOP_DETECTION_ENABLED = "stop_detection_enabled"
        private const val KEY_STOP_DETECTION_TIME_S = "stop_detection_time_s"
        private const val KEY_STOP_DETECTION_DISTANCE_M = "stop_detection_distance_m"
        private const val KEY_STOP_DETECTION_DELAY_GPS = "stop_detection_delay_gps"
        private const val KEY_MAP_REFRESH_FPS = "map_refresh_fps"
        private const val KEY_MAP_CENTER_LAT = "map_center_lat"
        private const val KEY_MAP_CENTER_LON = "map_center_lon"
        private const val KEY_ZOOM_LEVEL = "zoom_level"
        private const val KEY_IS_WATER = "is_water"
        private const val KEY_DISTANCE_TO_SHORE = "distance_to_shore"
        private const val KEY_LANGUAGE_CODE = "language_code"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_KEEP_SCREEN_ON_MOVEMENT_GATE = "keep_screen_on_movement_gate"
        private const val KEY_KEEP_SCREEN_ON_SPEED_THRESHOLD_KN = "keep_screen_on_speed_threshold_kn"
        private const val KEY_KEEP_SCREEN_ON_GRACE_MIN = "keep_screen_on_grace_min"
        private const val KEY_BATTERY_OPT_PROMPTED = "battery_optimization_prompted"
        /** Legacy store the prompt flag used to live in, plus its key — read once by the migration. */
        private const val LEGACY_BATTERY_PREFS = "maro_battery_prefs"
        private const val LEGACY_BATTERY_OPT_PROMPTED = "battery_opt_prompted"
        private const val KEY_LOW_DEPTH_WARNING_VISIBLE = "low_depth_warning_visible"
        private const val KEY_LOW_DEPTH_CRASH_DEPTH_M = "low_depth_crash_depth_m"
        private const val KEY_LOW_DEPTH_START_WARNING_M = "low_depth_start_warning_m"
        private const val KEY_ZONE300_COLOR = "zone300_fill_color"
        private const val KEY_ZONE300_FILL_TRANSPARENCY_PCT = "zone300_fill_transparency_pct"
        private const val KEY_ZONE300_BOUNDARY_TRANSPARENCY_PCT = "zone300_boundary_transparency_pct"
        private const val KEY_BOAT_MARKER_IDLE_THRESHOLD_S = "boat_marker_idle_threshold_s"
        private const val KEY_BOAT_MARKER_AUTO_MIN_DURATION_S = "boat_marker_auto_min_duration_s"
        private const val KEY_BOAT_MARKER_AUTO_DEDUP_RADIUS_M = "boat_marker_auto_dedup_radius_m"
        private const val KEY_EMODNET_SHALLOW_CUTOFF_M = "emodnet_shallow_cutoff_m"
        private const val KEY_REGEN_GRID = "regen_grid"
        private const val KEY_REGEN_ISOBATHS = "regen_isobaths"
        private const val KEY_REGEN_COLOUR = "regen_colour"
        private const val KEY_REGEN_WARNING = "regen_warning"
        private const val KEY_HEADING_LINE_VISIBLE = "heading_line_visible"
        private const val KEY_CAP_ARROW_VISIBLE = "cap_arrow_visible"
        private const val KEY_DEPTH_LAYER_VISIBLE = "depth_layer_visible"
        private const val KEY_REGULATED_ZONES_VISIBLE = "regulated_zones_visible"
        private const val KEY_BOAT_SIZE_M = "boat_size_m"
        private const val KEY_SHOW_CATEGORY_NO_ANCHOR = "show_category_no_anchor"
        private const val KEY_SHOW_CATEGORY_MOORING = "show_category_mooring"
        private const val KEY_SHOW_CATEGORY_SPEED_LIMIT = "show_category_speed_limit"
        private const val KEY_SHOW_CATEGORY_NO_DIVING = "show_category_no_diving"
        private const val KEY_SHOW_CATEGORY_SEAPLANE = "show_category_seaplane"
        private const val KEY_SHOW_CATEGORY_NO_ACCESS = "show_category_no_access"
        private const val KEY_SHOW_CATEGORY_FISHING_PROHIBITED = "show_category_fishing_prohibited"
        private const val KEY_SHOW_CATEGORY_ENVIRONMENTAL = "show_category_environmental"
        private const val KEY_SHOW_CATEGORY_INFORMATION = "show_category_information"
        private const val KEY_REGULATION_INFO_VISIBLE = "regulation_info_visible"
        private const val KEY_DEMO_HEADING_UP = "demo_heading_up"
        private const val KEY_GPS_IDLE_MIN_DISTANCE_M = "gps_idle_min_distance_m"
        private const val KEY_TRACK_ENABLED = "track_enabled"
        private const val KEY_TRACK_ORIGIN_LAT = "track_origin_lat"
        private const val KEY_TRACK_ORIGIN_LON = "track_origin_lon"
        private const val KEY_TRACK_GEOFENCE_RADIUS_M = "track_geofence_radius_m"
        private const val KEY_TRACK_GEOFENCE_ENABLED = "track_geofence_enabled"
        private const val KEY_TRACKS_VISIBLE = "tracks_visible"
        private const val KEY_TRACKS_DIRECTION_VISIBLE = "tracks_direction_visible"
        private const val KEY_TRACK_DIRECTION_DENSITY = "track_direction_density"
        private const val KEY_TRACK_DIRECTION_SPEED_FLOOR_KN = "track_direction_speed_floor_kn"
        private const val KEY_TRACK_DIRECTION_SPEED_CEILING_KN = "track_direction_speed_ceiling_kn"
        private const val KEY_TRACK_DIRECTION_MIN_SPACING_DP = "track_direction_min_spacing_dp"
        private const val KEY_TRACK_DIRECTION_MAX_SPACING_DP = "track_direction_max_spacing_dp"
        private const val KEY_MARKER_LAYER_STATE = "marker_layer_state"
        private const val KEY_MARKER_ZONES_VISIBLE = "marker_zones_visible"
        private const val KEY_MARKER_HALO_SIZE = "marker_halo_size"
        private const val KEY_MARKER_POINT_ICON_ZOOM = "marker_point_icon_zoom"
        private const val KEY_MARKER_HALO_PINNED_COLOR = "marker_halo_pinned_color"
        private const val KEY_MARKER_HALO_UNPINNED_COLOR = "marker_halo_unpinned_color"
        private const val KEY_MARKER_HALO_PINNED_FILL_TRANSPARENCY_PCT = "marker_halo_pinned_fill_transparency_pct"
        private const val KEY_MARKER_HALO_PINNED_BORDER_TRANSPARENCY_PCT = "marker_halo_pinned_border_transparency_pct"
        private const val KEY_MARKER_HALO_UNPINNED_FILL_TRANSPARENCY_PCT = "marker_halo_unpinned_fill_transparency_pct"
        private const val KEY_MARKER_HALO_UNPINNED_BORDER_TRANSPARENCY_PCT = "marker_halo_unpinned_border_transparency_pct"
        private const val KEY_TRACKING_RENDER_NB = "tracking_render_nb"
        private const val KEY_TRACKING_COLOR_ACTIVE = "tracking_color_active"
        private const val KEY_TRACKING_COLOR_HISTORY = "tracking_color_history"
        private const val KEY_TRACKING_COLOR_HISTORY_END = "tracking_color_history_end"
        private const val KEY_TRACKING_COLOR_PINNED = "tracking_color_pinned"
        private const val KEY_TRACKING_COLOR_PAST_FROM = "tracking_color_past_from"
        private const val KEY_TRACKING_COLOR_PAST_TO = "tracking_color_past_to"
        private const val KEY_TRACKING_TRANSPARENCY_NEWEST = "tracking_transparency_newest"
        private const val KEY_TRACKING_TRANSPARENCY_OLDEST = "tracking_transparency_oldest"
        private const val KEY_TRACKING_TRANSPARENCY_PINNED_NEWEST = "tracking_transparency_pinned_newest"
        private const val KEY_TRACKING_TRANSPARENCY_PINNED_OLDEST = "tracking_transparency_pinned_oldest"
        private const val KEY_TRACKING_COLOR_PINNED_FROM = "tracking_color_pinned_from"
        private const val KEY_TRACKING_COLOR_PINNED_TO = "tracking_color_pinned_to"
        private const val KEY_TRACK_SIMPLIFY_ENABLED = "track_simplify_enabled"
        private const val KEY_TRACK_SIMPLIFY_EPSILON_M = "track_simplify_epsilon_m"
        private const val KEY_TRACK_SIMPLIFY_SPEED_DELTA_KN = "track_simplify_speed_delta_kn"
        private const val KEY_MARKER_DEBUG_RAYS = "marker_debug_rays"
        private const val KEY_TRACK_LIST_SORT = "track_list_sort"
        private const val KEY_MARKER_LIST_SORT = "marker_list_sort"
        private const val KEY_TRACK_LIST_FILTER = "track_list_filter"
        private const val KEY_MARKER_LIST_FILTER = "marker_list_filter"
        private const val KEY_TRACK_MAP_FILTER = "track_map_filter"
        private const val KEY_MARKER_MAP_FILTER = "marker_map_filter"
        private const val KEY_TRACK_FILTER_LINKED = "track_filter_linked"
        private const val KEY_MARKER_FILTER_LINKED = "marker_filter_linked"
        private const val KEY_MAP_OFFSET_GPS = "map_offset_gps"
        private const val KEY_MAP_OFFSET_DEMO = "map_offset_demo"
        private const val KEY_MAP_OFFSET_BOAT_FROM_BOTTOM_PCT = "map_offset_boat_from_bottom_pct"
        private const val KEY_MAX_RECORDING_ACCURACY_M = "max_recording_accuracy_m"
    }
}
