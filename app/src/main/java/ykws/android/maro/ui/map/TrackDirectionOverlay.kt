package ykws.android.maro.ui.map

import android.graphics.Canvas
import android.graphics.Paint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.track.PointType
import ykws.android.maro.data.track.TrackPoint
import ykws.android.maro.data.track.deriveSpeedMps
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/** Direction-arrow density mode. */
enum class TrackDirectionDensity { UNIFORM, SPEED }

/** Screen-space point used by the pure sampler (avoids android.graphics in unit tests). */
internal data class ScreenPt(val x: Float, val y: Float)

/** A direction arrow anchor: position on segment [segmentIndex] at fraction [t], oriented by [bearingDeg]. */
internal data class ArrowAnchor(val segmentIndex: Int, val t: Float, val bearingDeg: Float)

/** The ui/map package's single metres-per-second → knots factor, shared by this path and the heatmap. */
internal const val KNOTS_PER_MPS = 1.94384f

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
 * The core a chevron's metrics are read from: [coreWidth] itself at or below [knee], and
 * `knee + (core − knee) × temper` above it — the excess over the knee scaled by the factor, so a class
 * that is wider than the knee draws arrows proportional to something nearer the thin end instead of
 * to its own full width. A factor of 1 is the identity, 0 pins every wide class to the knee.
 *
 * Only the chevron's three multiples read this: the chevron's own casing offset joined the raw-core
 * side instead, reading the line's physical width, because the rim belongs to the selection rather
 * than to the stroke it edges.
 */
internal fun temperedCore(coreWidth: Float, knee: Float, temper: Float): Float =
    if (coreWidth <= knee) coreWidth else knee + (coreWidth - knee) * temper

/**
 * The chevron's length for a core of [coreWidth]: 2.5 × the core, capped at 2.5 × [ceilingWidth], the
 * widest width the stored table can hand a track ([widestStoredTrackWidth]). [coreWidth] is the
 * *tempered* core ([temperedCore]) on every draw path, so the cap is compared against the same value
 * the multiples are read from.
 *
 * The cap replaces the fixed 12–24 px window that flattened every class whose core exceeded 9.6 px —
 * two of the six widths the file ships — so it can no longer bite inside the table while a retuned
 * file still cannot invert the chevrons against the line they sit on.
 */
internal fun chevronLength(coreWidth: Float, ceilingWidth: Float): Float =
    (coreWidth * 2.5f).coerceAtMost(ceilingWidth * 2.5f)

/** The chevron's half-width: 0.6 × its length. */
internal fun chevronHalfWidth(chevronLength: Float): Float = chevronLength * 0.6f

/** The coloured chevron's stroke: 0.5 × the core, never thinner than the 2 px floor it always had. */
internal fun chevronStrokeWidth(coreWidth: Float): Float = (coreWidth * 0.5f).coerceAtLeast(2f)

/**
 * How far the dark casing chevron sits outside the coloured V: half the line's casing over the line's
 * own width — `(casingWidth − coreWidth) / 2` — which is the rim the line itself wears under the very
 * same rule.
 *
 * [coreWidth] is the line's own width (`TrackPolylineAppearance.strokeWidth`), never the *tempered*
 * core the coloured chevron's length and stroke are read from ([temperedCore]): the rim belongs to the
 * selection and not to the stroke it edges, so the line and its arrowheads wear one weight and the
 * offset is the very `(casing − core) / 2` the line's own casing uses. At the file's 22-over-14 pair
 * that is 4 px, standing the dark band's inner edge 1 px clear of the coloured centreline — the coloured
 * stroke is 6 px wide there, read from the tempered 12 px core — where a rim taken from that tempered
 * core would be 5 px and clear it by 2. The coloured stroke itself is untouched by the reference: it
 * stays the tempered core's own half, 6 px.
 *
 * Floored at zero, so a file that sets the casing no wider than the line draws the dark exactly under
 * the coloured V rather than turning the two strokes inside out. A wider key is followed rather than
 * clamped: while `casingWidth − coreWidth` stays under the coloured stroke's own width the dark band's
 * inner edge sits inside that stroke and is covered by it, at that width it reaches the stroke's outer
 * edge, and past it the rim leaves the stroke behind and a gap opens between rim and core.
 */
