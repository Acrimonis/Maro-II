package ykws.android.maro.data.model

import kotlinx.serialization.Serializable

/**
 * Precomputed geometry for the regulatory **300 m band** (French *bande des
 * 300 m*, 5-knot speed limit) within a coastline region.
 *
 * The band is the 300 m contour of the `distanceToCoast` field restricted to
 * water (`isOnWater && distanceToCoast ≤ 300`). It is built once per region (see
 * `Zone300Builder`), cached inside [CoastlineData], and drawn as a static overlay.
 *
 * Fill is **water only**: land (mainland interior or island) is never filled —
 * around an island the band is a ring whose land middle is a polygon hole.
 *
 * @property fillPolygons Translucent fill areas (each an outer ring with optional
 *                        island holes).
 * @property seawardLines The red 300 m boundary, **seaward runs only** (open
 *                        polylines — never painted on the coast itself).
 * @property gridCellM    Mask grid spacing (m) used to build it — provenance.
 * @property bandM        Band half-width in meters (300).
 */
@Serializable
data class Zone300Data(
    val fillPolygons: List<BandPolygon>,
    val seawardLines: List<List<LatLng>>,
    val gridCellM: Double,
    val bandM: Double = 300.0
)

/**
 * One filled band area: an [outer] ring (the water region boundary) with zero or
 * more [holes] cut out (island land that must not be filled).
 *
 * Rings are ordered lists of distinct vertices (implicit closure — first vertex
 * is not duplicated at the end).
 */
@Serializable
data class BandPolygon(
    val outer: List<LatLng>,
    val holes: List<List<LatLng>> = emptyList()
)
