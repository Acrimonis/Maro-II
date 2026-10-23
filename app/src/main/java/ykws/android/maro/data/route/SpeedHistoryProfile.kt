package ykws.android.maro.data.route

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.settings.AppSettings

/**
 * The speed-history profile the settings describe: the last N stored trips (N, and whether demo trips count,
 * being settings of their own), combined by the capped open-water-weighted median — or null for an empty
 * history. Derived on every read, never stored, so moving N or the demo switch moves it at once.
 */
fun AppSettings.speedHistoryProfileKn(): Double? = SpeedHistory.profile(
    store = routeSpeedHistoryTrips,
    n = routeSpeedHistoryTrackCount,
    includeDemo = routeSpeedHistoryIncludeDemo,
    capMin = AppConfig.routePaceTripWeightCapMin.toDouble()
)
