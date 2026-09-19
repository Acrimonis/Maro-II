
package ykws.android.maro.ui.map

import android.graphics.Bitmap
import android.graphics.Color
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.GroundOverlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.depth.DepthConstants
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.Isobath
import ykws.android.maro.data.model.Zone300Data
import ykws.android.maro.data.regulation.RegulatedZoneSet
import ykws.android.maro.data.regulation.RegulatedZoneType

/** Below this zoom the 300 m band is sub-pixel and meaningless; skip drawing it.
 *  Matches the map's default zoom (11) so the band is visible on launch. */
const val ZONE_MIN_ZOOM = 11.0

/** Minimum zoom level to draw regulated zone polygons (below this they'd be sub-pixel). */
const val REGULATED_ZONE_MIN_ZOOM = 10.0

/** Number of horizontal strips per depth raster overlay — see [addBandedOverlay]. ~8 ⇒ sub-metre. */
const val DEPTH_OVERLAY_BANDS = 8

/**
 * Transparency percentage → paint alpha, shared by the 300 m band and the regulated zones.
 * 0 = opaque (255), 100 = invisible (0). Mirrors the historical 300 m band maths, which truncates
 * on integer division, so not every alpha has a percentage that reproduces it.
 */
fun transparencyPctToAlpha(pct: Int): Int = ((100 - pct.coerceIn(0, 100)) * 255) / 100

/**
 * The Compose sibling of [transparencyPctToAlpha]: the same transparency percentage as the alpha
 * fraction a canvas draw needs. The overlays drawn with `drawLine`/`drawPath` take a fraction rather
 * than a packed alpha, and sitting here keeps the one percentage semantics — 0 = opaque, 100 =
 * invisible — in one place instead of a second derivation beside each stroke.
 */
fun transparencyPctToAlphaFraction(pct: Int): Float = (100 - pct.coerceIn(0, 100)) / 100f

/** Map each [RegulatedZoneType] to its distinct polygon hue. */
fun regulatedZoneColor(type: RegulatedZoneType): Int = when (type) {
    // Palette ARGB values; only their RGB part reaches the map — the fill and outline alphas come
    // from the user's transparency pair, so a packed alpha here (a `#CC` prefix) is not used.
    RegulatedZoneType.SPEED_LIMIT           -> AppConfig.regulatedZoneTypeSpeedLimit             // Blue
    RegulatedZoneType.ANCHORING_PROHIBITED  -> AppConfig.regulatedZoneTypeAnchoringProhibited     // Amber
    RegulatedZoneType.ACCESS_PROHIBITED     -> AppConfig.regulatedZoneTypeAccessProhibited        // Red
    RegulatedZoneType.ENVIRONMENTAL         -> AppConfig.regulatedZoneTypeEnvironmental           // Green
    RegulatedZoneType.MOORING               -> AppConfig.regulatedZoneTypeMooring                 // Teal
    RegulatedZoneType.FISHING_PROHIBITED    -> AppConfig.regulatedZoneTypeFishingProhibited       // Yellow
    RegulatedZoneType.NAVIGATION_RESTRICTION -> AppConfig.regulatedZoneTypeNavigationRestriction  // Purple
    RegulatedZoneType.OTHER                 -> AppConfig.regulatedZoneTypeOther                   // Blue Grey
}

/**
 * Draws the coastline segments on the OSMdroid [MapView].
 *
 * Plain segments take [mainlandColor] or [islandColor] by [CoastlineSegment.isMainland], at
 * [widthPx] and the alpha [transparencyPctToAlpha] derives from [transparencyPct] — the appearance
 * arrives as parameters rather than being read here, so the map view can key its rebuild on it.
 * Hazards keep their baked disc, ring and cross strokes.
 *
 * A segment is treated as a hazard primarily via its explicit [CoastlineSegment.isHazard] flag
 * (set by [HazardRings.toSegment], persisted in the proto cache). As a fallback for pre-feature
 * cached/bundled data that predates the flag, a closed island with no real OSM way id
 * (`!isMainland && isClosed && osmWayId == 0L`) is also treated as a hazard — hazard rings always
 * carry `osmWayId = 0L`, genuine OSM islands have positive ids — so hazards still render correctly
 * without re-baking the bundled asset.
 */
