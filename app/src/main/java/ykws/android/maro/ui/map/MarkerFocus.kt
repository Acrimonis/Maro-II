package ykws.android.maro.ui.map

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.model.markers.markerProximityRangeM
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.tan

/** Tile edge osmdroid renders at, in pixels: the unit the zoom maths below is written in. */
private const val TILE_SIZE_PX = 256.0

/**
 * The camera a selected marker asks for: where to put the map centre, and the zoom that gives the
 * marker's framed extent its configured share of the smaller displayed dimension.
 *
 * Design: `xTrack/Markers/260918_FEAT_PLN_Markers_focus-zoom-fractions.md` §1, extended
 * `xTrack/Markers/261011_FEAT_PLN_Markers_focus-include-proximity.md` so the extent includes the collar.
 */
internal data class MarkerFocus(val centre: LatLng, val zoom: Double)

/**
 * The camera [marker] wants when its dashboard opens.
 *
 * The framed extent is the marker's **own body plus the proximity collar the map draws outward from
 * it**, so the whole proximity zone lands on screen: a circle's reach is `radius + proximity`, a
 * corridor's the band grown by the proximity on every side, and a pin — which has no body of its own —
 * its reach alone, floored at [pinFootprintM], the default zone's diameter. The collar is read through
 * its one home, [markerProximityRangeM], so the framing and the drawn ring can never disagree about
 * where the zone ends.
 *
 * The share is a **pixel budget on the smaller displayed dimension**: the extent's larger projected
 * span is made to equal `share × min(width, height)`, so a shape stretched along the screen's longer
 * axis frames tighter than the bare number suggests. The centre is [UserMarker.centerPoint] — the map
 * centre itself, with no allowance for the dashboard's own footprint. Every select applies the computed
 * zoom: a marker with a proximity collar is always framed to show that collar, the pin's old
 * zoom-in-only rule having been dropped with this change.
 *
 * @param viewportWidthPx  Map view width in pixels, as laid out.
 * @param viewportHeightPx Map view height in pixels, as laid out.
 * @param corridorShare    Corridor share, from `marker.focus.corridor_share`.
 * @param zoneShare        Circle and pin share, from `marker.focus.zone_share`.
 * @param pinFootprintM    Pin's minimum footprint in metres, from `marker.focus.pin_footprint_m`.
 * @param proximityPinM    Pin proximity default, from `marker.proximity.pinM`.
 * @param proximityZoneMultiplier Circle/Corridor proximity multiplier, from `marker.proximity.zoneMultiplier`.
 * @return null when the viewport is unmeasured — the caller then keeps its centre-only move.
 */
internal fun markerFocusTarget(
    marker: UserMarker,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    corridorShare: Double,
    zoneShare: Double,
    pinFootprintM: Double,
    proximityPinM: Double,
    proximityZoneMultiplier: Double
): MarkerFocus? {
    if (viewportWidthPx <= 0 || viewportHeightPx <= 0) return null

    val geometry = marker.geometry
    val proximityM = markerProximityRangeM(marker, proximityPinM, proximityZoneMultiplier)
    // The extent the map draws: each geometry grown by its own proximity collar, expressed back through
    // the model's own metres-to-degrees conversion so no second copy of that maths exists here.
    val extent = when (geometry) {
        is MarkerGeometry.Circle -> UserMarker.bboxOf(
            MarkerGeometry.Circle(geometry.center, geometry.radiusM + proximityM)
        )
        is MarkerGeometry.Corridor -> UserMarker.bboxOf(
            // The drawn collar reaches widthM/2 + proximityM either side, which is exactly a corridor of
            // this width — the model's own bbox then adds the same half-width back.
            MarkerGeometry.Corridor(geometry.p1, geometry.p2, geometry.widthM + 2.0 * proximityM)
        )
        // A pin's own body is a point, so its extent is its reach, floored at the nominal footprint.
        is MarkerGeometry.Pin -> UserMarker.bboxOf(
            MarkerGeometry.Circle(
                geometry.position,
                maxOf(proximityM, pinFootprintM / 2.0).coerceAtLeast(1.0)
            )
        )
    }
    val share = if (geometry is MarkerGeometry.Corridor) corridorShare else zoneShare
    val budgetPx = share * min(viewportWidthPx, viewportHeightPx)
    val target = zoomForExtent(extent, budgetPx)
    return MarkerFocus(marker.centerPoint, target)
}

/**
 * The zoom at which [extent]'s larger projected span covers [budgetPx] pixels, clamped to the map's
 * own [MAP_MIN_ZOOM]..[MAP_MAX_ZOOM] range.
 *
 * Web Mercator at [TILE_SIZE_PX]: the world is `256 · 2^zoom` pixels wide, longitude is linear in it,
 * and latitude is `ln(tan φ + sec φ)`. An axis with no extent imposes no constraint and is skipped,
 * which leaves the other axis — or, when both are flat, the map's ceiling.
 */
private fun zoomForExtent(extent: BBox, budgetPx: Double): Double {
    var zoom = Double.MAX_VALUE
    val dLon = extent.lonEast - extent.lonWest
    if (dLon > 0.0) {
        zoom = min(zoom, log2(budgetPx * 360.0 / (TILE_SIZE_PX * dLon)))
    }
    val dMercatorY = mercatorY(extent.latNorth) - mercatorY(extent.latSouth)
    if (dMercatorY > 0.0) {
        zoom = min(zoom, log2(budgetPx * 2.0 * PI / (TILE_SIZE_PX * dMercatorY)))
    }
    return zoom.coerceIn(MAP_MIN_ZOOM, MAP_MAX_ZOOM)
}

/** Web Mercator's latitude term, `ln(tan φ + sec φ)`, in the projection's own units. */
private fun mercatorY(latitudeDeg: Double): Double {
    val phi = Math.toRadians(latitudeDeg)
    return ln(tan(phi) + 1.0 / cos(phi))
}
