package ykws.android.maro.ui.map

import android.graphics.Canvas
import android.graphics.Paint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import ykws.android.maro.data.track.PointType
import ykws.android.maro.data.track.TrackPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.asin
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/** Direction-arrow density mode. */
enum class TrackDirectionDensity { UNIFORM, SPEED }

/** Screen-space point used by the pure sampler (avoids android.graphics in unit tests). */
internal data class ScreenPt(val x: Float, val y: Float)

/** A direction arrow anchor: position on segment [segmentIndex] at fraction [t], oriented by [bearingDeg]. */
internal data class ArrowAnchor(val segmentIndex: Int, val t: Float, val bearingDeg: Float)

private const val KNOTS_PER_MPS = 1.94384f

/**
 * On-screen spacing (px) between direction arrows for a given speed.
 * Clamped below [floorKn] and above [ceilingKn]; linear in between.
 */
internal fun spacingPxForSpeed(
    speedKn: Float,
    floorKn: Float,
    ceilingKn: Float,
    minPx: Float,
    maxPx: Float
): Float {
    val lo = minOf(minPx, maxPx)
    val hi = maxOf(minPx, maxPx)
    return when {
        speedKn <= floorKn -> lo
        speedKn >= ceilingKn -> hi
        else -> {
            val t = ((speedKn - floorKn) / (ceilingKn - floorKn)).coerceIn(0f, 1f)
            val ratio = if (lo > 0f && hi > lo) hi / lo else 1f
            (lo * ratio.pow(t)).coerceIn(lo, hi)
        }
    }
}

/** Map a log-scale slider position (0..1) to a value in [min, max]. */
internal fun logSliderToValue(position: Float, min: Float, max: Float): Float =
    (min * (max / min).pow(position.coerceIn(0f, 1f))).coerceIn(min, max)

/** Map a value in [min, max] to a log-scale slider position (0..1). */
internal fun logSliderFromValue(value: Float, min: Float, max: Float): Float =
    (ln(value.coerceIn(min, max) / min) / ln(max / min)).coerceIn(0f, 1f)

/** Initial great-circle bearing (0-360°) from (lat1, lon1) to (lat2, lon2). */
internal fun initialBearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dLon = Math.toRadians(lon2 - lon1)
    val y = sin(dLon) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
    return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
}

/**
 * Walks the track polyline in projected screen space and returns arrow anchors
 * spaced [spacingPx] apart (which may vary with speed). GAP segments are skipped.
 */
internal fun sampleArrowAnchors(
    points: List<TrackPoint>,
    project: (TrackPoint) -> ScreenPt,
    spacingPx: (speedKn: Float) -> Float,
    maxArrows: Int
): List<ArrowAnchor> {
    if (points.size < 2 || maxArrows <= 0) return emptyList()
    val anchors = ArrayList<ArrowAnchor>(maxArrows)
    var distanceToNext = spacingPx(speedKn(points[0]))
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        if (a.type == PointType.GAP || b.type == PointType.GAP) {
            distanceToNext = spacingPx(speedKn(b))
            continue
        }
        val pa = project(a)
        val pb = project(b)
        val dx = pb.x - pa.x
        val dy = pb.y - pa.y
        val segLen = sqrt(dx * dx + dy * dy)
        if (segLen < 0.001f) continue
        var consumed = 0f
        while (consumed + distanceToNext <= segLen) {
            consumed += distanceToNext
            val t = consumed / segLen
            val bearing = a.bearingDeg ?: initialBearingDeg(a.lat, a.lon, b.lat, b.lon)
            anchors.add(ArrowAnchor(i, t, bearing))
            if (anchors.size >= maxArrows) return anchors
            distanceToNext = spacingPx(interpolatedSpeedKn(a, b, t))
        }
        distanceToNext -= (segLen - consumed)
    }
    return anchors
}

private fun speedKn(p: TrackPoint): Float = (p.speedMps ?: 0f) * KNOTS_PER_MPS

private fun interpolatedSpeedKn(a: TrackPoint, b: TrackPoint, t: Float): Float {
    val sa = a.speedMps ?: 0f
    val sb = b.speedMps ?: 0f
    return (sa + (sb - sa) * t) * KNOTS_PER_MPS
}

/**
 * One overlay per rendered track, drawing direction chevrons along the polyline
 * in a single pass. Re-samples anchors when the integer zoom level changes.
 */
