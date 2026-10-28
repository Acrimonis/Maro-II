package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * **A costed marker as the route engine reads it** — the projection the `spatial/` layer holds in its
 * own vocabulary, so no engine or field file imports `UserMarker`. Only a marker the user placed becomes
 * one: a **Circle**, a **Corridor** and, since a point may carry a well, a **Pin**; an auto marker (a
 * record, not a rule) is dropped at the world.
 *
 * The scale is the marker's shipped one: `1..9` is a **price** ([step]), the top step is the **wall**
 * ([isWall]) the route may never cross — and a Pin is never a wall, so its own cost tops out at a price.
 *
 * @property id    The marker's id, carried for the fine tile key and any future reading.
 * @property geometry The price area, the wall's own containment and the well's fall-off.
 * @property step  The price step (1–9); 0 where this marker is a wall.
 * @property isWall True where this marker is the wall rather than a price.
 */
data class RouteMarker(
    val id: String,
    val geometry: RouteMarkerGeometry,
    val step: Int,
    val isWall: Boolean
)

/**
 * The two geometries a costed marker may carry — a **price area**, never a bare point, so a Pin reaches
 * the field as a body of radius `0` whose whole price is the collar.
 *
 * Each answers three reads. [contains] is the marker's **own body** — the water a wall blocks. [wellFactor]
 * is the price's own shape: full inside the body, falling away across the **proximity collar** the map
 * already draws, so a cheap mark is only leaned around and a dear one is given a wide berth.
 * [boundaryDistanceM] is the metres to the **outer priced edge** — the price walk's declared clearance.
 *
 * That declaration must be a **true distance** to a real arm change, **caps included**, or the group proof
 * is unsound and the drawn line can move silently.
 *
 * **The collar is the marker's own proximity**, the same reach the map draws and the where-am-I match
 * gates on, so there is one number per marker and no second control: moving the ring moves the well.
 */
sealed interface RouteMarkerGeometry {

    /** True when [p] stands inside the marker's own body — the wall's own area, never the collar. */
    fun contains(p: LatLng): Boolean

    /**
     * **The well's own factor at [p]**, in `0.0..1.0`: `1.0` anywhere inside the body, a straight line to
     * `0.0` at the outer edge of the proximity collar, and `0.0` beyond it. A zero collar prices the body
     * alone — the pre-well behaviour, kept for a marker stored without a proximity.
     */
    fun wellFactor(p: LatLng): Double

    /** The metres from [p] to the **outer priced edge** — the price walk's declared clearance. */
    fun boundaryDistanceM(p: LatLng): Double

    /**
     * **The box this marker can reach at all — its body and its collar, grown by [slackM] and a twentieth.**
     * Beyond it [wellFactor] is `0.0` and [contains] is `false`, so a raster whose box misses it prices and
     * walls no cell of that raster. The box is written in degrees at the geometry's **own** latitude, the
     * longitude side at the narrower of its latitudes, so it is never smaller than the reach in metres; the
     * slack covers the hair the planar reads and the great-circle ones can differ by.
     */
    fun reachBox(slackM: Double): BBox

    /** A circular body: a flat core within [radiusM] of [center], and a collar of [proximityM] beyond it. */
    data class Circle(
        val center: LatLng,
        val radiusM: Double,
        val proximityM: Double
    ) : RouteMarkerGeometry {
        override fun contains(p: LatLng): Boolean =
            SpatialOperations.haversine(p, center) <= radiusM

        override fun wellFactor(p: LatLng): Double =
            collarFactor(SpatialOperations.haversine(p, center), radiusM, proximityM)

        override fun boundaryDistanceM(p: LatLng): Double =
            abs(SpatialOperations.haversine(p, center) - (radiusM + proximityM))

        override fun reachBox(slackM: Double): BBox =
            boxAround(center.latitude, center.latitude, center.longitude, center.longitude, radiusM + proximityM, slackM)
    }

