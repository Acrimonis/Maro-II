package ykws.android.maro.ui.map

import android.content.Context
import android.os.PowerManager
import android.os.Process
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
import ykws.android.maro.spatial.multipass.WarmArea
import ykws.android.maro.spatial.multipass.boxInsideRegion
import ykws.android.maro.spatial.multipass.prewarmGate
import ykws.android.maro.spatial.multipass.warmOutward
import java.util.concurrent.Executors

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
                    { settingsProvider().verdictWeight },
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
 * **The warm-up's own worker** — a dispatcher of its own, its threads at a **background CPU priority**,
 * with the pool bounded by `route.prewarm.threads` (D5).
 *
 * Created once, lazily, here at the app edge: `spatial/multipass` stays free of Android types, so the one
 * place a thread priority may be set is the host that does the wiring. The priority is set on the worker
 * itself, before the task runs, so an executor thread that is reused keeps it.
 *
 * The pool is deliberately **small** — the pass rasterises one tile at a time, so this is a ceiling on the
 * background load rather than a fan-out — and it is never closed: it lives as long as the process, exactly
 * as the app's own dispatchers do.
 */
private val warmDispatcher: CoroutineDispatcher by lazy {
    Executors.newFixedThreadPool(AppConfig.routePrewarmThreads) { task ->
        object : Thread(task, WARM_THREAD_NAME) {
            override fun run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                super.run()
            }
        }
    }.asCoroutineDispatcher()
}

/**
 * **The warm-up's own host** — the pass that builds the fine layer's tiles over `route.prewarm.box`, then
 * outward from it in rings of whole tiles, before an arm asks for them.
 *
 * **It starts with the app** (D4). The effect is keyed on [worldReady], the moment both baked layers can
 * answer, rather than on the boat being idle: warming used to be gated on the boat standing still, so on a
 * boat that never idles nothing was ever warmed. It stays off **battery-saver**, and it **pauses** — never
 * stops — while a route is **armed** or while another heavy foreground job holds the CPU ([heavyJob], a
 * depth-raster regeneration), resuming to fill the holes afterwards. **All three terms are read per tile**,
 * inside the running walk: a job or an arm that starts mid-pass pauses it rather than being raced, no tile
 * is rebuilt on the way back, and battery-saver switched on mid-walk **ends** the pass there rather than
 * letting it run to its cap. The gate is asked at launch too, so nothing starts on saver.
 *
 * **It never touches the screen's thread** (D5). The readiness gate is all that runs on the composition's
 * thread; the walk, the derivations and every build run on [warmDispatcher], and nothing inside
 * `spatial/multipass` adds a hop of its own. The tiles are derived by the row's own plan ([plan]) through
 * [`RouteGridBuilder.fineTilesOver`], so they are keyed and built exactly as the arm keys and builds them,
 * and the pass fills them store-first, so a warm cache costs a listing and shows no dot. The routing
 * square's indicator stands for warm-up work alone: the window is lit while the walk is really building a
 * tile ([`FineTilePrewarm.run`], consecutive tiles merged by the grace) and dark while it waits at its
 * gate — an armed route, a competing job or battery-saver — or where a ring would build nothing. So a
 * startup or post-route warm-up lights it, a fully warm cache leaves it dark, and a route computing over
 * the store never lights it at all.
 *
 * `spatial/multipass` keeps the logic; this host owns only the Android signals (readiness, arming, battery,
 * a competing job, the log), the worker and the world the map already holds.
 */
