package ykws.android.maro.ui.map

import ykws.android.maro.data.temperature.TemperatureState

/** The two faces of the temperature tile. */
internal enum class TemperatureView { AIR, WATER }

/**
 * The view the tile shows: the user's pick when there is a water view to pick, else water on water and air
 * on land. A pick of water is kept while the marker is on land (only air can show there) and applies again
 * once it is back on water.
 */
internal fun effectiveTemperatureView(state: TemperatureState, preferred: TemperatureView?): TemperatureView = when {
    state.water == null -> TemperatureView.AIR
    preferred != null -> preferred
    else -> TemperatureView.WATER
}

/** The pick a tap makes: the other view, or null when the marker is on land and there is nothing to switch to. */
internal fun tappedTemperatureView(state: TemperatureState, preferred: TemperatureView?): TemperatureView? {
    if (state.water == null) return null
    return if (effectiveTemperatureView(state, preferred) == TemperatureView.WATER) TemperatureView.AIR
    else TemperatureView.WATER
}
