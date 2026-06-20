
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
    var lastLowDepthBitmap: Bitmap? = null
    var lastIsobaths: List<Isobath> = emptyList()
    var lastRegulatedZones: RegulatedZoneSet? = null
    var lastZone300: Zone300Data? = null
    var lastSegments: List<CoastlineSegment> = emptyList()
    var lastDepthBox: BoundingBox? = null
    var lastZoom: Double = -1.0
}
