package ykws.android.maro.spatial.multipass

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
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
 * **The two decisions an algorithm makes about its own walk** — the rectangles it rasterizes the corridor
 * with, each at its own cell size, and the regions its second pass is allowed to re-rasterize.
 *
 * They are the whole of what separates one route algorithm from another: everything else — the corridor,
 * the A\*, the taut pull, the corner snap, the clock — is the same work over whichever cells it is handed.
 * A plan is therefore the engine's **injection point**, and `avoid`'s plan is the one that reproduces
 * today's behaviour exactly.
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
     * The regions the **second pass** may re-rasterize around [line], grown from the engine's own
     * [outsideMarginM] and [cellM] — one box for the line's own span, a chain of them for a corridor. An
     * empty list is where no region can be cut, the case the caller reads as *nothing to re-search*.
     *
     * [corridor] is the box the whole lookup is bounded to; a plan that honours it hands its regions back
     * unclamped, because the engine clamps whatever it is given.
     */
    fun secondPassRegions(
        line: List<LatLng>,
        corridor: BBox,
        outsideMarginM: Double,
        cellM: Double
    ): List<BBox> =
        if (line.isEmpty()) emptyList() else listOf(inflateBox(lineBBox(line), outsideMarginM + cellM))

    /**
     * The **fine cell this algorithm's second pass and its clock read**, in metres, given the configured
     * [baseCellM]. A metres value rather than a ratio, because the precision a drawn line resolves at is
     * the fact and a ratio drifts with the coarse cell it multiplies: `evolutive` answers its own key,
     * while [UniformGridPlan] reproduces `avoid`'s ratio and therefore today's behaviour exactly.
     */
    fun fineCellM(baseCellM: Double): Double
}

/**
 * **The shipped plan: one tile over the whole corridor and the settled line's own bounding box, widened.**
 * It is the behaviour every `avoid` answer was found on, kept here as a plan so a second algorithm can be a
 * second plan rather than a second pipeline. Its single tile and its single region are what make it
 * reproduce today exactly.
 */
object UniformGridPlan : RouteGridPlan {

    override fun firstWalkGrid(corridor: BBox, baseCellM: Double): List<GridTile> =
        listOf(GridTile(corridor, baseCellM))

    /** The ratio's own arithmetic, so `avoid`'s metres are its coarse cell times its ratio and nothing else. */
    override fun fineCellM(baseCellM: Double): Double = baseCellM * AppConfig.routeAvoidFineCellRatio
}

/**
 * **`evolutive`'s plan: the adaptive grid's own two sizes, before its layers land.** It answers the first
 * walk at the engine's own coarse cell and the second pass at its own metres value, so the metres key is
 * live from the first commit while the walk is still one rectangle — the fine band beside the coarse
 * interior and the corridor chain are the steps that follow, and each one replaces a member here rather
 * than the pipeline both engines share.
 */
object EvolutiveGridPlan : RouteGridPlan {

    override fun firstWalkGrid(corridor: BBox, baseCellM: Double): List<GridTile> =
        listOf(GridTile(corridor, AppConfig.routeEvolutiveGridCellM))

    /**
     * **The chain, not the span**: the second pass walks a ribbon of side `2w` along the settled line
     * rather than the rectangle that contains it, at the engine's own fine cell — the very lattice the
     * pass will window — so a U-shaped route no longer pays for the box its ends describe.
     */
    override fun secondPassRegions(
        line: List<LatLng>,
        corridor: BBox,
        outsideMarginM: Double,
        cellM: Double
    ): List<BBox> = corridorChain(
        line,
        AppConfig.routeEvolutiveFineCorridorHalfWidthM,
        WalkLattice.of(corridor, fineCellM(cellM))
    )

    override fun fineCellM(baseCellM: Double): Double = AppConfig.routeEvolutiveGridFineCellM
}

/**
 * The axis-aligned box a polyline spans — the second pass's own frame, and the shape whose **span** a
 * corridor plan exists to stop paying for.
 */
internal fun lineBBox(points: List<LatLng>): BBox {
    var latSouth = Double.MAX_VALUE
    var latNorth = -Double.MAX_VALUE
    var lonWest = Double.MAX_VALUE
    var lonEast = -Double.MAX_VALUE
    for (p in points) {
        if (p.latitude < latSouth) latSouth = p.latitude
        if (p.latitude > latNorth) latNorth = p.latitude
        if (p.longitude < lonWest) lonWest = p.longitude
        if (p.longitude > lonEast) lonEast = p.longitude
    }
    return BBox(latSouth, latNorth, lonWest, lonEast)
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
