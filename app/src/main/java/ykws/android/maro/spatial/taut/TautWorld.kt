package ykws.android.maro.spatial.taut

import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.RoutePointQueries

/**
 * A priced speed zone as the tracer's obstacle harvest sees it: the ring the traversal rule keys on, the
 * holes that are water rather than zone, and the limit the price reads.
 *
 * It is deliberately not the regulation model: the harvest needs the polygon, the name and the number,
 * and nothing else — a zone's provenance, its type and its vessel filter are the layer's business.
 */
data class TautZoneShape(
    val id: String,
    val name: String,
    val limitKn: Double,
    val outerRing: List<LatLng>,
    val holes: List<List<LatLng>> = emptyList()
)

/**
 * **What a world can answer right now — the one readiness member every world answers.**
 *
 * The tracer draws its wall from two layers, and each of them answers *permissively* while it is absent:
 * a missing coastline reads as water, so land is not a wall at all and the shore offset never applies,
 * and a missing depth grid reads as no sounding, so the 2 m gate has nothing to stand on. An engine that
 * searched inside that window would draw a line over land and call it a route, which is the failure the
 * review caught: readiness was gated on **one** of the two layers while three queries answered
 * permissively for the other.
 *
 * So readiness is a value with three cases rather than a boolean, the layer that is missing is named, and
 * **both** doors — the engine's own preparation and the search it runs — read it. The regulation is
 * deliberately not one of them: a zone is a price and an absent zone layer prices open water, which is
 * the permissive default the seam's contract already states for the live layers.
 */
enum class TautWorldReadiness {

    /** Both layers are in: the wall may be drawn, and the answer is the water the boat may use. */
    READY,

    /** No coastline: land is not a wall yet and the shore offset cannot apply. */
    COASTLINE_MISSING,

    /** No depth grid: the soundings the 2 m gate and the contour come from are not in. */
    DEPTH_MISSING
}

/**
 * **The world the obstacle tracer reads — the seam's queries, plus the geometry a corridor needs.**
 *
 * [RoutePointQueries] answers the point questions both engines ask (water, the zone at a point, the
 * distance to a zone and the band's limit). A corridor-scoped tracer needs three more kinds of answer,
 * and they belong on the same single seam rather than on a second importer: the **rings** inside a box
 * (so the harvest never walks the whole coastline for a 5 km corridor), the **2 m contour the depth
 * feature already knows how to trace**, and the **sounding** at a point, since where it is missing or
 * too coarse the wall comes from the shore instead of from the contour.
 *
 * The single implementation in the app is `RouteSpatialAdapter`, which is also the only file allowed to
 * import the coastline and regulation types — so the tracer itself depends on neither.
 *
 * A [DepthSample] is reused rather than a new type declared: it already carries exactly what the
 * sounding question is — the depth, the source whose resolution decides whether a 2 m contour may be
 * trusted there, and whether there is any data at all.
 */
interface TautWorld : RoutePointQueries {

    /**
     * **The one readiness member: whether every layer the wall is drawn from has landed.**
     *
     * It is a member of the seam rather than of one engine so that **every** world answers it — the
     * adapter, the harness's own world and the test fakes alike — and so that one reading of it serves
     * both doors: `TautRouteEngine.prepare` answers [TautWorldReadiness] as a named reason, and the
     * search itself refuses rather than pricing a permissive answer as a route.
     */
    val readiness: TautWorldReadiness

    /**
     * **Bumped whenever anything a route reads from this world changes** — the kept terrain's own
     * invalidator (§19.4).
     *
     * A corridor's terrain (`TautObstacles`, the zone set, the band) is a function of the box *and* of
     * the world, so a terrain kept across searches is stale the moment one of the layers behind it moves:
     * [RouteSpatialAdapter] therefore bumps this as **each layer's own load completes**, and the kept
     * entry is keyed on it beside the box and the numbers that shaped it. Without a named invalidator a
     * kept terrain would answer about a coastline that no longer exists, which is the one way a cache of
     * this kind can be wrong rather than merely slow.
     *
     * It is a member of the seam rather than of one engine so that **every** world answers it — the
     * adapter, the harness's own world and the test fakes alike. A world that never changes answers a
     * constant, which is the honest reading of "nothing here moves".
     */
    val generation: Int

    /**
     * The coastline polylines that meet [box], each as its own vertex list — islands closed, the open
     * mainland coast not. Returns the full polyline rather than the segments inside the box, because the
     * abstraction and the dilation are properties of the whole shape and a ring cut in half would be
     * dilated as an open line.
     */
    fun landPolylinesIn(box: BoundingBox): List<List<LatLng>>

    /** The priced zones whose own box meets [box], with their rings and their holes. */
    fun zoneShapesIn(box: BoundingBox): List<TautZoneShape>

    /**
     * The **2 m contour** inside [box], traced by the depth feature's own marching squares at its own
     * fine-level rule — a level that shallow is drawn only where the source resolution is fine, so no
     * contour is ever faked from a 115 m EMODnet cell. Where it cannot be drawn, the shore offset is the
     * wall instead, and this list is simply empty there.
     */
    fun shallowContoursIn(box: BoundingBox): List<List<LatLng>>

    /** The sounding at a point — depth, source and whether there is data at all. */
    fun depthSampleAt(latitude: Double, longitude: Double): DepthSample

    /** The distance (m) from a point to the nearest coastline segment, `+∞` when nothing is near. */
    fun distanceToCoastM(latitude: Double, longitude: Double): Double

    /** The depth grid's own box, or null when no grid is loaded — the corridor is clipped to it. */
    val depthBox: BoundingBox?

    /**
     * The coastal band's own width (m) — the 300 m band's shape, whose limit is
     * [RoutePointQueries.coastalBandSpeedLimitKn]. Both come from the band's own homes rather than from
     * a second spelling here.
     */
    val coastalBandWidthM: Double
}
