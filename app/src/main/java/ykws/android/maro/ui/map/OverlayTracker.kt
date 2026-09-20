
package ykws.android.maro.ui.map

import android.graphics.Bitmap
import org.osmdroid.views.overlay.GroundOverlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.Isobath
import ykws.android.maro.data.model.Zone300Data
import ykws.android.maro.data.regulation.RegulatedZoneSet

/**
 * Holds persistent references to all [MapView] overlays per layer, plus the
 * last-known data state for dirty checking. Used by [CoastlineMapView] to
 * rebuild only the layer whose data actually changed — no blanket removeAll.
 *
 * The zoom memory here is a **gate**, not a level: a layer draws the same geometry at every zoom
 * above its floor, so it is rebuilt when that gate crosses and not on every step. The floors
 * themselves live with the drawing code ([ZONE_MIN_ZOOM], [REGULATED_ZONE_MIN_ZOOM],
 * `DepthConstants`), never as a literal here.
 */
class OverlayTracker {
    val depth = mutableListOf<GroundOverlay>()
    val lowDepth = mutableListOf<GroundOverlay>()
    /** Every contour polyline attached, whatever the gates say — the caller switches visibility. */
    val isobaths = mutableListOf<Polyline>()
    /** The 2 m subset of [isobaths], the group the shallower zoom floor switches on its own. */
    val isobathsShallow = mutableListOf<Polyline>()
    val regulatedZones = mutableListOf<Polygon>()
    val zone300 = mutableListOf<Any>()
    val coastline = mutableListOf<Any>()

    var lastDepthBitmap: Bitmap? = null
    var lastDepthBox: BoundingBox? = null
    var lastDepthDraws: Boolean = false

    var lastLowDepthBitmap: Bitmap? = null
    var lastLowDepthDraws: Boolean = false

    var lastIsobaths: List<Isobath> = emptyList()

    var lastRegulatedZones: RegulatedZoneSet? = null
    var lastRegZoneDraws: Boolean = false
    var lastRegZoneFillTransparencyPct: Int = -1
    var lastRegZoneBoundaryTransparencyPct: Int = -1
    var lastRegZoneOutlineWidthPx: Float = -1f

    var lastZone300: Zone300Data? = null
    var lastZone300Draws: Boolean = false
    var lastZone300Color: Int = 0
    var lastZone300FillTransparencyPct: Int = -1
    var lastZone300BoundaryTransparencyPct: Int = -1
    var lastZone300BoundaryWidthPx: Float = -1f

    var lastSegments: List<CoastlineSegment> = emptyList()
    var lastCoastlineMainlandColor: Int = 0
    var lastCoastlineIslandColor: Int = 0
    var lastCoastlineWidthPx: Float = -1f
    var lastCoastlineTransparencyPct: Int = -1
}
