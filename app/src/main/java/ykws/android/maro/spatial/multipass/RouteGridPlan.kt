package ykws.android.maro.spatial.multipass

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.cos

/**
 * **One rectangle the walk may rasterize, at one cell size.** A plan answers a **list** of these, so a
 * single uniform grid and a two-layer lattice are the same shape to the caller: the lattice is more than
 * one tile, not a different kind of answer.
 */
data class GridTile(val box: BBox, val cellM: Double)

/**
 * **The decision an algorithm makes about its own walk** — the rectangles it rasterizes the corridor
 * with, each at its own cell size — and the fine cell its clock steps at.
 *
 * That grid is the whole of what separates one route algorithm from another: everything else — the
 * corridor, the A\*, the taut pull, the corner snap, the clock — is the same work over whichever cells it
 * is handed. A plan is therefore the engine's **injection point**, and `avoid`'s plan is the one that
 * reproduces today's behaviour exactly.
 *
 * A plan decides **only** where and at what size work happens. It never prices, never times and never
 * reads a switch: the engine's own readings and its clock stay its own, so a plan cannot move an answer
 * without changing the cells the answer was found on.
 */
interface RouteGridPlan {

    /**
     * The engine name this plan answers — the instrument's own label, read from the plan rather than
     * special-cased at the caller, so a third plan names itself truthfully without touching the engine.
     */
    val name: String

    /**
     * The **first walk's grid**: one [`GridTile`] per rectangle the walk may use, each at the size the
     * plan chooses for it, given the configured [baseCellM] and the [corridor] the lookup is bounded to.
     * `avoid` answers the whole corridor at one size; a lattice answers a fine band over a coarse interior.
     */
    fun firstWalkGrid(corridor: BBox, baseCellM: Double): List<GridTile>

    /**
     * The **fine cell this algorithm's clock reads**, in metres, given the configured [baseCellM]. A
     * metres value rather than a ratio, because the precision a drawn line resolves at is the fact and a
     * ratio drifts with the coarse cell it multiplies: each engine answers its own metres key.
     */
    fun fineCellM(baseCellM: Double): Double

    /**
     * **Where this plan's fine layer may stand, in geographic terms — before any lattice snapping.** The
     * builder grows the coast's harvested segments by the cut reaches, marks the zone-rim and depth
     * collars, snaps the union to the corridor's own fine lattice and merges it into windows; the mask
     * then keeps the answer's own membership bands. `evolutive` answers today's coast ribbon and nothing
     * else, so its windows are byte-identical to the ones the builder derived for it before the seam
     * moved here; `avoid`'s plan answers nothing at all, so its walk stays the single coarse grid.
     */
    fun fineWater(query: FineWaterQuery): FineWater

    /**
     * Whether this plan prices the shallow wall — a per-metre gradient over a band beside the depth
     * gate's wall, scoped to `selective` alone. `avoid` and `evolutive` answer `false`, so their search,
     * their guard and their cells are untouched by the depth nudge.
     */
    val pricesDepthBand: Boolean get() = false
}

/**
 * **The shipped plan: one tile over the whole corridor.**
 *
 * The single tile is the behaviour every `avoid` answer was found on, kept here as a plan so a second
 * algorithm can be a second plan rather than a second pipeline.
 */
object UniformGridPlan : RouteGridPlan {

    override val name: String = "uniform"

    override fun firstWalkGrid(corridor: BBox, baseCellM: Double): List<GridTile> =
        listOf(GridTile(corridor, baseCellM))

    /** `avoid`'s own metres key, the same idiom as `evolutive`'s: the base cell does not move the precision. */
    override fun fineCellM(baseCellM: Double): Double = AppConfig.routeAvoidGridFineCellM

    /** One tile is no lattice, and no lattice carries no fine layer: `avoid`'s mask is empty. */
    override fun fineWater(query: FineWaterQuery): FineWater = FineWater()
}

/**
 * **`evolutive`'s plan: the adaptive grid's own two sizes, answered as the two layers of one family.**
 *
 * The first walk is the coarse interior **and** the fine band — two tiles over the corridor, ordered
 * coarse first so the layer a layer-agnostic lookup resolves is the interior, which is the layer the
 * engine's own single-grid read sites (`GridContext.grid`, its two end cells) still describe. The two
 * sizes are the metres keys; the exact `5 : 1` nesting is built by the pair's own derivation, never by a
 * ratio carried here.
 */
object EvolutiveGridPlan : RouteGridPlan {

    override val name: String = "evolutive"

    /**
     * The two layers, **coarse interior first, fine band second** — the order the family's own layers hold,
     * so the engine's interior grid is the one its single-grid readings still name.
     */
    override fun firstWalkGrid(corridor: BBox, baseCellM: Double): List<GridTile> = listOf(
        GridTile(corridor, AppConfig.routeEvolutiveGridCellM),
        GridTile(corridor, AppConfig.routeEvolutiveGridFineCellM)
    )