fun drawCoastline(
    mapView: MapView,
    segments: List<CoastlineSegment>,
    mainlandColor: Int,
    islandColor: Int,
    widthPx: Float,
    transparencyPct: Int,
    sink: MutableList<Any>
) {
    sink.clear()
    val alpha = transparencyPctToAlpha(transparencyPct)
    for (segment in segments) {
        val points = segment.points
        if (points.size < 2) continue

        val osmPoints = points.map { GeoPoint(it.lat.toDouble(), it.lon.toDouble()) }

        // Explicit flag first (robust, incl. unnamed dangers); heuristic fallback for
        // pre-feature cached/bundled data that lacks the flag.
        val isHazard = segment.isHazard ||
            (!segment.isMainland && segment.isClosed && segment.osmWayId == 0L)

        if (isHazard) {
            // Isolated offshore danger → vivid filled yellow disc with a black outline,
            // plus a black cross spreading a little past the circle. Distinct from green
            // islands / blue mainland and the magenta low-depth overlay.
            val disc = Polygon().apply {
                setPoints(osmPoints)
                fillPaint.color = AppConfig.mapHazardDiscFill   // vivid yellow (#FFE800) — full circle
                fillPaint.isAntiAlias = true
                outlinePaint.color = AppConfig.mapHazardOutline  // black circle around it
                outlinePaint.strokeWidth = 6f
                outlinePaint.isAntiAlias = true
            }
            mapView.overlays.add(disc)
            sink.add(disc)
            val cLat = (osmPoints.minOf { it.latitude } + osmPoints.maxOf { it.latitude }) / 2.0
            val cLon = (osmPoints.minOf { it.longitude } + osmPoints.maxOf { it.longitude }) / 2.0
            // Outer black ring, concentric with the disc (~1.6× radius).
            val outerRing = Polyline().apply {
                setPoints(osmPoints.map {
                    GeoPoint(cLat + (it.latitude - cLat) * 1.6, cLon + (it.longitude - cLon) * 1.6)
                })
                outlinePaint.apply { color = Color.BLACK; strokeWidth = 5f; isAntiAlias = true }
            }
            mapView.overlays.add(outerRing)
            sink.add(outerRing)
            // Black cross centred on the marker, arms ~80% past the radius (just past the outer ring).
            val hLat = (osmPoints.maxOf { it.latitude } - osmPoints.minOf { it.latitude }) / 2.0 * 1.8
            val hLon = (osmPoints.maxOf { it.longitude } - osmPoints.minOf { it.longitude }) / 2.0 * 1.8
            val crossH = Polyline().apply {
                setPoints(listOf(GeoPoint(cLat, cLon - hLon), GeoPoint(cLat, cLon + hLon)))
                outlinePaint.apply { color = Color.BLACK; strokeWidth = 5f; isAntiAlias = true }
            }
            mapView.overlays.add(crossH)
            sink.add(crossH)
            val crossV = Polyline().apply {
                setPoints(listOf(GeoPoint(cLat - hLat, cLon), GeoPoint(cLat + hLat, cLon)))
                outlinePaint.apply { color = Color.BLACK; strokeWidth = 5f; isAntiAlias = true }
            }
            mapView.overlays.add(crossV)
            sink.add(crossV)
            continue
        }

        val polyline = Polyline().apply {
            setPoints(osmPoints)
            outlinePaint.apply {
                color = if (segment.isMainland) mainlandColor else islandColor
                strokeWidth = widthPx
                this.alpha = alpha
                isAntiAlias = true
            }
        }
        mapView.overlays.add(polyline)
        sink.add(polyline)
    }
}

/**
 * Draws the precomputed 300 m band: translucent red fill (water only, island land
 * cut out as holes) plus the red seaward boundary line, stroked at [boundaryWidthPx].
 * Zoom-gated — nothing is drawn below [ZONE_MIN_ZOOM] (the band would be sub-pixel)
 * or before the band has been built ([zone] == null).
 *
 * Must be drawn **before** [drawCoastline] so the coastline reads on top of the fill.
 */
