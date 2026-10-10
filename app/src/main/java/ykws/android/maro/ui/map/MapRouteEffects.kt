package ykws.android.maro.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.MutableStateFlow
import ykws.android.maro.data.route.RouteEndSelection
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.spatial.RouteEngine
import ykws.android.maro.spatial.RouteEngineChoice
import ykws.android.maro.spatial.multipass.MultipassWorld

/**
 * The route family's effects (code-health step 3, tier 2d). Each host is called from `MapScreen` at the
 * position its blocks held, so no effect changed its order among the screen's other effects.
 *
 * The auto-pick effect stays in the screen: it writes the screen's own `routeAutoPick` flag and calls its
 * `followRoute`, which a child composable cannot do.
 */

/** Rebuilds the live engine whenever the chosen id moves (D5); the keys and body are the screen's own. */
@Composable
internal fun MapRouteEngineEffect(
    appSettings: AppSettings,
    routeEngineSelection: MutableStateFlow<RouteEngine>,
    avoidWorldProvider: () -> MultipassWorld,
    settingsProvider: () -> AppSettings
) {
    LaunchedEffect(appSettings.routeEngineId) {
        routeEngineSelection.value =
            RouteEngineChoice.resolve(appSettings.routeEngineId)
                .factory(
                    { settingsProvider().routeFreeWaterPaceKn.toDouble() },
                    { settingsProvider().routeSlowWaterAversion.toDouble() },
                    { settingsProvider().routeSlowWaterBudgetPct },
                    avoidWorldProvider,
                    { settingsProvider().routeMarkerStrength.toDouble() }
                )
    }
}

/**
 * The two end stores (R66): a stored value that no longer resolves is replaced by the resolution, so the
 * dead id leaves the store rather than being re-read on the next frame. `onStoreEnd` is the screen's own
 * `storeRouteEnd`, which the route ends panel calls too.
 */
@Composable
internal fun MapRouteEndEffects(
    routeStartSelection: RouteEndSelection,
    routeStartStored: String,
    routeDestinationSelection: RouteEndSelection,
    routeDestinationStored: String,
    gpsMode: Boolean,
    markersLoaded: Boolean,
    onStoreEnd: (RouteEndSelection.End, RouteEndSelection) -> Unit
) {
    LaunchedEffect(routeStartSelection, routeStartStored, gpsMode, markersLoaded) {
        // Never purge a stored marker id before the first load: an empty marker set at startup is the
        // "not yet loaded" case, not a deleted flag, and overwriting it here is what erased the ends.
        if (!markersLoaded) return@LaunchedEffect
        if (RouteEndSelection.encode(routeStartSelection) != routeStartStored) {
            onStoreEnd(RouteEndSelection.End.START, routeStartSelection)
        }
    }
    LaunchedEffect(routeDestinationSelection, routeDestinationStored, gpsMode, markersLoaded) {
        if (!markersLoaded) return@LaunchedEffect
        if (RouteEndSelection.encode(routeDestinationSelection) != routeDestinationStored) {
            onStoreEnd(RouteEndSelection.End.DESTINATION, routeDestinationSelection)
        }
    }
}
