package ykws.android.maro.ui.map

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
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
 * marker's extent its configured share of the smaller displayed dimension.
 *
 * Design: `xTrack/Markers/260918_FEAT_PLN_Markers_focus-zoom-fractions.md` §1.
 */
internal data class MarkerFocus(val centre: LatLng, val zoom: Double)

/**
 * The camera [marker] wants when its dashboard opens.
 *
 * The share is a **pixel budget on the smaller displayed dimension**: the geometry's larger projected
 * span is made to equal `share × min(width, height)`, so a shape stretched along the screen's longer
 * axis frames tighter than the bare number suggests. The centre is [UserMarker.centerPoint] — the map
 * centre itself, with no allowance for the dashboard's own footprint.
 *
 * A pin has no extent, so it frames a nominal footprint of [pinFootprintM] metres — the default zone's
 * diameter — and **never lowers the zoom**: a user already closer than that framing keeps their view.
 * Corridor and circle apply their computed zoom regardless, since seeing the whole extent is the point.
 *
 * @param viewportWidthPx  Map view width in pixels, as laid out.
 * @param viewportHeightPx Map view height in pixels, as laid out.
 * @param currentZoom      The map's live zoom; read only for the pin's zoom-in-only rule.
 * @param corridorShare    Corridor share, from `marker.focus.corridor_share`.
 * @param zoneShare        Circle share, from `marker.focus.zone_share`.
 * @param pinFootprintM    Pin footprint in metres, from `marker.focus.pin_footprint_m`.
 * @return null when the viewport is unmeasured — the caller then keeps its centre-only move.
 */
internal fun markerFocusTarget(
    marker: UserMarker,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    currentZoom: Double,
    corridorShare: Double,
    zoneShare: Double,
    pinFootprintM: Double
): MarkerFocus? {
    if (viewportWidthPx <= 0 || viewportHeightPx <= 0) return null

    val geometry = marker.geometry
    val extent = when (geometry) {
        is MarkerGeometry.Corridor -> UserMarker.bboxOf(geometry)
        is MarkerGeometry.Circle -> UserMarker.bboxOf(geometry)
        // A point borrows a circle's bbox, through the model's own metres-to-degrees conversion.
        is MarkerGeometry.Pin -> UserMarker.bboxOf(
            MarkerGeometry.Circle(geometry.position, (pinFootprintM / 2.0).coerceAtLeast(1.0))
        )
    }
    val share = if (geometry is MarkerGeometry.Corridor) corridorShare else zoneShare
    val budgetPx = share * min(viewportWidthPx, viewportHeightPx)
    val target = zoomForExtent(extent, budgetPx)
    val framed = if (geometry is MarkerGeometry.Pin) maxOf(target, currentZoom) else target
    return MarkerFocus(marker.centerPoint, framed)
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
