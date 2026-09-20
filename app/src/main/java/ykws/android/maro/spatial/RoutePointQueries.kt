package ykws.android.maro.spatial

/**
 * A priced speed zone a point stands in: the identity the traversal rule keys on, the name the forced
 * crossing is reported by, and the limit the search prices that water at.
 *
 * It is a plain value rather than the regulation model because the search must not depend on it: the
 * adapter is the one file that knows the zone layer, and this is all the search needs to hear from it.
 */
data class PricedZoneRef(val id: String, val name: String, val limitKn: Double)

/**
 * The route's read-only view of the live spatial layers.
 *
 * This interface is the whole of what the route search knows about the world: the mesh supplies
 * geometry, and these three answers supply the water test and the live cost. `RouteSpatialAdapter`
 * is its only runtime implementation and the single file allowed to import the coastline and
 * regulation types, so the search itself has no dependency on either.
 */
interface RoutePointQueries {

    /** @return true when the point is water rather than land or the open sea past the covered band. */
    fun isWater(latitude: Double, longitude: Double): Boolean

    /**
     * The most restrictive priced speed zone covering the point, or `null` when no zone covers it.
     *
     * **It is one answer with two readers.** The search prices an edge by the limit this carries, and
     * the same answer decides whether the point stands inside a zone **interior** — which is not
     * traversable, the rule keyed on the zone's own identity so that the zone a boat already stands in
     * can be excepted. The name is what a forced crossing is reported by.
     *
     * **The 300 m band is deliberately absent here.** The band's shape is a triangulation constraint
     * and every edge carries its in-band mark, so the band is priced by the mark together with
     * [coastalBandSpeedLimitKn] — folding it in here as well would be a second, drifting statement
     * of where the band begins, and it would make the coastal corridor itself a forbidden interior,
     * which it is not.
     *
     * Resolved live, per search, so a corrected regulation needs no re-bake.
     */
    fun pricedZoneAt(latitude: Double, longitude: Double): PricedZoneRef?

    /**
     * @return the distance (m) from the point to the nearest regulated-zone boundary, whether the
     *         point stands inside a zone or outside one, and [Double.POSITIVE_INFINITY] when no zone
     *         is near enough for the layer to answer.
     *
     * The route's berth is priced from this, and pricing it needs the distance, not the fact: a leg
     * pays for passing *near* an edge, and it pays more the nearer it passes. It is the live zone
     * layer's own reading, so a corrected regulation moves the berth with it and never needs a
     * rebake. The signature is absolute — the sign the index carries is what says "inside", which
     * the berth does not care about: hugging a zone's edge from within costs what hugging it from
     * without does.
     */
    fun distanceToZoneM(latitude: Double, longitude: Double): Double

    /**
     * The 300 m coastal band's own limit in knots.
     *
     * An edge marked in band is priced at this value even when both of its ends sit outside the
     * band, which is what lets a band crossing be priced exactly.
     */
    val coastalBandSpeedLimitKn: Double
}