fun drawZone300(
    mapView: MapView,
    zone: Zone300Data?,
    zoomLevel: Double,
    zoneColor: Int,
    fillTransparencyPct: Int,
    boundaryTransparencyPct: Int,
    boundaryWidthPx: Float,
    sink: MutableList<Any>
) {
    sink.clear()
    if (zone == null || zoomLevel < ZONE_MIN_ZOOM) return

    val fillAlpha = transparencyPctToAlpha(fillTransparencyPct)
    val boundaryAlpha = transparencyPctToAlpha(boundaryTransparencyPct)

    // Fill (water only) — translucent red, no outline on the polygon itself.
    for (poly in zone.fillPolygons) {
        if (poly.outer.size < 3) continue
        val fill = Polygon().apply {
            setPoints(poly.outer.map { GeoPoint(it.latitude, it.longitude) })
            val validHoles = poly.holes.filter { it.size >= 3 }
            if (validHoles.isNotEmpty()) {
                setHoles(validHoles.map { hole -> hole.map { GeoPoint(it.latitude, it.longitude) } })
            }
            fillPaint.color = Color.argb(fillAlpha, Color.red(zoneColor), Color.green(zoneColor), Color.blue(zoneColor))
            outlinePaint.color = Color.TRANSPARENT
            outlinePaint.strokeWidth = 0f
        }
        mapView.overlays.add(fill)
        sink.add(fill)
    }

    // Red seaward boundary line (above the fill).
    for (line in zone.seawardLines) {
        if (line.size < 2) continue
        val redLine = Polyline().apply {
            setPoints(line.map { GeoPoint(it.latitude, it.longitude) })
            outlinePaint.apply {
                color = Color.argb(boundaryAlpha, Color.red(zoneColor), Color.green(zoneColor), Color.blue(zoneColor))
                strokeWidth = boundaryWidthPx
                isAntiAlias = true
            }
        }
        mapView.overlays.add(redLine)
        sink.add(redLine)
    }
}

/**
 * Draws regulated zones as translucent filled polygons with coloured outlines, one per
 * [RegulatedZone] in the set. Each [RegulatedZoneType] gets a distinct hue (see
 * [regulatedZoneColor]); the fill and outline alphas come from the user's transparency pair
 * (0 = opaque, 100 = invisible). Polygon holes (island interiors) are supported.
 *
 * Zoom-gated below [REGULATED_ZONE_MIN_ZOOM] and skipped when [zones] is null.
 *
 * Drawn between isobaths and the 300 m band (see [CoastlineMapView] factory / update).
 */
fun drawRegulatedZones(
    mapView: MapView,
    zones: RegulatedZoneSet?,
    zoomLevel: Double,
    fillTransparencyPct: Int,
    boundaryTransparencyPct: Int,
    outlineWidthPx: Float,
    sink: MutableList<Polygon>
) {
    sink.clear()
    if (zones == null || zoomLevel < REGULATED_ZONE_MIN_ZOOM) return
    val fillAlpha = transparencyPctToAlpha(fillTransparencyPct)
    val boundaryAlpha = transparencyPctToAlpha(boundaryTransparencyPct)
    for (zone in zones.zones) {
        if (zone.outerRing.size < 3) continue

        val color = regulatedZoneColor(zone.zoneType)
        val fill = Polygon().apply {
            setPoints(zone.outerRing.map { GeoPoint(it.latitude, it.longitude) })
            val validHoles = zone.holes.filter { it.size >= 3 }
            if (validHoles.isNotEmpty()) {
                setHoles(validHoles.map { hole -> hole.map { GeoPoint(it.latitude, it.longitude) } })
            }
            fillPaint.color = Color.argb(fillAlpha, Color.red(color), Color.green(color), Color.blue(color))
            outlinePaint.color = color
            outlinePaint.strokeWidth = outlineWidthPx
            outlinePaint.alpha = boundaryAlpha
            outlinePaint.isAntiAlias = true
        }
        mapView.overlays.add(fill)
        sink.add(fill)
    }
}

/**
 * Adds [bitmap] as [bands] stacked horizontal `GroundOverlay` strips instead of one, each pinned
 * at its own true latitudes. osmdroid stretches every overlay linearly in Web-Mercator, but the
 * grid's rows are equal *latitude* steps — which are NOT equal Mercator steps — so one full-height
 * overlay bows by ~tens of metres mid-grid. Splitting resets that error to zero at every strip
 * edge; it then falls with the square of the strip height (~8 bands ⇒ sub-metre). Longitude is
 * already linear in Mercator, so only latitude is split. Adjacent strips share pixel-row AND
 * latitude boundaries, so they tile exactly — no seam, no gap.
 */
