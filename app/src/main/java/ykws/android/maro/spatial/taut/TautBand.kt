package ykws.android.maro.spatial.taut

import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.spatial.SpatialOperations

/**
 * **The coastal band as a span source — the intervals of a leg that stand inside the 300 m strip.**
 *
 * The band is the one limit source that is a **distance rather than a ring**: `inCoastalBand` asks
 * `distanceToCoastM(p) ≤ coastalBandWidthM`, so its boundary is a locus and not a stored polygon. That
 * is why the splitter could cut every zone and still charge a straddling leg at one value — the shape the
 * per-leg midpoint rule was left standing on (§18.1).
 *
 * **What the world's distance actually measures decides the geometry, and it is the distance to the
 * nearest coastline *segment*** — a union of capsules around the segments, read in
 * [`ykws.android.maro.spatial.CoastlineSpatialIndex.query`] as `pointToSegmentDistance`. Segment
 * distance makes the leg's crossing **closed-form**, and this is it: `dist(p(t), segment) ≤ W` is the
 * union of three sets, each convex in the leg's parameter `t` — a **slab** between the two lines
 * parallel to the segment at `W` and the **two endpoint circles** — so each nearby segment contributes
 * **at most one interval**, and the intervals of a leg are the merged union of those.
 *
 * **It invents no tolerance.** The slab condition is affine in `t` (`|(p(t) − c) × e| ≤ W·|e|`), and
 * each circle is a quadratic in `t` with a positive leading coefficient, so both are solved rather than
 * searched: no march, no step, no sample.
 *
 * **The slab alone is not a subset of the true set, and the hull cannot hide that.** A point within `W`
 * of the segment's *infinite line* but beyond its end is not within `W` of the segment at all, so the
 * slab must first be **intersected with the range of `t` whose projection falls on the segment itself**;
 * only then are the three pieces subsets of the true set. With that intersection the remaining pieces —
 * the strip's own part and the two endpoint circles — union to the true set, which is convex because the
 * distance from a point to a convex set is, so the hull of the pieces is the set exactly. Without it the
 * hull of a superset is a superset: a leg parallel to a segment's line, within `W` of it and entirely
 * beyond its end, was reported inside the strip when none of it stands there.
 *
 * **The segments are the world's own coastline**, gathered once per corridor from `landPolylinesIn` over
 * a box grown by the band's width — not the harvested walls, which are abstracted and dilated, and not
 * the `2 m` contour, which is a different question. They are bucketed into the corridor's uniform grid,
 * so a leg asks only about the coast within reach of the cells it passes through, and each is solved for
 * the one interval it can contribute.
 */