    /**
     * A linear body between [p1] and [p2] with a width: a flat core within `widthM / 2` of the segment,
     * its **rounded end caps included** ([SpatialOperations.pointToSegmentDistance] clamps to the
     * segment, so a point beyond an end reads its distance to that end — the cap's own boundary), and a
     * collar of [proximityM] beyond it.
     */
    data class Corridor(
        val p1: LatLng,
        val p2: LatLng,
        val widthM: Double,
        val proximityM: Double
    ) : RouteMarkerGeometry {
        private val halfWidthM: Double get() = widthM / 2.0

        override fun contains(p: LatLng): Boolean =
            SpatialOperations.pointToSegmentDistance(p, p1, p2) <= halfWidthM

        override fun wellFactor(p: LatLng): Double =
            collarFactor(SpatialOperations.pointToSegmentDistance(p, p1, p2), halfWidthM, proximityM)

        override fun boundaryDistanceM(p: LatLng): Double =
            abs(SpatialOperations.pointToSegmentDistance(p, p1, p2) - (halfWidthM + proximityM))

        override fun reachBox(slackM: Double): BBox = boxAround(
            min(p1.latitude, p2.latitude), max(p1.latitude, p2.latitude),
            min(p1.longitude, p2.longitude), max(p1.longitude, p2.longitude),
            halfWidthM + proximityM, slackM
        )
    }
}

/**
 * The box `[latSouth, latNorth] x [lonWest, lonEast]` grown on every side by `reachM x 1.05 + slackM`, the
 * longitude side converted at the box's furthest latitude from the equator — the widest degrees the reach
 * can need — so the box is a superset of the reach whatever the planar read's own mid-latitude.
 */
private fun boxAround(
    latSouth: Double,
    latNorth: Double,
    lonWest: Double,
    lonEast: Double,
    reachM: Double,
    slackM: Double
): BBox {
    val growM = reachM * REACH_SLACK_FACTOR + slackM
    val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    val farthestAbsLat = max(abs(latSouth), abs(latNorth))
    val mPerDegLon = mPerDegLat * cos(Math.toRadians(farthestAbsLat)).coerceAtLeast(1e-6)
    return BBox(
        latSouth - growM / mPerDegLat, latNorth + growM / mPerDegLat,
        lonWest - growM / mPerDegLon, lonEast + growM / mPerDegLon
    )
}

/**
 * **The twentieth a reach is grown by before it is written in degrees** — the over-inclusion that keeps a
 * reach filter exact: the rasterizer's sweeps expand their boxes at a lattice's reference latitude while they
 * measure at the points' own, so a reach written to the metre could leave out an edge that still stands
 * within it by distance. Over-inclusion never moves a byte; under-inclusion could.
 */
internal const val REACH_SLACK_FACTOR = 1.05

/**
 * **The well's one fall-off law, in one home.** Full at the body's own edge ([coreM], the flat core) and
 * a straight line to zero at the collar's outer edge ([coreM] + [proximityM]): `1.0` inside the body,
 * `0.0` beyond the collar, and the position between the two bands in between. A collar of zero — a marker
 * stored without a proximity — prices the body alone.
 */
private fun collarFactor(distanceM: Double, coreM: Double, proximityM: Double): Double {
    if (distanceM <= coreM) return 1.0
    if (proximityM <= 0.0) return 0.0
    val outerM = coreM + proximityM
    return if (distanceM >= outerM) 0.0 else (outerM - distanceM) / proximityM
}

/**
 * **The one marker price law** — a cell costs its base times the marker's own pace-free factor, the
 * `1 + m` the well's summed step-scale reads. It is a **single multiply**, never an addition to re-spell:
 * the base is the target grid's own, and the factor is [markerFactorAt]'s summed `step × strength ×
 * wellFactor`, so the fine layer and the coarse interior price off one home.
 */
internal fun markerPriceSec(baseCostSec: Double, factor: Double): Double = baseCostSec * factor

/**
 * **The one marker factor law** — the pace-free `m` a point's priced markers sum to: each priced
 * marker's `step × strength × wellFactor`, its body-and-collar shape scaled by the step and the shipped
 * [strength] (`route.marker.costPerStep`, with no screen behind it). It is **linear**, **λ-free** — a
 * marker is a rule, not a rung's aversion, so all three
 * rungs price it alike — and it only ever **adds**, so no point's factor drops below zero and the A\*'s
 * closed set stays valid (the field's own invariant). The wall is never priced: a wall is law and stands
 * whatever [enabled] says.
 *
 * The rasterizer and the tile's own unit-base build read this one law, so the factor the field priced
 * with and the factor a tile stores cannot disagree.
 */
