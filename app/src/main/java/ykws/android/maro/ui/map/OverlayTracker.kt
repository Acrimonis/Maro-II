
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
 */
class OverlayTracker {
    val depth = mutableListOf<GroundOverlay>()
    val lowDepth = mutableListOf<GroundOverlay>()
    val isobaths = mutableListOf<Polyline>()
    val regulatedZones = mutableListOf<Polygon>()
    val zone300 = mutableListOf<Any>()
    val coastline = mutableListOf<Any>()

    var lastDepthBitmap: Bitmap? = null
    var lastDepthBox: BoundingBox? = null
    var lastDepthZoom: Double = -1.0

    var lastLowDepthBitmap: Bitmap? = null
    var lastLowDepthZoom: Double = -1.0

    var lastIsobaths: List<Isobath> = emptyList()
    var lastIsobathZoom: Double = -1.0

    var lastRegulatedZones: RegulatedZoneSet? = null
    var lastRegZoneZoom: Double = -1.0

    var lastZone300: Zone300Data? = null
    var lastZone300Zoom: Double = -1.0
    var lastZone300Color: Int = 0
    var lastZone300FillOpacityPct: Int = -1
    var lastZone300BoundaryOpacityPct: Int = -1

    var lastSegments: List<CoastlineSegment> = emptyList()
}