internal class TautBand private constructor(
    /** The band's own width (m) — `coastalBandWidthM`, the value the world's predicate reads. */
    private val widthM: Double,
    private val segments: List<Pair<Pt, Pt>>,
    private val cells: HashMap<Long, MutableList<Int>>,
    private val minX: Double,
    private val minY: Double
) {

    /** How many coastline segments the band's own index holds — the price of the spans, in segments. */
    val segmentCount: Int get() = segments.size

    /**
     * **The intervals of the leg `a → b` inside the band**, merged and in order — the band's own shape of
     * [`TautZoneSet.limitSpans`], and exact for the same reason: nothing is sampled.
     *
     * A leg may enter the strip in more than one place — a headland is the everyday case — so the result
     * is a list and never a single span.
     */
    fun spansOf(a: Pt, b: Pt): List<Span> {
        if (segments.isEmpty()) return emptyList()
        val query = stamp.incrementAndGet()
        val legM = hypot(b.x - a.x, b.y - a.y)
        if (legM <= 0.0) return emptyList()
        val found = ArrayList<Span>(4)
        forEachGridCell(a, b, minX, minY) { cell, _, _ ->
            val bucket = cells[cell] ?: return@forEachGridCell
            for (index in bucket) {
                if (seen[index] == query) continue
                seen[index] = query
                val segment = segments[index]
                val span = spanWithin(a, b, segment.first, segment.second, widthM) ?: continue
                // **A touch is not an interval.** A leg grazing the width's own locus produces a
                // measure-zero span, and floating point turns that into a sliver of picometres — priced, it
                // would charge the band's limit for metres that do not exist. The floor is stated in
                // **metres** rather than in the leg's parameters, so a long leg's genuinely short stretch
                // survives it and only a contact is dropped.
                if ((span.to - span.from) * legM < MIN_SPAN_M) continue
                found.add(span)
            }
        }
        if (found.isEmpty()) return emptyList()
        found.sortBy { it.from }
        val out = ArrayList<Span>(found.size)
        for (span in found) {
            val last = out.lastOrNull()
            if (last != null && span.from <= last.to + BAND_EPS) {
                out[out.size - 1] = Span(last.from, max(last.to, span.to))
            } else {
                out.add(span)
            }
        }
        return out
    }

    private var seen = IntArray(segments.size)

    /**
     * **One stamp per query, taken atomically** — the same rule as the wall index's, and for the same
     * reason (§19.5 C1, `TautObstacles`' own note): the band rides in a kept terrain, so it is read by a
     * search and by the cancelled, unjoined one the engine no longer waits for. A stamp two queries could
     * share would let one of them read the other's marks as its own and **drop the segment whose span it
     * was about to add**, which shortens or splits the intervals the band charges. Unique stamps mean a
     * stale mark is never read as this query's own, and a mark overwritten by another query only costs a
     * repeated solve, whose span merges into the same union.
     */
    private val stamp = AtomicInteger(0)

    companion object {

        /**
         * The band's own source over [box], or **null when the band is switched off** — a width of zero
         * has no strip to stand in, so the splitter keeps its single piece and the crossing that cannot
         * exist costs nothing.
         *
         * [cancelCheck] is **asked per polyline**, so an abandoned corridor stops inside the coastline
         * scan rather than after it: the band is harvested with the terrain (§19.4), and a terrain that
         * could not be abandoned would hold the newest aim behind a whole strip build.
         */
        fun of(
            world: TautWorld,
            frame: Frame,
            box: BoundingBox,
            cancelCheck: () -> Unit = {}
        ): TautBand? {
            val width = world.coastalBandWidthM
            if (width <= 0.0) return null
            val polylines = world.landPolylinesIn(queryBox(box, width))
            if (polylines.isEmpty()) return null
            val boxM = BoxM(
                minX = frame.x(box.lonWest),
                maxX = frame.x(box.lonEast),
                minY = frame.y(box.latSouth),
                maxY = frame.y(box.latNorth)
            )
            val segments = ArrayList<Pair<Pt, Pt>>()
            var minX = Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            for (polyline in polylines) {
                cancelCheck()
                val points = frame.pts(polyline)
                for (i in 0 until points.size - 1) {
                    val a = points[i]
                    val b = points[i + 1]
                    if (a == b) continue
                    // Only the coast within the band's own reach of the corridor can be nearest to a leg
                    // that lies inside it — a shape further off is not part of any answer here.
                    val reach = BoxM.ofSegment(a, b).inflate(width)
                    if (!reach.overlaps(boxM)) continue
                    segments.add(a to b)
                    minX = min(minX, reach.minX)
                    minY = min(minY, reach.minY)
                }
            }
            if (segments.isEmpty()) return null
            val cells = HashMap<Long, MutableList<Int>>(segments.size * 8)
            for ((index, segment) in segments.withIndex()) {
                val reach = BoxM.ofSegment(segment.first, segment.second).inflate(width)
                val firstColumn = column(reach.minX, minX)
                val lastColumn = column(reach.maxX, minX)
                val firstRow = row(reach.minY, minY)
                val lastRow = row(reach.maxY, minY)
                for (r in firstRow..lastRow) {
                    for (c in firstColumn..lastColumn) {
                        cells.getOrPut(gridKey(r, c)) { ArrayList(2) }.add(index)
                    }
                }
            }
            return TautBand(width, segments, cells, minX, minY)
        }

        /**
         * **The box the coastline is asked for** — the corridor grown by the band's own width, on every
         * side.
         *
         * A leg inside the corridor may stand within `W` of a coastline that is itself outside the box,
         * and the band's answer has to be the world's own; the meridian factor is taken at the box's
         * furthest latitude so the growth is never short.
         */
        private fun queryBox(box: BoundingBox, widthM: Double): BoundingBox {
            val metresPerDegreeLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
            val latitude = max(abs(box.latSouth), abs(box.latNorth)).coerceAtMost(89.0)
            val metresPerDegreeLon = metresPerDegreeLat * cos(latitude * PI / 180.0)
            val marginLat = widthM / metresPerDegreeLat
            val marginLon = widthM / metresPerDegreeLon
            return BoundingBox(
                latSouth = box.latSouth - marginLat,
                latNorth = box.latNorth + marginLat,
                lonWest = box.lonWest - marginLon,
                lonEast = box.lonEast + marginLon
            )
        }

        private fun row(y: Double, minY: Double): Int = gridIndex(y, minY)
        private fun column(x: Double, minX: Double): Int = gridIndex(x, minX)

        /**
         * **The interval of the leg `a → b` whose points stand within [widthM] of the segment `c → d`**,
         * or null when none of them do.
         *
         * Three pieces, each a subset of the true set and their union the whole of it: the **strip**, the
         * slab's interval **intersected with the range of `t` whose projection falls on the segment** —
         * the intersection the plan's own step named, and the one whose absence made this a superset —
         * and the two **endpoint circles**, each a quadratic. The hull of the pieces is the answer, and
         * the class note says why a hull is exact once that intersection is in place.
         */
        private fun spanWithin(a: Pt, b: Pt, c: Pt, d: Pt, widthM: Double): Span? {
            val vx = b.x - a.x
            val vy = b.y - a.y
            val lengthSq = vx * vx + vy * vy
            if (lengthSq <= 0.0) {
                return if (pointSegmentDistance(a, c, d) <= widthM) Span(0.0, 1.0) else null
            }
            var lo = Double.MAX_VALUE
            var hi = -Double.MAX_VALUE

            fun absorb(from: Double, to: Double) {
                val start = max(from, 0.0)
                val end = min(to, 1.0)
                if (end <= start) return
                if (start < lo) lo = start
                if (end > hi) hi = end
            }

            val ex = d.x - c.x
            val ey = d.y - c.y
            val segmentM = hypot(ex, ey)
            if (segmentM > 0.0) {
                // The slab: |(p(t) − c) × e| ≤ W·|e|, where the cross product is affine in t, A + t·B.
                val a0 = (a.x - c.x) * ey - (a.y - c.y) * ex
                val b0 = vx * ey - vy * ex
                val slab = slabSpan(a0, b0, widthM * segmentM)
                // **The projection's own range**, without which the slab would cover a leg standing
                // beyond the segment's end: only where the nearest point of the line is a point of the
                // segment is "within W of the line" the same statement as "within W of the segment".
                val projection = projectionSpan(a, vx, vy, c, ex, ey, segmentM)
                if (slab != null && projection != null) {
                    val from = max(slab.from, projection.from)
                    val to = min(slab.to, projection.to)
                    if (to > from) absorb(from, to)
                }
            }
            // The two endpoint circles: the other half of "within W of the segment".
            circleSpan(a, vx, vy, lengthSq, c, widthM)?.let { absorb(it.from, it.to) }
            circleSpan(a, vx, vy, lengthSq, d, widthM)?.let { absorb(it.from, it.to) }

            if (hi < lo) return null
            return Span(lo, hi)
        }

        /** `{t : |(p(t) − c) × e| ≤ reach}` — the slab between the two parallel lines, or null. */
        private fun slabSpan(a0: Double, b0: Double, reach: Double): Span? {
            if (abs(b0) <= BAND_EPS) {
                // The leg is parallel to the segment's line: either it stands in the slab throughout or
                // it never does.
                return if (abs(a0) <= reach) Span(0.0, 1.0) else null
            }
            val first = (-reach - a0) / b0
            val second = (reach - a0) / b0
            return Span(min(first, second), max(first, second))
        }

        /**
         * **`{t : the nearest point of the segment's line to `p(t)` is a point of the segment}`** — the
         * projection's parameter running from `0` to `1` — or null when it never does.
         *
         * `proj(t) = ((p(t) − c)·e) / |e|² = P₀ + t·P_v` is affine, so the set is one interval, and an
         * unbounded one where the leg runs parallel to the segment with a projection that stays inside
         * it: that is `[0, 1]` in the leg's own parameters, which is the whole leg and needs no point of
         * infinity to say so.
         */
        private fun projectionSpan(
            a: Pt,
            vx: Double,
            vy: Double,
            c: Pt,
            ex: Double,
            ey: Double,
            segmentM: Double
        ): Span? {
            val scale = segmentM * segmentM
            val constant = ((a.x - c.x) * ex + (a.y - c.y) * ey) / scale
            val slope = (vx * ex + vy * ey) / scale
            if (abs(slope) <= BAND_EPS) {
                return if (constant >= -BAND_EPS && constant <= 1.0 + BAND_EPS) Span(0.0, 1.0) else null
            }
            val first = (0.0 - constant) / slope
            val second = (1.0 - constant) / slope
            return Span(min(first, second), max(first, second))
        }

        /** `{t : |p(t) − centre| ≤ W}`, or null when the leg never comes that close to the endpoint. */
        private fun circleSpan(
            a: Pt,
            vx: Double,
            vy: Double,
            lengthSq: Double,
            centre: Pt,
            widthM: Double
        ): Span? {
            val gx = a.x - centre.x
            val gy = a.y - centre.y
            val along = gx * vx + gy * vy
            val constant = gx * gx + gy * gy - widthM * widthM
            val discriminant = along * along - lengthSq * constant
            if (discriminant < 0.0) return null
            val root = sqrt(discriminant)
            return Span((-along - root) / lengthSq, (-along + root) / lengthSq)
        }

        /** The band's own slack, for the merge of two intervals that meet at a crossing. */
        private const val BAND_EPS = 1e-12

        /**
         * **The shortest stretch inside the band worth calling an interval**, in metres — a millimetre.
         *
         * Below it the span is a *contact*: the tangent case reaches the width's locus at a single point,
         * and the quadratic's double root is a double root only in exact arithmetic, so what arrives is a
         * sliver whose metres are nought and whose price would not be. At 28 kn a millimetre is 70 µs, so
         * nothing a boat can feel is lost by refusing to price it. It is read from the class body as well
         * as from here, which is why it is not `private`.
         */
        const val MIN_SPAN_M = 1e-3
    }
}
