package ykws.android.maro.ui.map

import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.route.RouteEndSelection
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.spatial.RouteEngine
import ykws.android.maro.spatial.RouteEngineChoice
import ykws.android.maro.spatial.multipass.FineTileCache
import ykws.android.maro.spatial.multipass.FineTilePrewarm
import ykws.android.maro.spatial.multipass.MultipassWorld
import ykws.android.maro.spatial.multipass.RouteGridBuilder
import ykws.android.maro.spatial.multipass.RouteGridPlan
import ykws.android.maro.spatial.multipass.boxInsideRegion
import ykws.android.maro.spatial.multipass.prewarmGate

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

/**
 * **The pre-warm's own host** — the startup pass that builds the fine layer's tiles over
 * `route.prewarm.box` before an arm asks for them.
 *
 * It is the app edge the user settled: it runs only when the boat is **idle** ([boatIdle], the adaptive
 * policy's IDLE state) and off **battery-saver**, and it **pauses** — never stops — while a route is
 * **armed**, resuming to fill the holes once the arm ends. The effect is keyed on [boatIdle] alone, so
 * becoming idle starts it and moving away cancels it; the arming is handled inside the running pass by
 * [awaitMayProceed], so a live arm is never starved and no tile is rebuilt on the way back.
 *
 * The tiles are derived by the row's own plan ([plan]) through [`RouteGridBuilder.fineTilesOver`], so they
 * are keyed and built exactly as the arm keys and builds them; the pass then fills them store-first, so a
 * warm cache costs a listing and shows no dot. `spatial/multipass` keeps the logic; this host owns only the
 * Android signals (idle, arming, battery, the log) and the world the map already holds.
 */
@Composable
internal fun MapRoutePrewarmEffect(
    plan: RouteGridPlan,
    boatIdle: Boolean,
    routeArmed: Boolean,
    position: LatLng?,
    avoidWorldProvider: () -> MultipassWorld,
    markerStrength: () -> Double
) {
    val context = LocalContext.current
    // The running pass must see the *current* arming, not the one captured when the effect launched.
    val armed by rememberUpdatedState(routeArmed)
    LaunchedEffect(boatIdle) {
        // Idle only: a moving boat (or no fix yet) leaves the box alone.
        if (!boatIdle) return@LaunchedEffect
        val box = AppConfig.routePrewarmBox ?: return@LaunchedEffect
        val map = FineTileCache.shared ?: return@LaunchedEffect
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (!prewarmGate(idle = true, routeArmed = routeArmed, batterySaver = power?.isPowerSaveMode == true)) {
            return@LaunchedEffect
        }
        val world = avoidWorldProvider()
        if (!world.coastlineReady || !world.depthReady) return@LaunchedEffect
        // A fine tile can exist only where the baked depth and coast do: report a box that falls outside the
        // region rather than shrinking it silently, and still walk what the region covers.
        val region = world.regionBounds
        if (!boxInsideRegion(box, region)) {
            Log.i(
                PREWARM_TAG,
                "PREWARM box outside region box=[${box.latSouth},${box.lonWest}..${box.latNorth},${box.lonEast}] " +
                    "region=${region?.let { "[${it.latSouth},${it.lonWest}..${it.latNorth},${it.lonEast}]" } ?: "none"} " +
                    "— walking what the region covers, not shrinking the box"
            )
        }
        val requests = RouteGridBuilder(plan).fineTilesOver(world, box, markerStrength())
        if (requests.isEmpty()) return@LaunchedEffect
        val orderFrom = position
            ?: LatLng((box.latSouth + box.latNorth) / 2.0, (box.lonWest + box.lonEast) / 2.0)
        FineTilePrewarm(map, trace = { line -> Log.i(PREWARM_TAG, line) }).run(
            requests = requests,
            storedNames = map.storedNames(),
            orderFrom = orderFrom,
            maxTiles = AppConfig.routePrewarmMaxTiles,
            maxTimeMs = AppConfig.routePrewarmMaxMs,
            awaitMayProceed = { snapshotFlow { armed }.first { !it } }
        )
    }
}

/** The tag the pre-warm's own lines print on — the route trace the fine layer already writes to. */
private const val PREWARM_TAG = "MaroRoute"
