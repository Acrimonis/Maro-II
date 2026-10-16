package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.sync.withLock
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox

/**
 * **Why an outward warm-up walk ended** — the fact a device pass reads instead of guessing from a count.
 *
 * [TARGET] and [REGION] are the two stops D3 settled, the first as §11 of the plan sharpened it: the store
 * has room for no further entry **under the share of its ceiling the walk is held to** — so the rest stands
 * as a reserve for water the seed area does not cover — or the area has grown to the edges of the baked
 * region and no tile can exist beyond it. [NOTHING_TO_DO] means the seed's own water carries no fine layer
 * at all (a single-grid plan, or a plan whose mask keeps nothing there), so growing outward could only
 * speculate. [CAPS] is the pass's own tile or time budget, and [GATE] is the app's own gate closing under
 * the walk — battery-saver arriving mid-pass, so the walk stood down.
 */
internal enum class WarmStop { TARGET, REGION, NOTHING_TO_DO, CAPS, GATE }

/** What one outward walk found and did — the reading a device pass or a test asserts against. */
internal data class WarmOutcome(
    /** How many tiles the walk built, summed over its areas. */
    val built: Int,
    /** How many requests were already warm — in memory or on disk — and skipped before any build. */
    val warm: Int,
    /** How many growth steps were walked, the seed not counted. */
    val rings: Int,
    /** Why the walk ended. */
    val stop: WarmStop
)

/**
 * **The warm area — the defined box as the seed, then outward in whole tiles of the lattice.**
 *
 * D3's shape, and the reason the cache can extend itself without a rolling area: the seed
 * (`route.prewarm.box`) decides where warming starts, and the area grows outward from it — a ring at a time,
 * [growthStepTiles] tiles wide on every edge — while the store has room. So the ceiling is spent on the
 * water around the defined area rather than on a fixed square, and the pass stops on the store itself.
 *
 * **It carries no radius.** D3 settled that the two bounds are the store's ceiling and the baked region:
 * [grown] answers `null` the moment the region's own edges leave nothing to add, and the walk's own tile
 * budget is the ring bound where a world names no region — so no radius knob exists to be tuned, and
 * [growthStepTiles] alone says how fast the area opens out.
 *
 * The tile side is handed in as degrees rather than derived here: it is the plan's own fine lattice
 * arithmetic ([`RouteGridBuilder.fineTileSideOver`], which hands back the side [`RouteGridBuilder`]'s own
 * tile block is laid out on), and a policy inventing its own could grow the area by a step the plan's tiles
 * do not share.
 */
internal class WarmArea(
    seed: BBox,
    private val region: BBox?,
    /** How many tiles wide one growth step is — `route.prewarm.growthStepTiles`. */
    growthStepTiles: Int,
    /** One tile's side in degrees of latitude — `FINE_TILE_CELLS × the fine lattice's own cell`. */
    tileSideLatDeg: Double,
    /** One tile's side in degrees of longitude, from the same lattice. */
    tileSideLonDeg: Double
) {

    /** The seed the walk starts from, clamped to the baked region where one is loaded. */
    val seedBox: BBox = clampToRegion(seed)

    /** One growth step's reach, in degrees — a step of at least one tile, whatever the load accepted. */
    private val stepLat = growthStepTiles.coerceAtLeast(1) * tileSideLatDeg

    /** One growth step's east–west reach, in degrees. */
    private val stepLon = growthStepTiles.coerceAtLeast(1) * tileSideLonDeg

    /**
     * **The area one ring outward, or `null` where there is nothing left to add.** A step widens every edge
     * by [stepLat] / [stepLon] and the result is clamped to the region, so a box that started inside it
     * stays inside. The stop is read against the **region's own edges** rather than against the box's: a
     * clamp lands on the region's line to within a double's last bit, and comparing the clamped box to the
     * one it came from would then read "it moved" forever.
     */
    fun grown(box: BBox): BBox? {
        val bounds = region
        if (bounds != null && reachesEdge(box, bounds)) return null
        return clampToRegion(
            BBox(
                box.latSouth - stepLat, box.latNorth + stepLat,
                box.lonWest - stepLon, box.lonEast + stepLon
            )
        )
    }

    /** Whether [box] already reaches every edge of [bounds] — the area is as far out as tiles can exist. */
    private fun reachesEdge(box: BBox, bounds: BBox): Boolean =
        box.latSouth <= bounds.latSouth + WARM_EDGE_EPS && box.latNorth >= bounds.latNorth - WARM_EDGE_EPS &&
            box.lonWest <= bounds.lonWest + WARM_EDGE_EPS && box.lonEast >= bounds.lonEast - WARM_EDGE_EPS

    /**
     * **The box trimmed to the region's own edges.** A seed that reaches past them is trimmed rather than
     * walked whole — the overflow is still *reported* by the app edge, and warming follows the water tiles
     * can exist on. Where the two do not overlap at all the seed is kept as it stands, so a mis-set box
     * behaves exactly as it did before the clamp existed rather than warming nothing silently.
     */
    private fun clampToRegion(box: BBox): BBox {
        val bounds = region ?: return box
        val south = maxOf(box.latSouth, bounds.latSouth)
        val north = minOf(box.latNorth, bounds.latNorth)
        val west = maxOf(box.lonWest, bounds.lonWest)
        val east = minOf(box.lonEast, bounds.lonEast)
        if (south >= north || west >= east) return box
        return BBox(south, north, west, east)
    }
}

