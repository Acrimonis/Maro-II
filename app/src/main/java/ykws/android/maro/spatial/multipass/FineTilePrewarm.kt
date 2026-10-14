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
    val stoppedByCaps: Boolean
)

/**
 * **The pre-warm: a startup pass that builds the fine layer's tiles over a fixed box before an arm asks
 * for them, so a later arm over that water reuses them.**
 *
 * It reaches the fine-tile map the arm reaches, one tile at a time, and the map is the same one the store
 * is installed into (`FineTileCache.shared`), so the pass is **store-first** for free: a tile already on
 * disk is a hit and costs neither a build nor a byte. Before it opens its busy window it asks the map and
 * the store's own names, so a **fully warm cache builds nothing and shows no dot** — the correct outcome a
 * boat restarting on warm water sees, not a fault.
 *
 * **The window is the pass, not a tile.** The store's 400 ms grace merges the tiles of one arm, but a pass
 * over a whole box may leave tiles far enough apart that the grace would let the dot fall between them. So
 * the pass **holds** the holder's window for its whole duration ([`FineTileCache.beginPrewarm`]) and
 * releases it at the end: one `busy on` at the first tile and one `busy off` when the pass ends. That is
 * the witness chosen over extending the grace — the grace keeps its own job (an arm's merging) untouched,
 * and the pre-warm states its window explicitly rather than moving a shared constant.
 *
 * **It is pure Kotlin** — no Android type reaches it. The gating (idle only, off battery-saver, paused on an
 * arming) lives at the app edge; this class owns the arithmetic of the pass: skip-what-is-warm, order
 * own-ship outward, honour the two caps, and pause (not stop) the moment [awaitMayProceed] says a route is
 * armed, resuming afterwards to fill the holes.
 */
internal class FineTilePrewarm(
    /** The fine-tile map to fill — in production the installed `FineTileCache.shared`. */
    private val map: FineTileMap,
    /** Where the pass's own lines go — the route tag on device, inert off it. */
    private val trace: (String) -> Unit = {}
) {

    /**
     * **Run one pass over [requests].** The tiles already warm are skipped first (no window opens when none
     * is left); the rest are built nearest-[orderFrom]-first, each bounded by [maxTiles] and [maxTimeMs].
     * Before every tile [awaitMayProceed] is asked — the app edge suspends it there while a route is armed,
     * so the pass pauses rather than starving a live arm, and resumes to fill what it left.
     *
     * [nowMs] is injected so a test drives the time budget without a clock; [awaitMayProceed] defaults to a
     * no-op so a test never pauses.
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
        awaitMayProceed: suspend () -> Unit = {},
        nowMs: () -> Long = { System.currentTimeMillis() }
    ): PrewarmOutcome {
        // Skip what is already there — in memory, then on disk — so a warm cache costs a listing and the
        // pass opens no window at all when nothing is left to build (the dot stays dark, correctly).
        val missing = ArrayList<FineTileRequest>(requests.size)
        for (request in requests) {
            if (map.holds(request.key)) continue
            if (FineTileCodec.entryName(request.key) in storedNames) continue
            missing.add(request)
        }
        val warm = requests.size - missing.size
        if (missing.isEmpty()) {
            trace("PREWARM warm requested=${requests.size} built=0")
            return PrewarmOutcome(built = 0, warm = warm, stoppedByCaps = false)
        }
        // Own-ship outward: the boat's own water first, so the arm most likely next reuses the tiles it
        // already stands on before the far ones.
        val ordered = missing.sortedBy { distanceM(it.center, orderFrom) }
        FineTileCache.beginPrewarm()
        var built = 0
        var stoppedByCaps = false
        val startMs = nowMs()
        try {
            for (request in ordered) {
                if (built >= maxTiles) {
                    stoppedByCaps = true
                    break
                }
                if (nowMs() - startMs >= maxTimeMs) {
                    stoppedByCaps = true
                    break
                }
                // Pause (never stop) the moment a route is armed: the pass yields here, the arm runs, and
                // the pass resumes to build the tiles it had queued.
                awaitMayProceed()
                trace("PREWARM tile row=${request.key.tileRow} col=${request.key.tileCol}")
                map.get(request.key) { request.build() }
                built++
            }
        } finally {
            FineTileCache.endPrewarm()
        }
        trace("PREWARM done requested=${requests.size} warm=$warm built=$built caps=$stoppedByCaps")
        return PrewarmOutcome(built = built, warm = warm, stoppedByCaps = stoppedByCaps)
    }
}

/**
 * **Whether the pre-warm may start**, from the three gates the user settled: it runs only when the boat is
 * **idle**, when **no route is armed**, and **off battery-saver**. A pure function, so the truth table is
 * pinned without a device; the app edge reads the three facts and hands them here.
 */
internal fun prewarmGate(idle: Boolean, routeArmed: Boolean, batterySaver: Boolean): Boolean =
    idle && !routeArmed && !batterySaver

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
