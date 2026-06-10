package ykws.android.maro.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ykws.android.maro.data.depth.DepthConstants

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
 * @property adaptiveWindowSec      Adaptive idle detector: seconds of sub-threshold movement before
 *                             dropping to the idle fix rate (15–60). Default 30.
 * @property adaptiveDistanceM      Adaptive idle detector: max displacement (m) still counted as
 *                             "stationary" (10–30). Default 20.
 * @property adaptiveIdleIntervalSec  Adaptive idle: seconds between fixes once stationary (4–15).
 *                             Default 6.
 * @property mapRefreshFps     GPS auto-follow re-render ceiling in frames/s (5–50). Lower = fewer
 *                             whole-map repaints = less battery. Default 25.
 * @property emodnetShallowCutoffM  EMODnet shallow cutoff (m): EMODnet point readings shallower
 *                             than this are coarse (115 m cell over rocks/coast) and unreliable →
 *                             presented as no-data. 0 disables the gate. Default 2.0.
 */
data class AppSettings(
    val defaultLatitude: Double = 43.55,
    val defaultLongitude: Double = 7.00,
    val coastlineVisible: Boolean = true,
    val zone300Visible: Boolean = true,
    val zoneAutoRevealDistanceM: Float = 200f,
    val zoneAutoRevealTimeS: Int = 20,
    val zone300AutoShowGps: Boolean = true,
    val zone300AutoShowDemo: Boolean = true,
    val gpsMode: Boolean = false,
    val recenterDelaySeconds: Int = 5,
    val gpsActiveIntervalSec: Int = 2,
    val gpsActiveMinDistanceM: Float = 5f,
    val adaptiveWindowSec: Int = 30,
    val adaptiveDistanceM: Int = 20,
    val adaptiveIdleIntervalSec: Int = 6,
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
    val capArrowVisible: Boolean = false
)

class SettingsManager(
    context: Context,
    private val defaultAutoRevealDistM: Float = 200f,
    private val defaultAutoRevealTimeS: Int = 20,
    private val defaultLowDepthMinOpacityPct: Int = 25
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings = AppSettings(
        defaultLatitude  = prefs.getFloat(KEY_DEFAULT_LAT, 43.55f).toDouble(),
        defaultLongitude = prefs.getFloat(KEY_DEFAULT_LON, 7.00f).toDouble(),
        coastlineVisible = prefs.getBoolean(KEY_COASTLINE_VISIBLE, true),
        zone300Visible   = prefs.getBoolean(KEY_ZONE300_VISIBLE, true),
        zoneAutoRevealDistanceM = prefs.getFloat(KEY_ZONE_AUTOREVEAL_DIST_M, defaultAutoRevealDistM),
        zoneAutoRevealTimeS     = prefs.getInt(KEY_ZONE_AUTOREVEAL_TIME_S, defaultAutoRevealTimeS),
        zone300AutoShowGps  = prefs.getBoolean(KEY_ZONE300_AUTOSHOW_GPS, true),
        zone300AutoShowDemo = prefs.getBoolean(KEY_ZONE300_AUTOSHOW_DEMO, true),
        gpsMode          = prefs.getBoolean(KEY_GPS_MODE, false),
        recenterDelaySeconds = prefs.getInt(KEY_RECENTER_DELAY_S, 5),
        gpsActiveIntervalSec = prefs.getInt(KEY_GPS_INTERVAL_S, 2),
        gpsActiveMinDistanceM = prefs.getFloat(KEY_GPS_MIN_DISTANCE_M, 5f),
        adaptiveWindowSec     = prefs.getInt(KEY_ADAPTIVE_WINDOW_S, 30),
        adaptiveDistanceM     = prefs.getInt(KEY_ADAPTIVE_DISTANCE_M, 20),
        adaptiveIdleIntervalSec = prefs.getInt(KEY_ADAPTIVE_IDLE_S, 6),
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
        capArrowVisible   = prefs.getBoolean(KEY_CAP_ARROW_VISIBLE, false)
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
            .putInt(KEY_ADAPTIVE_WINDOW_S, updated.adaptiveWindowSec)
            .putInt(KEY_ADAPTIVE_DISTANCE_M, updated.adaptiveDistanceM)
            .putInt(KEY_ADAPTIVE_IDLE_S, updated.adaptiveIdleIntervalSec)
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
        private const val KEY_ADAPTIVE_WINDOW_S = "adaptive_window_s"
        private const val KEY_ADAPTIVE_DISTANCE_M = "adaptive_distance_m"
        private const val KEY_ADAPTIVE_IDLE_S = "adaptive_idle_s"
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
    }
}