internal class TrackDirectionOverlay(
    points: List<TrackPoint>,
    private val appearances: List<TrackPolylineAppearance>,
    private val spacingPx: (speedKn: Float) -> Float,
    private val maxArrows: Int = 2000
) : Overlay() {

    /** Identifier used by the track overlay effect for cleanup and z-order. */
    var title: String = ""

    private val points: List<TrackPoint> = fillMissingSpeed(points)

    init {
        if (this.points.isNotEmpty()) {
            var minLat = this.points[0].lat
            var maxLat = this.points[0].lat
            var minLon = this.points[0].lon
            var maxLon = this.points[0].lon
            for (p in this.points) {
                if (p.lat < minLat) minLat = p.lat
                if (p.lat > maxLat) maxLat = p.lat
                if (p.lon < minLon) minLon = p.lon
                if (p.lon > maxLon) maxLon = p.lon
            }
            mBounds = org.osmdroid.util.BoundingBox(maxLat, maxLon, minLat, minLon)
        }
    }

    private var anchors: List<ArrowAnchor> = emptyList()
    private var sampledZoomInt: Int = Int.MIN_VALUE
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val geoA = GeoPoint(0.0, 0.0)
    private val geoB = GeoPoint(0.0, 0.0)
    private val screen = android.graphics.Point()

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow || points.size < 2) return
        val projection = osmv.projection
        val zoomInt = osmv.zoomLevelDouble.toInt()
        if (zoomInt != sampledZoomInt) {
            sampledZoomInt = zoomInt
            anchors = sampleArrowAnchors(points, { p ->
                geoA.latitude = p.lat
                geoA.longitude = p.lon
                projection.toPixels(geoA, screen)
                ScreenPt(screen.x.toFloat(), screen.y.toFloat())
            }, spacingPx, maxArrows)
        }
        if (anchors.isEmpty()) return

        val viewW = c.width.toFloat()
        val viewH = c.height.toFloat()
        val margin = 48f

        for (appearance in appearances) {
            val chevronLen = (appearance.strokeWidth * 2.5f).coerceIn(12f, 24f)
            val halfW = chevronLen * 0.6f
            paint.color = appearance.argb
            paint.strokeWidth = (appearance.strokeWidth * 0.5f).coerceAtLeast(2f)
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND

            for (anchor in anchors) {
                val a = points[anchor.segmentIndex]
                val b = points[anchor.segmentIndex + 1]
                geoA.latitude = a.lat
                geoA.longitude = a.lon
                projection.toPixels(geoA, screen)
                val ax = screen.x.toFloat()
                val ay = screen.y.toFloat()
                geoB.latitude = b.lat
                geoB.longitude = b.lon
                projection.toPixels(geoB, screen)
                val bx = screen.x.toFloat()
                val by = screen.y.toFloat()
                val x = ax + (bx - ax) * anchor.t
                val y = ay + (by - ay) * anchor.t
                if (x < -margin || x > viewW + margin || y < -margin || y > viewH + margin) continue

                c.save()
                c.rotate(anchor.bearingDeg, x, y)
                c.drawLine(x, y - chevronLen, x - halfW, y, paint)
                c.drawLine(x, y - chevronLen, x + halfW, y, paint)
                c.restore()
            }
        }
    }
}

/** Fill null [TrackPoint.speedMps] with speed derived from time delta + haversine distance. */
private fun fillMissingSpeed(points: List<TrackPoint>): List<TrackPoint> {
    if (points.none { it.speedMps == null }) return points
    return points.mapIndexed { i, p ->
        if (p.speedMps != null) p else p.copy(speedMps = deriveSpeedMps(points, i))
    }
}

private fun deriveSpeedMps(points: List<TrackPoint>, i: Int): Float {
    val j = if (i + 1 < points.size) i + 1 else i - 1
    if (j < 0 || j >= points.size) return 0f
    val a = points[minOf(i, j)]
    val b = points[maxOf(i, j)]
    if (a.type == PointType.GAP || b.type == PointType.GAP) return 0f
    val dtSec = (b.timeOffsetMs - a.timeOffsetMs) / 1000.0
    if (dtSec <= 0.0) return 0f
    return (haversineM(a.lat, a.lon, b.lat, b.lon) / dtSec).toFloat()
}

private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * asin(sqrt(a))
}
