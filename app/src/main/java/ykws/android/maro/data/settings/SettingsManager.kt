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
    val zone300AutoShowGps: Boolean = true,
    val zone300AutoShowDemo: Boolean = true,
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
    /** Keep the device screen awake while the app is in the foreground. */
    val keepScreenOn: Boolean = true,
    /** Highlight charted shallow water as a bright grounding-hazard overlay. */
    val lowDepthWarningVisible: Boolean = true,
    /** Depth threshold (m) for the low-depth warning: cells shallower than this are painted. */
    val lowDepthWarningMaxM: Float = DepthConstants.LOW_DEPTH_WARNING_MAX_M.toFloat(),
    /** Min opacity (%, 0–100) of the low-depth warning at the threshold; 100 % at the shoreline fades to this. */
    val lowDepthWarningMinOpacityPct: Int = 25,
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
    /** Whether the regulation info expander in settings is expanded. */
    val regulationInfoExpanded: Boolean = false,
    /** Whether the category visibility expander in settings is expanded. */
    val categoryFilterExpanded: Boolean = false,
    /** Whether the boat size expander in settings is expanded. */
    val boatSizeFilterExpanded: Boolean = false,
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
     * Whether the speed zone (SHOM speed-limited regulated zones) overlay is visible.
     * Separate from [regulatedZonesVisible] — speed zones have their own auto-show logic.
     */
    val speedZonesVisible: Boolean = false,
    /**
     * GPS mode: auto-reveal the hidden speed zone overlay when approaching a speed-limited zone.
     * When off, the overlay stays under manual control in GPS mode.
     */
    val speedZoneAutoShowGps: Boolean = true,
    /**
     * Demo mode: same approach auto-reveal, driven by pan speed.
     */
    val speedZoneAutoShowDemo: Boolean = true,
    /**
     * GPS mode: auto-reveal the regulated zone overlay when approaching a speed-enforced zone.
     * When off, the overlay stays under manual control in GPS mode.
     */
    val regulatedZoneAutoShowGps: Boolean = true,
    /**
     * Demo mode: same approach auto-reveal for regulated zone overlay, driven by pan speed.
     */
    val regulatedZoneAutoShowDemo: Boolean = true,
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
    /** Whether the tracks overlay layer is visible on the map. */
    val tracksVisible: Boolean = true,
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
     * Opacity % (0-100) for the NEWEST past track.
     * 0 = fully invisible, 100 = fully opaque.
     * Higher value = newest track more visible.
     */
    val trackingOpacityNewest: Int = BuildConfig.TRACKING_TRANSPARENCY_FROM,
    /**
     * Opacity % (0-100) for the OLDEST past track.
     * 0 = fully invisible, 100 = fully opaque.
     * Lower value = oldest track more faded.
     */
    val trackingOpacityOldest: Int = BuildConfig.TRACKING_TRANSPARENCY_TO,
    /**
     * ARGB start color for pinned track gradient.
     * Reserved for future use — pinned tracks not yet implemented.
     */
    val trackingColorPinnedFrom: Int = BuildConfig.TRACKING_COLOR_PINNED_FROM,
    /**
     * ARGB end color for pinned track gradient.
     * Reserved for future use — pinned tracks not yet implemented.
     */
    val trackingColorPinnedTo: Int = BuildConfig.TRACKING_COLOR_PINNED_TO
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
    private val defaultAutoRevealTimeS: Int = 20,
    private val defaultLowDepthMinOpacityPct: Int = 25
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Migration: reset stale values when prefs version changes.
        val savedVersion = prefs.getInt(KEY_PREFS_VERSION, 1)
        if (savedVersion < CURRENT_VERSION) {
            prefs.edit()
                .remove(KEY_ZONE_AUTOREVEAL_DIST_M)
                .remove(KEY_ZONE_AUTOREVEAL_TIME_S)
                .putInt(KEY_PREFS_VERSION, CURRENT_VERSION)
                .apply()
        }
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
        zone300AutoShowGps  = prefs.getBoolean(KEY_ZONE300_AUTOSHOW_GPS, true),
        zone300AutoShowDemo = prefs.getBoolean(KEY_ZONE300_AUTOSHOW_DEMO, true),
        gpsMode          = prefs.getBoolean(KEY_GPS_MODE, false),
        recenterDelaySeconds = prefs.getInt(KEY_RECENTER_DELAY_S, 5),
        gpsActiveIntervalSec = prefs.getInt(KEY_GPS_INTERVAL_S, 2),
        gpsActiveMinDistanceM = prefs.getFloat(KEY_GPS_MIN_DISTANCE_M, 5f),
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
        lowDepthWarningVisible = prefs.getBoolean(KEY_LOW_DEPTH_WARNING_VISIBLE, true),
        lowDepthWarningMaxM = prefs.getFloat(KEY_LOW_DEPTH_WARNING_MAX_M, DepthConstants.LOW_DEPTH_WARNING_MAX_M.toFloat()),
        lowDepthWarningMinOpacityPct = prefs.getInt(KEY_LOW_DEPTH_MIN_OPACITY_PCT, defaultLowDepthMinOpacityPct),
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
        regulationInfoExpanded = prefs.getBoolean(KEY_REGULATION_INFO_EXPANDED, false),
        boatSizeM = prefs.getFloat(KEY_BOAT_SIZE_M, BuildConfig.REGULATED_ZONES_DEFAULT_VESSEL_LENGTH_M.toFloat()).toDouble(),
        categoryFilterExpanded = prefs.getBoolean(KEY_CATEGORY_FILTER_EXPANDED, false),
        boatSizeFilterExpanded = prefs.getBoolean(KEY_BOAT_SIZE_FILTER_EXPANDED, false),
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
        speedZonesVisible = prefs.getBoolean(KEY_SPEED_ZONES_VISIBLE, false),
        speedZoneAutoShowGps = prefs.getBoolean(KEY_SPEED_ZONE_AUTOSHOW_GPS, true),
        speedZoneAutoShowDemo = prefs.getBoolean(KEY_SPEED_ZONE_AUTOSHOW_DEMO, true),
        regulatedZoneAutoShowGps = prefs.getBoolean(KEY_REGULATED_ZONE_AUTOSHOW_GPS, true),
        regulatedZoneAutoShowDemo = prefs.getBoolean(KEY_REGULATED_ZONE_AUTOSHOW_DEMO, true),
        gpsIdleMinDistanceM = prefs.getFloat(KEY_GPS_IDLE_MIN_DISTANCE_M, 0f),
        trackEnabled = prefs.getBoolean(KEY_TRACK_ENABLED, BuildConfig.TRACK_ENABLED_DEFAULT),
        trackOriginLat = prefs.getFloat(KEY_TRACK_ORIGIN_LAT, BuildConfig.TRACK_ORIGIN_LAT.toFloat()).toDouble(),
        trackOriginLon = prefs.getFloat(KEY_TRACK_ORIGIN_LON, BuildConfig.TRACK_ORIGIN_LON.toFloat()).toDouble(),
        trackGeofenceRadiusM = prefs.getFloat(KEY_TRACK_GEOFENCE_RADIUS_M, BuildConfig.TRACK_GEOFENCE_RADIUS_M.toFloat()).toDouble(),
        trackGeofenceEnabled = prefs.getBoolean(KEY_TRACK_GEOFENCE_ENABLED, true),
        tracksVisible = prefs.getBoolean(KEY_TRACKS_VISIBLE, true),
        trackingRenderNb = prefs.getInt(KEY_TRACKING_RENDER_NB, BuildConfig.TRACKING_RENDER_NB).coerceIn(0, 20),
        trackingColorActive = prefs.getInt(KEY_TRACKING_COLOR_ACTIVE, BuildConfig.TRACKING_COLOR_ACTIVE),
        trackingColorHistory = prefs.getInt(KEY_TRACKING_COLOR_HISTORY, BuildConfig.TRACKING_COLOR_HISTORY),
        trackingColorHistoryEnd = prefs.getInt(KEY_TRACKING_COLOR_HISTORY_END, BuildConfig.TRACKING_COLOR_HISTORY_END),
        trackingColorPinned = prefs.getInt(KEY_TRACKING_COLOR_PINNED, BuildConfig.TRACKING_COLOR_PINNED),
        trackingColorPastFrom = prefs.getInt(KEY_TRACKING_COLOR_PAST_FROM, BuildConfig.TRACKING_COLOR_PAST_FROM),
        trackingColorPastTo = prefs.getInt(KEY_TRACKING_COLOR_PAST_TO, BuildConfig.TRACKING_COLOR_PAST_TO),
        trackingOpacityNewest = prefs.getInt(KEY_TRACKING_OPACITY_NEWEST, BuildConfig.TRACKING_TRANSPARENCY_FROM),
        trackingOpacityOldest = prefs.getInt(KEY_TRACKING_OPACITY_OLDEST, BuildConfig.TRACKING_TRANSPARENCY_TO),
        trackingColorPinnedFrom = prefs.getInt(KEY_TRACKING_COLOR_PINNED_FROM, BuildConfig.TRACKING_COLOR_PINNED_FROM),
        trackingColorPinnedTo = prefs.getInt(KEY_TRACKING_COLOR_PINNED_TO, BuildConfig.TRACKING_COLOR_PINNED_TO)
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
            .putBoolean(KEY_ZONE300_AUTOSHOW_GPS, updated.zone300AutoShowGps)
            .putBoolean(KEY_ZONE300_AUTOSHOW_DEMO, updated.zone300AutoShowDemo)
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
            .putBoolean(KEY_LOW_DEPTH_WARNING_VISIBLE, updated.lowDepthWarningVisible)
            .putFloat(KEY_LOW_DEPTH_WARNING_MAX_M, updated.lowDepthWarningMaxM)
            .putInt(KEY_LOW_DEPTH_MIN_OPACITY_PCT, updated.lowDepthWarningMinOpacityPct)
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
            .putBoolean(KEY_REGULATION_INFO_EXPANDED, updated.regulationInfoExpanded)
            .putBoolean(KEY_CATEGORY_FILTER_EXPANDED, updated.categoryFilterExpanded)
            .putBoolean(KEY_BOAT_SIZE_FILTER_EXPANDED, updated.boatSizeFilterExpanded)
            .putBoolean(KEY_DEMO_HEADING_UP, updated.demoHeadingUp)
            .putBoolean(KEY_SPEED_ZONES_VISIBLE, updated.speedZonesVisible)
            .putBoolean(KEY_SPEED_ZONE_AUTOSHOW_GPS, updated.speedZoneAutoShowGps)
            .putBoolean(KEY_SPEED_ZONE_AUTOSHOW_DEMO, updated.speedZoneAutoShowDemo)
            .putBoolean(KEY_REGULATED_ZONE_AUTOSHOW_GPS, updated.regulatedZoneAutoShowGps)
            .putBoolean(KEY_REGULATED_ZONE_AUTOSHOW_DEMO, updated.regulatedZoneAutoShowDemo)
            .putFloat(KEY_GPS_IDLE_MIN_DISTANCE_M, updated.gpsIdleMinDistanceM)
            .putBoolean(KEY_TRACK_ENABLED, updated.trackEnabled)
            .putFloat(KEY_TRACK_ORIGIN_LAT, updated.trackOriginLat.toFloat())
            .putFloat(KEY_TRACK_ORIGIN_LON, updated.trackOriginLon.toFloat())
            .putFloat(KEY_TRACK_GEOFENCE_RADIUS_M, updated.trackGeofenceRadiusM.toFloat())
            .putBoolean(KEY_TRACK_GEOFENCE_ENABLED, updated.trackGeofenceEnabled)
            .putBoolean(KEY_TRACKS_VISIBLE, updated.tracksVisible)
            .putInt(KEY_TRACKING_RENDER_NB, updated.trackingRenderNb)
            .putInt(KEY_TRACKING_COLOR_ACTIVE, updated.trackingColorActive)
            .putInt(KEY_TRACKING_COLOR_HISTORY, updated.trackingColorHistory)
            .putInt(KEY_TRACKING_COLOR_HISTORY_END, updated.trackingColorHistoryEnd)
            .putInt(KEY_TRACKING_COLOR_PINNED, updated.trackingColorPinned)
            .putInt(KEY_TRACKING_COLOR_PAST_FROM, updated.trackingColorPastFrom)
            .putInt(KEY_TRACKING_COLOR_PAST_TO, updated.trackingColorPastTo)
            .putInt(KEY_TRACKING_OPACITY_NEWEST, updated.trackingOpacityNewest)
            .putInt(KEY_TRACKING_OPACITY_OLDEST, updated.trackingOpacityOldest)
            .putInt(KEY_TRACKING_COLOR_PINNED_FROM, updated.trackingColorPinnedFrom)
            .putInt(KEY_TRACKING_COLOR_PINNED_TO, updated.trackingColorPinnedTo)
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
        private const val KEY_ZONE300_AUTOSHOW_GPS = "zone300_autoshow_gps"
        private const val KEY_ZONE300_AUTOSHOW_DEMO = "zone300_autoshow_demo"
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
        private const val KEY_LOW_DEPTH_WARNING_VISIBLE = "low_depth_warning_visible"
        private const val KEY_LOW_DEPTH_WARNING_MAX_M = "low_depth_warning_max_m"
        private const val KEY_LOW_DEPTH_MIN_OPACITY_PCT = "low_depth_min_opacity_pct"
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
        private const val KEY_CATEGORY_FILTER_EXPANDED = "category_filter_expanded"
        private const val KEY_BOAT_SIZE_FILTER_EXPANDED = "boat_size_filter_expanded"
        private const val KEY_REGULATION_INFO_VISIBLE = "regulation_info_visible"
        private const val KEY_REGULATION_INFO_EXPANDED = "regulation_info_expanded"
        private const val KEY_DEMO_HEADING_UP = "demo_heading_up"
        private const val KEY_SPEED_ZONES_VISIBLE = "speed_zones_visible"
        private const val KEY_SPEED_ZONE_AUTOSHOW_GPS = "speed_zone_autoshow_gps"
        private const val KEY_SPEED_ZONE_AUTOSHOW_DEMO = "speed_zone_autoshow_demo"
        private const val KEY_REGULATED_ZONE_AUTOSHOW_GPS = "regulated_zone_autoshow_gps"
        private const val KEY_REGULATED_ZONE_AUTOSHOW_DEMO = "regulated_zone_autoshow_demo"
        private const val KEY_GPS_IDLE_MIN_DISTANCE_M = "gps_idle_min_distance_m"
        private const val KEY_TRACK_ENABLED = "track_enabled"
        private const val KEY_TRACK_ORIGIN_LAT = "track_origin_lat"
        private const val KEY_TRACK_ORIGIN_LON = "track_origin_lon"
        private const val KEY_TRACK_GEOFENCE_RADIUS_M = "track_geofence_radius_m"
        private const val KEY_TRACK_GEOFENCE_ENABLED = "track_geofence_enabled"
        private const val KEY_TRACKS_VISIBLE = "tracks_visible"
        private const val KEY_TRACKING_RENDER_NB = "tracking_render_nb"
        private const val KEY_TRACKING_COLOR_ACTIVE = "tracking_color_active"
        private const val KEY_TRACKING_COLOR_HISTORY = "tracking_color_history"
        private const val KEY_TRACKING_COLOR_HISTORY_END = "tracking_color_history_end"
        private const val KEY_TRACKING_COLOR_PINNED = "tracking_color_pinned"
        private const val KEY_TRACKING_COLOR_PAST_FROM = "tracking_color_past_from"
        private const val KEY_TRACKING_COLOR_PAST_TO = "tracking_color_past_to"
        private const val KEY_TRACKING_OPACITY_NEWEST = "tracking_opacity_newest"
        private const val KEY_TRACKING_OPACITY_OLDEST = "tracking_opacity_oldest"
        private const val KEY_TRACKING_COLOR_PINNED_FROM = "tracking_color_pinned_from"
        private const val KEY_TRACKING_COLOR_PINNED_TO = "tracking_color_pinned_to"
        private const val KEY_PREFS_VERSION = "prefs_version"
        private const val CURRENT_VERSION = 2
    }
}
