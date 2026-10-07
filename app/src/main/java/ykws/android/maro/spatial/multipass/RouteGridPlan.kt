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
     * The **first walk's grid**: one [`GridTile`] per rectangle the walk may use, each at the size the
     * plan chooses for it, given the configured [baseCellM] and the [corridor] the lookup is bounded to.
     * `avoid` answers the whole corridor at one size; a lattice answers a fine band over a coarse interior.
     */
    fun firstWalkGrid(corridor: BBox, baseCellM: Double): List<GridTile>

    /**
     * The **fine cell this algorithm's clock reads**, in metres, given the configured [baseCellM]. A
     * metres value rather than a ratio, because the precision a drawn line resolves at is the fact and a
     * ratio drifts with the coarse cell it multiplies: `evolutive` answers its own key, while
     * [UniformGridPlan] reproduces `avoid`'s ratio and therefore today's behaviour exactly.
     */
    fun fineCellM(baseCellM: Double): Double
}

/**
 * **The shipped plan: one tile over the whole corridor.**
 *
 * The single tile is the behaviour every `avoid` answer was found on, kept here as a plan so a second
 * algorithm can be a second plan rather than a second pipeline.
 */
object UniformGridPlan : RouteGridPlan {

    override fun firstWalkGrid(corridor: BBox, baseCellM: Double): List<GridTile> =
        listOf(GridTile(corridor, baseCellM))

    /** The ratio's own arithmetic, so `avoid`'s metres are its coarse cell times its ratio and nothing else. */
    override fun fineCellM(baseCellM: Double): Double = baseCellM * AppConfig.routeAvoidFineCellRatio
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

    /**
     * The two layers, **coarse interior first, fine band second** — the order the family's own layers hold,
     * so the engine's interior grid is the one its single-grid readings still name.
     */
    override fun firstWalkGrid(corridor: BBox, baseCellM: Double): List<GridTile> = listOf(
        GridTile(corridor, AppConfig.routeEvolutiveGridCellM),
        GridTile(corridor, AppConfig.routeEvolutiveGridFineCellM)
    )

    override fun fineCellM(baseCellM: Double): Double = AppConfig.routeEvolutiveGridFineCellM
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
