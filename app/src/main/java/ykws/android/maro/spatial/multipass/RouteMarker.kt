package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.abs

/**
 * **A costed marker as the route engine reads it** — the projection the `spatial/` layer holds in its
 * own vocabulary, so no engine or field file imports `UserMarker`. Only a **Circle** or a **Corridor**
 * that carries a routing cost becomes one: a Pin (no area) and an auto marker (a record, not a rule) are
 * dropped at the world.
 *
 * The scale is the marker's shipped one: `1..9` is a **price** ([step]), the top step is the **wall**
 * ([isWall]) the route may never cross.
 *
 * @property id    The marker's id, carried for the fine tile key and any future reading.
 * @property geometry The price area and the wall's own containment.
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
 * The two geometries a costed marker may carry — a **price area**, never a point.
 *
 * Both answer the two reads the cost field needs: [contains] (the price is paid here, the wall blocks
 * here) and [boundaryDistanceM] — the metres to the geometry's own boundary, the **price walk's declared
 * clearance**. That declaration must be a **true distance** to a real arm change, **caps included**, or
 * the group proof is unsound and the drawn line can move silently.
 */
sealed interface RouteMarkerGeometry {

    /** True when [p] stands inside this geometry — the price is paid here and the wall blocks here. */
    fun contains(p: LatLng): Boolean

    /** The metres from [p] to this geometry's own boundary — the price walk's declared clearance. */
    fun boundaryDistanceM(p: LatLng): Double

    /** A circular zone: the price is paid within [radiusM] of [center]. */
    data class Circle(val center: LatLng, val radiusM: Double) : RouteMarkerGeometry {
        override fun contains(p: LatLng): Boolean =
            SpatialOperations.haversine(p, center) <= radiusM

        override fun boundaryDistanceM(p: LatLng): Double =
            abs(SpatialOperations.haversine(p, center) - radiusM)
    }

    /**
     * A linear zone between [p1] and [p2] with a width: the price is paid within `widthM / 2` of the
     * segment, its **rounded end caps included** ([SpatialOperations.pointToSegmentDistance] clamps to
     * the segment, so a point beyond an end reads its distance to that end — the cap's own boundary).
     */
    data class Corridor(val p1: LatLng, val p2: LatLng, val widthM: Double) : RouteMarkerGeometry {
        private val halfWidthM: Double get() = widthM / 2.0

        override fun contains(p: LatLng): Boolean =
            SpatialOperations.pointToSegmentDistance(p, p1, p2) <= halfWidthM

        override fun boundaryDistanceM(p: LatLng): Double =
            abs(SpatialOperations.pointToSegmentDistance(p, p1, p2) - halfWidthM)
    }
}

/**
 * **The one marker price law.** A cell inside a priced marker adds the **base per-metre cost** scaled by
 * the marker's step and the user's [strength]. It is **linear** and **λ-free** — a marker is a rule, not a
 * Driving preference, so all three rungs price it alike — and it only ever **adds**, so no passable cell
 * drops below the base and the A\*'s closed set stays valid (the field's own invariant).
 */
internal fun markerPriceSec(baseCostSec: Double, step: Int, strength: Double): Double =
    baseCostSec * step * strength

/**
 * **The marker sources for one field** — a soft price source for the priced markers (1–9) and a rastered
 * hard source for the walls, or an empty list where no marker carries a cost. Both are added **only when
 * their own list is non-empty**, so a marker-free solve keeps today's fast path (`hasSoft` and
 * `hasBlocking` both false).
 *
 * The price is a single source summing over every priced marker that contains the point, its declared
 * clearance the **minimum** boundary distance across those markers — one source, so the dearest tag the
 * field writes is the one zone's for a marker cell. The wall is the depth gate's own shape: a per-cell
 * step tested at every mark, never a distance the margin stands off.
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
        sources.add(
            RouteCostSource.Soft(
                priceSec = { p ->
                    var sec = 0.0
                    for (m in priced) {
                        if (m.geometry.contains(p)) sec += markerPriceSec(baseCostSec, m.step, strength)
                    }
                    sec
                },
                tag = MultipassCellState.ZONE,
                clearanceAt = { p ->
                    var nearest = Double.MAX_VALUE
                    for (m in priced) nearest = minOf(nearest, m.geometry.boundaryDistanceM(p))
                    nearest
                }
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
