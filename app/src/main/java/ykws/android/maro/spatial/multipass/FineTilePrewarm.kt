package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * **One fine tile a pre-warm may build** — its canonical [TileKey], the [center] of its block, and the
 * [build] closure that produces it. It is the builder's own answer ([`RouteGridBuilder.fineTilesOver`]), so
 * the key is the one the arm derives and the closure is the arm's own rasterise: a pre-warm never invents a
 * second derivation. [center] lets the pass walk the box **own-ship outward**.
 *
 * The closure is invoked only on a **miss** — the map asks its store first — so a request whose tile is
 * already warm is skipped without building anything.
 */
internal class FineTileRequest(
    val key: TileKey,
    val center: LatLng,
    val build: suspend () -> FineTile
)

/** What one pre-warm pass found and did — the reading a device pass or a test asserts against. */
internal data class PrewarmOutcome(
    /** How many tiles the pass built (a store- or memory-warm tile is not built, so it is not counted). */
    val built: Int,
    /** How many requests were already warm — in memory or on disk — and skipped before any build. */
    val warm: Int,
    /** Whether a cap (the tile budget or the time budget) ended the pass before every request was met. */
    val stoppedByCaps: Boolean,
    /** Whether the store's own ceiling ended the pass — the disk is full, so further tiles evict peers. */
    val stoppedByCapacity: Boolean = false,
    /** Whether the app's own gate ended the pass — battery-saver, so the pass stood down rather than ran. */
    val stoppedByGate: Boolean = false
)

/**
 * **The pre-warm: a pass that builds the fine layer's tiles over an area before an arm asks for them, so a
 * later arm over that water reuses them.**
 *
 * It reaches the fine-tile map the arm reaches, one tile at a time, and the map is the same one the store
 * is installed into (`FineTileCache.shared`), so the pass is **store-first** for free: a tile already on
 * disk is a hit and costs neither a build nor a byte. Before it opens its busy window it asks the map and
 * the store's own names, so a **fully warm cache builds nothing and shows no dot** — the correct outcome a
 * boat restarting on warm water sees, not a fault. That filter is [pending], and it is asked twice: here,
 * before this pass opens its window, and by the walk ([`warmOutward`]) before it opens its own, so no
 * caller can light the dot for water that will never be built.
 *
 * **The window is the tile the pass is really building.** Each tile it builds is bracketed by the holder's
 * window ([`FineTileCache.beginWarmTile`] / [`endWarmTile`]), and the 400 ms grace merges the tiles that
 * follow one another — within a ring and across the rings of one walk — so the dot stands for the walk's
 * real work rather than flickering tile to tile. The moment the walk stops building to wait at its gate,
 * no further mark arrives and the grace lets the dot fall: the indicator says **working**, never **waiting**.
 *
 * **It is pure Kotlin** — no Android type reaches it. This class owns only the arithmetic of one area:
 * skip-what-is-warm, order own-ship outward, honour the two caps and the store's ceiling, and ask
 * [awaitMayProceed] before every tile — yielding where the app is busy and resuming afterwards to fill the
 * holes, standing down where the answer is `false` (battery-saver), so the gate binds for the whole pass.
 * What decides *when* a pass may start, and *how far* the area reaches, is the app edge and [`warmOutward`].
 */
