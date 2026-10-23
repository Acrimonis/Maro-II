package ykws.android.maro.data.settings

import android.content.Context

/**
 * The selected-item speed toggle, persisted per kind (2026-10-23): two private booleans that are NOT
 * part of [AppSettings] — no settings row reads them and no `maro.properties` key seeds them — and are
 * off by default. On bands the selected item's fill; off paints its gold highlight. Non-selected items
 * never read these: they keep following their kind's colours axis.
 */
class SelectionEyeStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The recorded kind's persisted toggle (off by default). */
    fun trackEye(): Boolean = prefs.getBoolean(KEY_TRACK_EYE, false)

    /** The route kind's persisted toggle (off by default). */
    fun routeEye(): Boolean = prefs.getBoolean(KEY_ROUTE_EYE, false)

    fun setTrackEye(value: Boolean) {
        prefs.edit().putBoolean(KEY_TRACK_EYE, value).apply()
    }

    fun setRouteEye(value: Boolean) {
        prefs.edit().putBoolean(KEY_ROUTE_EYE, value).apply()
    }

    private companion object {
        const val PREFS_NAME = "maro_selection_eye"
        const val KEY_TRACK_EYE = "track_eye"
        const val KEY_ROUTE_EYE = "route_eye"
    }
}