internal fun chevronCasingOffset(casingWidth: Float, coreWidth: Float): Float =
    ((casingWidth - coreWidth) * 0.5f).coerceAtLeast(0f)

/** A chevron's three points in its own frame: the apex ahead along the bearing (−y), two tips behind. */
internal data class ChevronV(val apex: ScreenPt, val leftTip: ScreenPt, val rightTip: ScreenPt)

/**
 * A hair of the dark arm carried past the coloured tip, so the two round caps overlap rather than
 * meeting on a tangent line that anti-aliasing would show as a seam at the arrow's outer corner.
 */
internal const val CHEVRON_CAP_OVERLAP_PX = 0.5f

/**
 * The chevron drawn at a direction anchor, in the anchor's own frame: the apex ahead along the bearing
 * at −y, both tips trailing behind, for a core of [chevronLength] and [halfWidth].
 *
 * [offset] pushes both arms out along their own normals, and everything follows from that one number:
 * zero gives the coloured V itself, the rim thickness gives the dark casing V, and in between the arms
 * stay parallel to the coloured ones at exactly [offset] outside them — their intersection, which is
 * the dark apex, moving `offset / sin(halfAngle)` further along the bearing, where `sin(halfAngle)` is
 * the arm's half-width over its length.
 *
 * Each tip is the coloured tip pushed out by [offset] and then a further [capOverlap] along its own
 * arm, so the dark rim wraps the coloured corners to their ends instead of stopping short of them, the
 * hair at the cap being what keeps anti-aliasing from drawing a seam where the two caps meet. Both
 * strokes are drawn at the same width, so the dark band runs from the coloured centreline outward and
 * the rim shows outside the V alone. Every point of the dark V's path therefore lies outside the
 * coloured V, the two overlapping along the coloured stroke's outer edge down each arm — but at the
 * vertex the shipped offset has outgrown the coloured cap: the dark join's round cap sits
 * `apex shift − coloured stroke`, ~1.8 px, ahead of the coloured apex's own cap at the file's
 * 22-over-14 pair, and the band's inner-edge apex reaches ~1.8 px ahead of the coloured apex against
 * that cap's 3 px reach, so the dark vertex stands in front of the coloured tip with a hair of
 * background between them rather than tucked under it. That is the geometry the pair now draws, not an
 * artefact of the pass: the vertex reads as a sliver at the shipped pair now that the offset reads the
 * line's width, and the casing pass drawing first is what keeps the rest of the join — the overlap down
 * each arm — under the coloured stroke. No clipping, no path operation, nothing to mask.
 */
internal fun chevronV(
    offset: Float,
    chevronLength: Float,
    halfWidth: Float,
    capOverlap: Float = 0f
): ChevronV {
    if (chevronLength <= 0f || halfWidth <= 0f) {
        return ChevronV(ScreenPt(0f, 0f), ScreenPt(0f, 0f), ScreenPt(0f, 0f))
    }
    val out = offset.coerceAtLeast(0f)
    val armLength = sqrt(chevronLength * chevronLength + halfWidth * halfWidth)
    // Along the bearing: the two arms pushed out by `out` meet `out / sin(halfAngle)` past the
    // coloured apex, that being where the dark vertex has always landed.
    val apexShift = out * armLength / halfWidth
    // Along each arm's own outward normal, (−length, −halfWidth) / armLength for the left arm and its
    // mirror for the right, plus the hair further along the arm itself, (±halfWidth, length) / armLength.
    val outX = out * chevronLength / armLength
    val outY = out * halfWidth / armLength
    // No rim means nothing to wrap, so the hair only applies once the arms are actually offset.
    val tail = if (out > 0f) capOverlap else 0f
    val tailX = tail * halfWidth / armLength
    val tailY = tail * chevronLength / armLength
    return ChevronV(
        apex = ScreenPt(0f, -chevronLength - apexShift),
        leftTip = ScreenPt(-halfWidth - outX - tailX, -outY + tailY),
        rightTip = ScreenPt(halfWidth + outX + tailX, -outY + tailY)
    )
}