    override fun fineCellM(baseCellM: Double): Double = AppConfig.routeEvolutiveGridFineCellM

    /**
     * **Today's coastal ribbon, moved onto the seam and not a metre of it moved.** The cut reach is the
     * one `fineWaterReachM` derived — the band's priced reach plus the margin plus one coarse cell — and
     * the membership band is the one the old `bandMask` applied: the water within `marginM + baseCellM`
     * of the coast. The `bandSpec == null` short-circuit becomes an empty answer here, and the builder's
     * windows come out byte-identical.
     */
    override fun fineWater(query: FineWaterQuery): FineWater {
        val band = query.world.bandWidthM
        if (!AppConfig.routeAvoidZone300Enabled || band <= 0.0) return FineWater()
        val cutReach =
            bandReachM(band, AppConfig.routeAvoidZone300OutsideMarginM) + query.marginM + query.baseCellM
        val memberRadius = query.marginM + query.baseCellM
        return FineWater(
            coastReachesM = listOf(cutReach),
            coastBandsM = listOf(0.0..memberRadius)
        )
    }
}

/**
 * **`selective`'s plan: the same two layers as `evolutive`, but the fine water is a union of thin
 * collars where the decisions are rather than a coastal blanket.**
 *
 * Four collars, one `route.selective.*` width key each, all starting at 100 m and each snapped to the
 * fine cell by the builder: the shoreline (0–100 m off the coast), a collar straddling the 300 m band's
 * outer boundary, a collar around every priced zone's boundary, and the water within 100 m of a cell the
 * depth gate blocks. The coast collars reuse the segment marking; the zone rim and the depth dilation add
 * their own marking passes. The mask keeps the collars' own membership, so a zone rim in open water stays
 * fine water while the interior stays coarse by design.
 *
 * The portable half — the geographic union, before the snap — is cached on the band width and the four
 * collar widths, the only fields it reads; the per-arm snap-and-merge is the cheap half.
 */
object SelectiveGridPlan : RouteGridPlan {

    override val name: String = "selective"

    override fun firstWalkGrid(corridor: BBox, baseCellM: Double): List<GridTile> = listOf(
        GridTile(corridor, AppConfig.routeSelectiveGridCellM),
        GridTile(corridor, AppConfig.routeSelectiveGridFineCellM)
    )

    override fun fineCellM(baseCellM: Double): Double = AppConfig.routeSelectiveGridFineCellM

    /** The depth nudge is `selective`'s alone — this plan's own declaration. */
    override val pricesDepthBand: Boolean get() = true

    override fun fineWater(query: FineWaterQuery): FineWater {
        val key = SelectiveMaskCache.Key(
            bandWidthM = query.world.bandWidthM,
            shoreCollarM = AppConfig.routeSelectiveShoreCollarM,
            bandCollarM = AppConfig.routeSelectiveBandCollarM,
            zoneRimM = AppConfig.routeSelectiveZoneRimM,
            depthCollarM = AppConfig.routeSelectiveDepthCollarM
        )
        return SelectiveMaskCache.getOrCompute(key) { union(query) }
    }

    /** The union of the four collars, in geographic terms — the one home of the four widths' reach. */
    private fun union(query: FineWaterQuery): FineWater {
        val band = query.world.bandWidthM
        val shoreM = AppConfig.routeSelectiveShoreCollarM
        val bandCollarM = AppConfig.routeSelectiveBandCollarM
        val reaches = ArrayList<Double>(2)
        val bands = ArrayList<ClosedFloatingPointRange<Double>>(2)
        // The shoreline collar: the water 0–shoreM off the coast.
        if (shoreM > 0.0) {
            reaches.add(shoreM)
            bands.add(0.0..shoreM)
        }
        // The band's outer collar: a strip straddling the band's own outer boundary, on the same read.
        if (bandCollarM > 0.0 && band > 0.0) {
            val half = bandCollarM / 2.0
            reaches.add(band + half)
            bands.add((band - half).coerceAtLeast(0.0)..(band + half))
        }
        return FineWater(
            coastReachesM = reaches,
            coastBandsM = bands,
            zoneRimM = AppConfig.routeSelectiveZoneRimM,
            depthCollarM = AppConfig.routeSelectiveDepthCollarM
        )
    }
}

/** [box] grown by [metresM] on every side, in the degrees the box itself is written in. */
internal fun inflateBox(box: BBox, metresM: Double): BBox {
    val midLat = (box.latSouth + box.latNorth) / 2.0
    val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))
    val dLat = metresM / mPerDegLat
    val dLon = metresM / mPerDegLon
    return BBox(box.latSouth - dLat, box.latNorth + dLat, box.lonWest - dLon, box.lonEast + dLon)
}