fun addBandedOverlay(
    mapView: MapView,
    bitmap: Bitmap,
    box: BoundingBox,
    bands: Int,
    sink: MutableList<GroundOverlay>
) {
    val w = bitmap.width
    val h = bitmap.height
    val n = bands.coerceIn(1, h)
    val latSpan = box.latNorth - box.latSouth
    for (i in 0 until n) {
        val y0 = (i.toLong() * h / n).toInt()
        val y1 = ((i + 1).toLong() * h / n).toInt()
        val sliceH = y1 - y0
        if (sliceH <= 0) continue
        // Bitmap row 0 = north; latitude is linear in pixel row, so split proportionally.
        val latNorthStrip = box.latNorth - latSpan * (y0.toDouble() / h)
        val latSouthStrip = box.latNorth - latSpan * (y1.toDouble() / h)
        val overlay = GroundOverlay().apply {
            setImage(Bitmap.createBitmap(bitmap, 0, y0, w, sliceH))
            setPosition(
                GeoPoint(latNorthStrip, box.lonWest),
                GeoPoint(latSouthStrip, box.lonEast)
            )
        }
        mapView.overlays.add(overlay)
        sink.add(overlay)
    }
}

/**
 * Draws the hypsometric depth colour map as a stack of [DEPTH_OVERLAY_BANDS] `GroundOverlay` strips
 * (see [addBandedOverlay] for why it is banded). Zoom-gated below
 * [DepthConstants.DEPTH_MAP_MIN_DRAW_ZOOM] and skipped until the bitmap is built ([bitmap] == null).
 * The bitmap carries per-pixel alpha (NaN cells transparent), so it draws at full overlay opacity.
 *
 * Added FIRST so the isobaths, 300 m band and coastline read on top.
 */
fun drawDepthMap(
    mapView: MapView,
    bitmap: Bitmap?,
    box: BoundingBox?,
    zoomLevel: Double,
    sink: MutableList<GroundOverlay>
) {
    sink.clear()
    if (bitmap == null || box == null || zoomLevel < DepthConstants.DEPTH_MAP_MIN_DRAW_ZOOM) return
    addBandedOverlay(mapView, bitmap, box, DEPTH_OVERLAY_BANDS, sink)
}

/**
 * Draws the low-depth warning raster as banded `GroundOverlay` strips (see [addBandedOverlay])
 * directly above the depth colour map but below the isobaths, 300 m band and coastline. Same zoom
 * gate as [drawDepthMap]; the bitmap is transparent except the shallow cells, so it only tints
 * genuine grounding hazards.
 */
fun drawLowDepthWarning(
    mapView: MapView,
    bitmap: Bitmap?,
    box: BoundingBox?,
    zoomLevel: Double,
    sink: MutableList<GroundOverlay>
) {
    sink.clear()
    if (bitmap == null || box == null || zoomLevel < DepthConstants.DEPTH_MAP_MIN_DRAW_ZOOM) return
    addBandedOverlay(mapView, bitmap, box, DEPTH_OVERLAY_BANDS, sink)
}

/**
 * Draws depth contour [isobaths] as polylines, above the colour map but below the 300 m band
 * and coastline. Zoom-gated: nothing below [DepthConstants.ISOBATH_MIN_DRAW_ZOOM]; the dense
 * 2 m contour appears only at [DepthConstants.SHALLOW_ISOBATH_MIN_ZOOM]+. "Round" contours
 * (10/20/30…m) read slightly bolder than the in-between lines.
 */
fun drawIsobaths(
    mapView: MapView,
    isobaths: List<Isobath>,
    zoomLevel: Double,
    sink: MutableList<Polyline>
) {
    sink.clear()
    if (zoomLevel < DepthConstants.ISOBATH_MIN_DRAW_ZOOM) return
    for (iso in isobaths) {
        if (iso.depthM <= 2f && zoomLevel < DepthConstants.SHALLOW_ISOBATH_MIN_ZOOM) continue
        val isMajor = iso.depthM.toInt() % 10 == 0
        for (line in iso.lines) {
            if (line.points.size < 2) continue
            val poly = Polyline().apply {
                setPoints(line.points.map { GeoPoint(it.latitude, it.longitude) })
                outlinePaint.apply {
                    color = AppConfig.isobarColor(line.source)   // colour by data source
                    strokeWidth = ((if (isMajor) 3f else 2f) + AppConfig.isobarWidthBonus(line.source)).coerceAtLeast(1f)
                    alpha = if (isMajor) 180 else 120
                    isAntiAlias = true
                    // Dash genuinely low-confidence fill (GEBCO/interpolated) → reads as "approximate".
                    pathEffect = if (line.confidence <= DepthConstants.ISOBATH_LOWCONF_DASH_MAX)
                        android.graphics.DashPathEffect(floatArrayOf(8f, 6f), 0f) else null
                }
            }
            mapView.overlays.add(poly)
            sink.add(poly)
        }
    }
}
