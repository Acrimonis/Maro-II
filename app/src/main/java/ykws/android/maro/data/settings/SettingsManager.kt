package ykws.android.maro.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 */
data class AppSettings(
    val defaultLatitude: Double = 43.55,
    val defaultLongitude: Double = 7.00,
    val coastlineVisible: Boolean = true,
    val zone300Visible: Boolean = true,
    val gpsMode: Boolean = false,
    val recenterDelaySeconds: Int = 5,
    val mapCenterLat: Double = Double.NaN,
    val mapCenterLon: Double = Double.NaN,
    val zoomLevel: Double = 0.0,
    val isWater: Boolean = true,
    val distanceToShore: Double = Double.NaN
)

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings = AppSettings(
        defaultLatitude  = prefs.getFloat(KEY_DEFAULT_LAT, 43.55f).toDouble(),
        defaultLongitude = prefs.getFloat(KEY_DEFAULT_LON, 7.00f).toDouble(),
        coastlineVisible = prefs.getBoolean(KEY_COASTLINE_VISIBLE, true),
        zone300Visible   = prefs.getBoolean(KEY_ZONE300_VISIBLE, true),
        gpsMode          = prefs.getBoolean(KEY_GPS_MODE, false),
        recenterDelaySeconds = prefs.getInt(KEY_RECENTER_DELAY_S, 5),
        mapCenterLat     = prefs.getFloat(KEY_MAP_CENTER_LAT, Float.NaN).toDouble(),
        mapCenterLon     = prefs.getFloat(KEY_MAP_CENTER_LON, Float.NaN).toDouble(),
        zoomLevel        = prefs.getFloat(KEY_ZOOM_LEVEL, 0f).toDouble(),
        isWater          = prefs.getBoolean(KEY_IS_WATER, true),
        distanceToShore  = prefs.getFloat(KEY_DISTANCE_TO_SHORE, Float.NaN).toDouble()
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
            .putBoolean(KEY_GPS_MODE, updated.gpsMode)
            .putInt(KEY_RECENTER_DELAY_S, updated.recenterDelaySeconds)
            .putFloat(KEY_MAP_CENTER_LAT, updated.mapCenterLat.toFloat())
            .putFloat(KEY_MAP_CENTER_LON, updated.mapCenterLon.toFloat())
            .putFloat(KEY_ZOOM_LEVEL, updated.zoomLevel.toFloat())
            .putBoolean(KEY_IS_WATER, updated.isWater)
            .putFloat(KEY_DISTANCE_TO_SHORE, updated.distanceToShore.toFloat())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "maro_settings"
        private const val KEY_DEFAULT_LAT = "default_lat"
        private const val KEY_DEFAULT_LON = "default_lon"
        private const val KEY_COASTLINE_VISIBLE = "coastline_visible"
        private const val KEY_ZONE300_VISIBLE = "zone300_visible"
        private const val KEY_GPS_MODE = "gps_mode"
        private const val KEY_RECENTER_DELAY_S = "recenter_delay_s"
        private const val KEY_MAP_CENTER_LAT = "map_center_lat"
        private const val KEY_MAP_CENTER_LON = "map_center_lon"
        private const val KEY_ZOOM_LEVEL = "zoom_level"
        private const val KEY_IS_WATER = "is_water"
        private const val KEY_DISTANCE_TO_SHORE = "distance_to_shore"
    }
}