internal class FineTilePrewarm(
    /** The fine-tile map to fill — in production the installed `FineTileCache.shared`. */
    private val map: FineTileMap,
    /** Where the pass's own lines go — the route tag on device, inert off it. */
    private val trace: (String) -> Unit = {}
) {

    /**
     * **The requests a pass would actually build** — those neither held by the map nor named by the store.
     * [run] filters with this call, and [`warmOutward`] asks it before it opens its busy window, so a ring
     * whose water is warm opens nothing and the dot stays dark: the answer the walk holds its window on and
     * the answer the pass builds on are one filter rather than two that could drift apart.
     */
    suspend fun pending(requests: List<FineTileRequest>, storedNames: Set<String>): List<FineTileRequest> {
        val missing = ArrayList<FineTileRequest>(requests.size)
        for (request in requests) {
            if (map.holds(request.key)) continue
            if (FineTileCodec.entryName(request.key) in storedNames) continue
            missing.add(request)
        }
        return missing
    }

    /**
     * **Run one pass over [requests].** The tiles already warm are skipped first (no window opens when none
     * is left); the rest are built nearest-[orderFrom]-first, each bounded by [maxTiles] and [maxTimeMs].
     * [storeFullness] is asked before every tile too: once the store answers that it has no room for another
     * entry **under [targetPct] of its ceiling** the pass stops, because every further tile it builds costs a
     * tile it already had — and the share left below the ceiling is the reserve the walk keeps for water the
     * seed area does not cover.
     *
     * [awaitMayProceed] is asked **before every tile**, and the window is opened only while a tile is
     * really being built — so a pass told to stand down before its first tile shows no dot at all, and a
     * pass waiting at its gate shows none either. The seam yields while the app is busy (a route armed, a
     * raster regenerating) and resumes to fill what it left; an answer of `false` is the app's own gate
     * closed (battery-saver), which **ends** the pass rather than letting it run to its cap.
     *
     * [nowMs] is injected so a test drives the time budget without a clock; [awaitMayProceed] defaults to
     * `true` so a test never pauses.
     */
    suspend fun run(
        requests: List<FineTileRequest>,
        /** The entry names the store already holds — `FineTileMap.storedNames()`. */
        storedNames: Set<String>,
        /** The boat's last known position, or the box's centre as its fallback — the outward ordering's origin. */
        orderFrom: LatLng,
        /** The tile budget: the most tiles one pass may build (`route.prewarm.maxTiles`). */
        maxTiles: Int,
        /** The time budget (ms): the longest one pass may run (`route.prewarm.maxMs`). */
        maxTimeMs: Long,
        /**
         * The share of the store's ceiling this pass may fill — `route.prewarm.targetPct`, the same value
         * the outward walk is held to. Required rather than defaulted, so a caller cannot warm past the
         * reserve by forgetting it.
         */
        targetPct: Int,
        /** Whether the pass may go on, or must stand down — asked before every tile. See the KDoc above. */
        awaitMayProceed: suspend () -> Boolean = { true },
        /** The store's own fullness, or `null` where it cannot say — probed before every tile (step 3). */
        storeFullness: suspend () -> StoreFullness? = { null },
        nowMs: () -> Long = { System.currentTimeMillis() }
    ): PrewarmOutcome {
        // Skip what is already there — in memory, then on disk — so a warm cache costs a listing and the
        // pass opens no window at all when nothing is left to build (the dot stays dark, correctly).
        val missing = pending(requests, storedNames)
        val warm = requests.size - missing.size
        if (missing.isEmpty()) {
            trace("PREWARM warm requested=${requests.size} built=0")
            return PrewarmOutcome(built = 0, warm = warm, stoppedByCaps = false)
        }
        // Own-ship outward: the boat's own water first, so the arm most likely next reuses the tiles it
        // already stands on before the far ones.
        val ordered = missing.sortedBy { distanceM(it.center, orderFrom) }
        var built = 0
        var stoppedByCaps = false
        var stoppedByCapacity = false
        var stoppedByGate = false
        val startMs = nowMs()
        for (request in ordered) {
            if (built >= maxTiles) {
                stoppedByCaps = true
                break
            }
            if (nowMs() - startMs >= maxTimeMs) {
                stoppedByCaps = true
                break
            }
            // The store's own room under the growth target ends the pass: once it can give no more under
            // that share it evicts its oldest entry for each new one, so building past it would retire the
            // water this pass already prepared.
            val fullness = storeFullness()
            if (fullness != null && !fullness.hasRoomAt(targetPct)) {
                stoppedByCapacity = true
                break
            }
            // The gate, asked before every tile. The seam **yields** there while the app is busy (the pass
            // resumes to build the tiles it had queued) and answers `false` where the pass must not run at
            // all — battery-saver switched on mid-pass — which stands the pass down here rather than
            // letting it run to its cap.
            if (!awaitMayProceed()) {
                stoppedByGate = true
                break
            }
            trace("PREWARM tile row=${request.key.tileRow} col=${request.key.tileCol}")
            // The window is opened **only around the tile the walk really builds** — never over a gate wait
            // or a warm skip — and closed in a `finally`, so a cancelled build cannot latch the dot lit.
            // The grace merges the tiles that follow one another; a wait at the gate lets it fall.
            FineTileCache.beginWarmTile()
            try {
                map.get(request.key) { request.build() }
            } finally {
                FineTileCache.endWarmTile()
            }
            built++
        }
        trace("PREWARM done requested=${requests.size} warm=$warm built=$built caps=$stoppedByCaps")
        return PrewarmOutcome(built, warm, stoppedByCaps, stoppedByCapacity, stoppedByGate)
    }
}

/**
 * **Whether the pre-warm may work**, from the gates the user settled: it runs when **no route is armed**,
 * **off battery-saver**, and **with no other heavy foreground job** taking the CPU (a depth-raster
 * regeneration). A pure function, so the truth table is pinned without a device; the app edge reads the
 * three facts and hands them here — and asks it again before every tile, so a job that starts mid-pass
 * pauses the pass rather than being raced.
 *
 * The idle gate is deliberately gone (D4): the pass starts with the app and keeps its own lifecycle, so a
 * moving boat is no longer a reason to leave the water cold.
 */
internal fun prewarmGate(routeArmed: Boolean, batterySaver: Boolean, heavyJob: Boolean): Boolean =
    !routeArmed && !batterySaver && !heavyJob

/**
 * **Whether [box] sits inside the baked region [region].** A fine tile can exist only where the baked depth
 * and coast do, so a box any part of which falls outside is **reported rather than shrunk**: the pass names
 * the overflow and still walks what it can, and the report says so. `false` where the region is not loaded.
 */
internal fun boxInsideRegion(box: BBox, region: BBox?): Boolean =
    region != null &&
        box.latSouth >= region.latSouth && box.latNorth <= region.latNorth &&
        box.lonWest >= region.lonWest && box.lonEast <= region.lonEast

/** Metres between two points, on the equal-area approximation the corridor arithmetic already uses. */
private fun distanceM(a: LatLng, b: LatLng): Double {
    val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    val mPerDegLon = mPerDegLat * cos(Math.toRadians((a.latitude + b.latitude) / 2.0))
    val dLat = (a.latitude - b.latitude) * mPerDegLat
    val dLon = (a.longitude - b.longitude) * mPerDegLon
    return sqrt(dLat * dLat + dLon * dLon)
}