/**
 * The hair by which a clamped edge may miss the region's own line, in degrees — a hundredth of a
 * millimetre. A step is added to an edge in floating point, so the clamped result can land a bit past or
 * short of the bound; this is what lets [`WarmArea.grown`] recognise the edge as reached.
 */
private const val WARM_EDGE_EPS = 1e-9

/**
 * **Walk the warm area outward** — the seed first, then one ring at a time while the store has room.
 *
 * It is the pass's own driver, and pure Kotlin like the pass: [derive] is the builder's own request
 * derivation (`RouteGridBuilder.fineTilesOver`), so every tile the walk asks for is keyed and built exactly
 * as the arm over that water would key and build it. Growth is not linear in area — a ring yields tiles only
 * where the active plan keeps fine water — which is why nothing is counted off the ring itself: each ring is
 * handed to the pass, and the walk counts what the derivation and the pass actually returned.
 *
 * **The window is the work, and it opens only where something is really built.** The walk works out what
 * the ring will build ([`FineTilePrewarm.pending`]) before the pass opens anything, so a ring of
 * already-warm water (the fully warm seed a boat restarting on prepared water has) builds nothing and the
 * dot correctly stays dark. The dot itself is fed tile by tile by the pass ([`FineTilePrewarm.run`]), and
 * the grace merges those tiles, so it is lit while the walk really builds and dark the moment the walk
 * settles at its gate.
 *
 * **One walk at a time.** The walk takes the holder's one walk slot ([`FineTileCache.walkSlot`]), because a
 * recreated Activity restarts the host's effect while the walk it replaced is only **cooperatively**
 * cancelled — still building the tile it had started. A second walk waits for the slot rather than running
 * beside the first, so no tile is built twice and the window is never held twice.
 *
 * The walk ends on the store's own reading that it has no room left under [targetPct] of its ceiling (the
 * app edge's `fullness` probe), on the region's own edges, on the app's own gate closing (battery-saver), on
 * the pass's tile or time budget, or at once where the seed's water carries no fine layer. The tile budget
 * is also the ring bound: a world that names no region can grow forever, and a walk that built nothing must
 * still stop.
 */