@Composable
internal fun MapRoutePrewarmEffect(
    plan: RouteGridPlan,
    worldReady: Boolean,
    routeArmed: Boolean,
    heavyJob: Boolean,
    position: LatLng?,
    avoidWorldProvider: () -> MultipassWorld,
    markerStrength: () -> Double
) {
    val context = LocalContext.current
    // The running pass must see the *current* signals, not the ones captured when the effect launched.
    val armed by rememberUpdatedState(routeArmed)
    val busy by rememberUpdatedState(heavyJob)
    val where by rememberUpdatedState(position)
    LaunchedEffect(worldReady) {
        // The baked layers land after the composition, so the pass starts the moment both can answer
        // rather than at the first frame — and from there it owns its own lifecycle.
        if (!worldReady) return@LaunchedEffect
        val box = AppConfig.routePrewarmBox ?: return@LaunchedEffect
        val map = FineTileCache.shared ?: return@LaunchedEffect
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        withContext(warmDispatcher) {
            if (!prewarmGate(
                    routeArmed = armed,
                    batterySaver = power?.isPowerSaveMode == true,
                    heavyJob = busy
                )
            ) {
                return@withContext
            }
            val world = avoidWorldProvider()
            if (!world.coastlineReady || !world.depthReady) return@withContext
            // A fine tile can exist only where the baked depth and coast do: report a box that falls outside
            // the region rather than shrinking it silently, and still walk what the region covers.
            val region = world.regionBounds
            if (!boxInsideRegion(box, region)) {
                Log.i(
                    PREWARM_TAG,
                    "PREWARM box outside region box=[${box.latSouth},${box.lonWest}..${box.latNorth},${box.lonEast}] " +
                        "region=${region?.let { "[${it.latSouth},${it.lonWest}..${it.latNorth},${it.lonEast}]" } ?: "none"} " +
                        "— walking what the region covers, not shrinking the box"
                )
            }
            val builder = RouteGridBuilder(plan)
            // Growth is expressed in the plan's own tiles, and the tile's side is the builder's own answer
            // ([`RouteGridBuilder.fineTileSideOver`]) rather than arithmetic re-derived here.
            val tileSide = builder.fineTileSideOver(world, box) ?: return@withContext
            val area = WarmArea(
                seed = box,
                region = region,
                growthStepTiles = AppConfig.routePrewarmGrowthStepTiles,
                tileSideLatDeg = tileSide.latDeg,
                tileSideLonDeg = tileSide.lonDeg
            )
            val orderFrom = where
                ?: LatLng((box.latSouth + box.latNorth) / 2.0, (box.lonWest + box.lonEast) / 2.0)
            warmOutward(
                pass = FineTilePrewarm(map, trace = { line -> Log.i(PREWARM_TAG, line) }),
                area = area,
                derive = { over -> builder.fineTilesOver(world, over, markerStrength()) },
                storedNames = map.storedNames(),
                orderFrom = orderFrom,
                maxTiles = AppConfig.routePrewarmMaxTiles,
                maxTimeMs = AppConfig.routePrewarmMaxMs,
                // The share of the store the walk may fill, so the rest stays a reserve for water outside
                // the seed area (the plan's §11): the walk stops on the store's own room under it.
                targetPct = AppConfig.routePrewarmTargetPct,
                storeFullness = { FineTileCache.storeFullness() },
                // Every gate is read **per tile, here**, never sampled once at launch: the seam waits out an
                // armed route and a competing job — the pass resumes to fill the holes — and answers `false`
                // on battery-saver, which stands the pass down instead of letting it run to its cap on a
                // saver the user switched on mid-walk. The saver is a **stop, never a wait**: the pass must
                // not run at all on it, and waiting would hold the dot and the walk slot for as long as the
                // user kept it on.
                awaitMayProceed = {
                    if (power?.isPowerSaveMode == true) {
                        false
                    } else {
                        snapshotFlow { armed to busy }.first { !it.first && !it.second }
                        // Asked again after the wait: the saver may have arrived while a route was armed.
                        power?.isPowerSaveMode != true
                    }
                }
            )
        }
    }
}

/** The warm-up worker's thread name — the device log reads the pass apart from the app's own work. */
private const val WARM_THREAD_NAME = "fine-tile-warm"

/** The tag the pre-warm's own lines print on — the route trace the fine layer already writes to. */
private const val PREWARM_TAG = "MaroRoute"
