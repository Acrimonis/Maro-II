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
     * builder grows the coast's harvested segments by the cut reaches, snaps the union to the corridor's
     * own fine lattice and merges it into windows; the mask then keeps the answer's own membership bands.
     * `evolutive` answers today's coast ribbon and nothing else, so its windows are byte-identical to the
     * ones the builder derived for it before the seam moved here; `avoid`'s plan answers nothing at all,
     * so its walk stays the single coarse grid.
     */
    fun fineWater(query: FineWaterQuery): FineWater

    /**
     * **Whether this plan prices the gate berth** — the 20→50 m standoff price off the gate edge. It
     * rides the plan because the berth is a decision about *this* algorithm's own water rather than a
     * switch any engine may flip: `avoid` answers `false` and its field, its grid and its line are
     * untouched, while `evolutive` answers `true` and prices the `route.evolutive.berth.*` keys behind
     * the gate's own switch. A plan still prices nothing by itself — the runner reads the berth law from
     * [berthLawOf].
     */
    val pricesBerth: Boolean get() = false
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

    /** `avoid` prices no berth: its water, its grid and its line are exactly what they were. */
    override val pricesBerth: Boolean get() = false
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
        val collarM =
            if (AppConfig.routeAvoidDepthGateEnabled && query.world.depthReady) {
                depthCollarReachM(query.marginM, query.baseCellM)
            } else {
                0.0
            }
        return FineWater(
            coastReachesM = listOf(cutReach),
            coastBandsM = listOf(0.0..memberRadius),
            depthCollarM = collarM
        )
    }

    /**
     * **This engine's berth: the shallow wall's own neighbourhood, priced off its edge.** The gate's
     * switch governs it — with the gate off there is no wall for the berth to lean against, so the water
     * is exactly `avoid`'s — and the law's own values live on the `route.evolutive.berth.*` keys.
     */
    override val pricesBerth: Boolean get() = true
}

/**
 * **The low-depth collar's reach (m off a shallow cell)** — the gate's own margin (the 20 m exclusion)
 * plus the same `margin + one coarse cell` the shore ribbon uses, so the fine 20 m cells follow the
 * shallow water out past the weighted band and a ribbon's width beyond it.
 */
internal fun depthCollarReachM(marginM: Double, baseCellM: Double): Double =
    AppConfig.routeAvoidDepthGateMarginM + (marginM + baseCellM)

/** [box] grown by [metresM] on every side, in the degrees the box itself is written in. */
internal fun inflateBox(box: BBox, metresM: Double): BBox {
    val midLat = (box.latSouth + box.latNorth) / 2.0
    val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))
    val dLat = metresM / mPerDegLat
    val dLon = metresM / mPerDegLon
    return BBox(box.latSouth - dLat, box.latNorth + dLat, box.lonWest - dLon, box.lonEast + dLon)
}