internal suspend fun warmOutward(
    pass: FineTilePrewarm,
    area: WarmArea,
    derive: (BBox) -> List<FineTileRequest>,
    storedNames: Set<String>,
    orderFrom: LatLng,
    maxTiles: Int,
    maxTimeMs: Long,
    /**
     * **The share of the store's ceiling the walk may fill** — `route.prewarm.targetPct`. Growth stops once
     * one more tile of the size the store last wrote would not fit under it, so the rest of the ceiling
     * stands as a reserve for water the seed area does not cover. Required rather than defaulted: a caller
     * that forgot it would warm to the ceiling and spend that reserve silently.
     */
    targetPct: Int,
    /** The store's own ceiling probe — `FineTileCache.storeFullness` in production. */
    storeFullness: suspend () -> StoreFullness? = { null },
    /**
     * Whether the walk may go on, or must stand down. It is asked **per ring** as well as per tile inside
     * the pass, so the app's own gate binds for the whole walk rather than for the launch that opened it.
     */
    awaitMayProceed: suspend () -> Boolean = { true },
    nowMs: () -> Long = { System.currentTimeMillis() },
    trace: (String) -> Unit = {}
): WarmOutcome = FineTileCache.walkSlot.withLock {
    val startMs = nowMs()
    // Every tile the walk has asked for, so a tile built in one ring is not counted warm — or asked for
    // again — when the next ring's larger box covers it too.
    val seen = HashSet<TileKey>()
    var box = area.seedBox
    var built = 0
    var warm = 0
    var rings = 0
    var stop = WarmStop.CAPS
    while (true) {
        if (built >= maxTiles || nowMs() - startMs >= maxTimeMs) {
            stop = WarmStop.CAPS
            break
        }
        // The ring bound. Each growth step widens the area by at least one tile, so a walk may not take
        // more steps than the tile budget it was given: without a region to clamp it would otherwise
        // spin on a plan that keeps no water outward.
        if (rings > maxTiles) {
            stop = WarmStop.CAPS
            break
        }
        // The app's own gate, asked per ring: the walk stands down rather than deriving another ring's
        // water while the app is busy (an armed route, a competing job) or on battery-saver.
        if (!awaitMayProceed()) {
            stop = WarmStop.GATE
            break
        }
        // The store's own room under the growth target, asked before the next area is even derived: a store
        // with nothing left to give under it ends the walk rather than paying for the derivation of a ring it
        // could not fill, and the share left below the ceiling is the reserve the walk keeps for water the
        // seed area does not cover.
        val fullness = storeFullness()
        if (fullness != null && !fullness.hasRoomAt(targetPct)) {
            stop = WarmStop.TARGET
            break
        }
        val requests = derive(box)
        if (requests.isEmpty()) {
            // The seed's own water carries no fine layer (a single-grid plan, or a mask that keeps
            // nothing here), so there is nothing to warm and no reason to speculate outward.
            stop = WarmStop.NOTHING_TO_DO
            break
        }
        val fresh = requests.filter { seen.add(it.key) }
        // **What this ring will actually build**, worked out before anything opens: a tile the map
        // already holds or the store already names is never built, so a ring of them holds no window at
        // all. Holding on `fresh` alone was the defect — it ignored `storedNames`, so a fully warm seed
        // lit the dot for a walk that could never build anything, against the pass's own contract.
        val toBuild = if (fresh.isEmpty()) emptyList() else pass.pending(fresh, storedNames)
        if (toBuild.isEmpty()) {
            // Nothing here to build: every tile was already asked for by a wider earlier ring, or all of
            // them are warm in memory or on disk. So the walk opens no window, credits what the ring
            // found warm, and opens the area one step further.
            warm += fresh.size
            val wider = area.grown(box)
            if (wider == null) {
                stop = WarmStop.REGION
                break
            }
            box = wider
            rings++
            continue
        }
        trace("PREWARM ring=$rings tiles=${fresh.size} box=[${box.latSouth},${box.lonWest}..${box.latNorth},${box.lonEast}]")
        // The pass opens and closes the window per tile it really builds; the walk holds nothing of its
        // own, so a ring's gate wait leaves the dot to the grace.
        val outcome = pass.run(
            requests = fresh,
            storedNames = storedNames,
            orderFrom = orderFrom,
            maxTiles = maxTiles - built,
            maxTimeMs = maxTimeMs - (nowMs() - startMs),
            targetPct = targetPct,
            awaitMayProceed = awaitMayProceed,
            storeFullness = storeFullness,
            nowMs = nowMs
        )
        built += outcome.built
        warm += outcome.warm
        if (outcome.stoppedByGate) {
            stop = WarmStop.GATE
            break
        }
        if (outcome.stoppedByCapacity) {
            stop = WarmStop.TARGET
            break
        }
        if (outcome.stoppedByCaps) {
            stop = WarmStop.CAPS
            break
        }
        val wider = area.grown(box)
        if (wider == null) {
            stop = WarmStop.REGION
            break
        }
        box = wider
        rings++
    }
    trace("PREWARM walk done rings=$rings built=$built warm=$warm stop=$stop")
    WarmOutcome(built = built, warm = warm, rings = rings, stop = stop)
}