internal fun markerFactorAt(
    markers: List<RouteMarker>,
    strength: Double,
    enabled: Boolean,
    p: LatLng
): Double {
    if (!enabled || markers.isEmpty()) return 0.0
    var sum = 0.0
    for (m in markers) {
        if (m.isWall) continue
        val factor = m.geometry.wellFactor(p).coerceIn(0.0, 1.0)
        if (factor > 0.0) sum += m.step * strength * factor
    }
    return sum
}

/**
 * **The marker sources for one field** — a soft price source for the priced markers (1–9) and a rastered
 * hard source for the walls, or an empty list where no marker carries a cost. Both are added **only when
 * their own list is non-empty**, so a marker-free solve keeps today's fast path (`hasSoft` and
 * `hasBlocking` both false).
 *
 * The price is a single source summing over every priced marker that reaches the point — its own step
 * scaled by that marker's well factor — its declared clearance the **minimum** outer-edge distance across
 * those markers, so a point beyond every collar is provably free and the walk can group there. The wall
 * is the depth gate's own shape: a per-cell step tested at every mark, never a distance the margin stands
 * off, and it blocks the marker's **body** alone — the collar is a price, never a wall.
 *
 * **The priced markers declare their own arm** — reported under [PriceArm.MARKER], beside the ring and
 * band arms the other sources name, so the price's own report can say the marker charged a point
 * rather than lump it into the residue. One local price lambda feeds both the source's total and its
 * `armAt`, so the arm it reports can never disagree with the seconds it charges. The **wall carries no
 * arm**: it is law, not a price, and never enters the total.
 *
 * @param priceEnabled the price's own switch (`route.marker.enabled`); the **wall never reads it** — a
 *   wall is law and stands whatever the switch says.
 */
internal fun markerSources(
    markers: List<RouteMarker>,
    baseCostSec: Double,
    strength: Double,
    priceEnabled: Boolean
): List<RouteCostSource> {
    if (markers.isEmpty()) return emptyList()
    val sources = ArrayList<RouteCostSource>(2)
    val priced = if (priceEnabled) markers.filter { !it.isWall } else emptyList()
    if (priced.isNotEmpty()) {
        // The price has **one home**: this lambda is both the source's total and the money its own arm
        // reports, so `armAt` can never claim a figure the summed price disagrees with.
        val markerPriceSecAt: (LatLng) -> Double = { p ->
            markerPriceSec(baseCostSec, markerFactorAt(markers, strength, priceEnabled, p))
        }
        sources.add(
            RouteCostSource.Soft(
                priceSec = markerPriceSecAt,
                tag = MultipassCellState.ZONE,
                clearanceAt = { p ->
                    var nearest = Double.MAX_VALUE
                    for (m in priced) nearest = minOf(nearest, m.geometry.boundaryDistanceM(p))
                    nearest
                },
                // The priced markers' own arm, so the price's report names the marker that charged a
                // point beside the ring and band arms rather than reporting it as `OTHER`.
                armAt = { p -> PriceArm.MARKER to markerPriceSecAt(p) }
            )
        )
    }
    val walls = markers.filter { it.isWall }
    if (walls.isNotEmpty()) {
        val blockedAt: (LatLng) -> Boolean = { p -> walls.any { it.geometry.contains(p) } }
        sources.add(
            // The depth gate's own shape: a rastered per-cell **step**, its distance `0` where it blocks
            // and `MAX_VALUE` everywhere else, so the pull folds it into its per-mark test and never its
            // margin — a step carries no bound a nearby reading could prove.
            RouteCostSource.Hard(
                blockedAt = blockedAt,
                distanceAt = { p -> if (blockedAt(p)) 0.0 else Double.MAX_VALUE }
            )
        )
    }
    return sources
}