/**
 * One overlay per rendered track, drawing direction chevrons along the polyline
 * in a single pass. Re-samples anchors when the integer zoom level changes.
 */
internal class TrackDirectionOverlay(
    points: List<TrackPoint>,
    private val appearances: List<TrackPolylineAppearance>,
    private val spacingPx: (speedKn: Float) -> Float,
    private val maxArrows: Int = 2000,
    /**
     * Per-anchor colour resolver. Null (the default) preserves today's iteration — every anchor is
     * painted once per appearance — so no other track changes behaviour. Non-null draws one chevron
     * per anchor in that anchor's own band.
     */
    private val colorResolver: ((ArrowAnchor) -> TrackPolylineAppearance)? = null,
    /**
     * The appearance the chevron's metrics — its core width, hence its length and stroke — are read
     * from on the resolver path. Passed in rather than inferred from the colour the resolver returns.
     */
    private val chevronMetrics: TrackPolylineAppearance? = null,
    /**
     * The selected track's casing: the dark under-shape drawn once beneath every chevron on the
     * resolver path, or null (the default) for none. Its colour is the dark the chevrons are painted
     * in, and its width, read against the line's own width, is what sets how far out the dark V sits
     * (see [chevronCasingOffset] and [chevronV]). A caller that passes none gets exactly the chevrons
     * this overlay drew before the casing existed.
     */
    private val casingAppearance: TrackPolylineAppearance? = null,
    /**
     * The widest width the stored table can hand a track ([widestStoredTrackWidth]), the reference
     * the chevron length's ceiling is taken from: 2.5 × it can never bite inside the table, so no
     * class is flattened by its own core while a retuned file cannot invert the chevrons against the
     * line either. Read from `AppConfig` at construction, where the table is settled.
     */
    private val chevronCeilingWidth: Float = widestStoredTrackWidth(),
    /**
     * The tempering the three multiples read their core through ([temperedCore]): at or below the knee
     * a core is used as it is, above it the excess is scaled by the factor, so the selected track's
     * chevrons stop reading oversized while the newest, pinned and oldest classes are untouched. Read
     * from `AppConfig` at construction, where the shipped file is already parsed.
     */
    private val chevronScaleKnee: Float = AppConfig.trackArrowScaleKnee,
    private val chevronTemper: Float = AppConfig.trackArrowTemper
) : Overlay() {

    /** Identifier used by the track overlay effect for cleanup and z-order. */
    var title: String = ""

    // Spacing keeps its historic behaviour: an underivable speed reads as zero *here*, while the
    // heatmap's colour answers the ramp's neutral tint for the very same point.
    private val points: List<TrackPoint> = if (points.any { it.speedMps == null }) {
        points.mapIndexed { i, p ->
            p.speedMps?.let { p } ?: p.copy(speedMps = deriveSpeedMps(points, i) ?: 0f)
        }
    } else points

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
    /** The tempered core the chevron's length, coloured stroke and half-width are read from. */
    private fun chevronCore(coreWidth: Float): Float =
        temperedCore(coreWidth, chevronScaleKnee, chevronTemper)

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

        val resolver = colorResolver
        if (resolver != null) {
            drawResolvedChevrons(c, osmv, anchors, resolver)
            return
        }

        for (appearance in appearances) {
            val core = chevronCore(appearance.strokeWidth)
            val chevronLen = chevronLength(core, chevronCeilingWidth)
            val halfW = chevronHalfWidth(chevronLen)
            paint.color = appearance.argb
            paint.strokeWidth = chevronStrokeWidth(core)
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

    /**
     * Resolver path (heatmap mode): one chevron per anchor, coloured by that anchor's own band.
     * Metrics come from [chevronMetrics] — passed in, never inferred — and the anchor sampling that
     * placed these chevrons is untouched.
     */
    private fun drawResolvedChevrons(
        c: Canvas,
        osmv: MapView,
        anchors: List<ArrowAnchor>,
        resolver: (ArrowAnchor) -> TrackPolylineAppearance
    ) {
        val metrics = chevronMetrics ?: appearances.firstOrNull() ?: return
        val core = chevronCore(metrics.strokeWidth)
        val chevronLen = chevronLength(core, chevronCeilingWidth)
        val halfW = chevronHalfWidth(chevronLen)
        val strokeWidth = chevronStrokeWidth(core)
        val colouredV = chevronV(0f, chevronLen, halfW)
        // The casing, when the caller passed one, is drawn here and only here: one pass over every
        // chevron before the coloured pass, so the dark sits beneath them all rather than under each
        // in turn. It is the *same V* shifted outward by the line's rim — half the line's casing over
        // the line's own width — and drawn at the coloured stroke's own width,
        // not as a thicker stroke of that V:
        // a thicker stroke shows the rim on both sides of every arm, inside the notch as well as
        // outside it, while a shifted V shows it outside alone, the coloured V covering the overlap
        // along the inner edge. Both dark arms stay collinear with the coloured ones, the dark vertex
        // landing where the two shifted arms meet, and each dark tip wraps round the coloured tip with
        // a hair of overlap so the rim reaches the arrow's outer corners instead of stopping short of
        // them — which keeps the dark join out of the coloured notch, bar the vertex the KDoc of
        // [chevronV] records, where the shipped offset stands the dark apex clear ahead of the coloured
        // tip rather than tucking it under.
        val casing = casingAppearance
        if (casing != null) {
            // The offset is read from the line's own width, not the tempered core the coloured V's
            // metrics came from: the rim belongs to the selection, so the line and its arrowheads wear
            // one weight.
            val offset = chevronCasingOffset(casing.strokeWidth, metrics.strokeWidth)
            val darkV = chevronV(offset, chevronLen, halfW, CHEVRON_CAP_OVERLAP_PX)
            forEachVisibleChevron(c, osmv, anchors) { _, x, y, bearing ->
                drawChevron(c, x, y, bearing, darkV, strokeWidth, casing.argb)
            }
        }
        forEachVisibleChevron(c, osmv, anchors) { anchor, x, y, bearing ->
            drawChevron(c, x, y, bearing, colouredV, strokeWidth, resolver(anchor).argb)
        }
    }

    /**
     * Walks [anchors] in projected screen space and hands each on-screen chevron's anchor, position
     * and bearing to [drawAt], skipping the ones outside the view. The casing pass and the coloured
     * pass share this walk rather than each repeating the projection, and it allocates nothing: the
     * projection reuses the same GeoPoints and Point the iteration loop has always used.
     */
    private inline fun forEachVisibleChevron(
        c: Canvas,
        osmv: MapView,
        anchors: List<ArrowAnchor>,
        drawAt: (anchor: ArrowAnchor, x: Float, y: Float, bearingDeg: Float) -> Unit
    ) {
        val projection = osmv.projection
        val viewW = c.width.toFloat()
        val viewH = c.height.toFloat()
        val margin = 48f

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

            drawAt(anchor, x, y, anchor.bearingDeg)
        }
    }

    /**
     * One chevron: two strokes meeting at the anchor, the whole V rotated onto its bearing. [v] is in
     * the anchor's own frame — the plain V for the coloured chevron, the shifted one for the casing —
     * so both arms follow from the geometry [chevronV] settled rather than from anything read here.
     */
    private fun drawChevron(
        c: Canvas,
        x: Float,
        y: Float,
        bearingDeg: Float,
        v: ChevronV,
        strokeWidth: Float,
        argb: Int
    ) {
        paint.color = argb
        paint.strokeWidth = strokeWidth
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        c.save()
        c.rotate(bearingDeg, x, y)
        c.drawLine(x + v.apex.x, y + v.apex.y, x + v.leftTip.x, y + v.leftTip.y, paint)
        c.drawLine(x + v.apex.x, y + v.apex.y, x + v.rightTip.x, y + v.rightTip.y, paint)
        c.restore()
    }
}
