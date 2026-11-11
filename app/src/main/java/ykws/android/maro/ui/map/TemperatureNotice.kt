package ykws.android.maro.ui.map

import ykws.android.maro.data.temperature.TemperatureProblem

/** What the temperature tile tells the user by toast. */
internal sealed interface TemperatureNotice {
    /** A refresh has begun (long press on the tile, or the settings control). */
    data object Refreshing : TemperatureNotice

    /** A request went wrong, or a refresh was asked with no connection. */
    data class Problem(val problem: TemperatureProblem) : TemperatureNotice
}
