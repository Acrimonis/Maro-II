package ykws.android.maro.spatial.taut

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.spatial.RoutePlanTiming
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units

/**
 * A parameter interval of a leg: `from` and `to` in `0..1` along `a → b`.
 *
 * It is the answer one segment test gives — whether the leg **shares an interval** with a region, and
 * where that interval begins and ends — and it is what replaced the midpoint-plus-parity reading: a leg
 * clipping a zone's corner crosses the ring twice, so a parity read said *not entered* and the crossing
 * report counted it zero, which presented a crossing as an ordinary route.
 */
internal class Span(val from: Double, val to: Double) {
    val length: Double get() = to - from
}

/**
 * **A priced zone as the graph holds it — in the corridor's own metres, with one predicate saying what
 * "inside" means.**
 *
 * A zone is not one ring. Its **interior** is the outer ring **minus its holes**, and that one fact has
 * three readers: the traversal rule (run one refuses the interior while a way around exists), the price
 * (a leg inside is charged the zone's limit, a leg near its boundary the margin's own seconds) and the
 * **splitter** that cuts a drawn leg where the limit changes. Reading it twice from two different
 * predicates is what let the built engine wall a hole §10.2 calls navigable, so all three read
 * [interiorSpans] and nothing else.
 *
 * The rings are held in the frame's metres rather than in raw degrees: containment, the interval a leg
 * shares with the interior and the distance to a boundary are one kind of measurement, and doing them in
 * degrees is what makes two of them disagree by a factor of `cos(latitude)` where the corridor is.
 */
internal class TautZone(
    val shape: TautZoneShape,
    /** The outer ring, closed on itself (its first vertex repeated last). */
    val ringM: List<Pt>,
    /** The holes, each closed on itself — water inside the zone, and therefore not zone. */
    val holesM: List<List<Pt>>,
    /** The ring's own convex corners as seen from the water outside — the points a line bends on. */
    val corners: List<RoutePoint>
) {

    private val box: BoxM = BoxM.of(ringM) ?: BoxM(0.0, 0.0, 0.0, 0.0)

    /**
     * Whether the point stands **inside the zone**: within the outer ring and not within a hole.
     *
     * It is the **point** reading of the one predicate, and it has two readers that ask about a point
     * rather than about a leg: the margin's own exclusion (a leg standing inside a zone is priced by the
     * limit and not by the berth) and the corner's reference limit at a vertex. The rule and the price —
     * both of them questions about a **leg** — read [interiorSpans] instead, which is why this is no
     * longer "the single home" it once claimed to be. A hole is water the zone does not cover, so a point
     * inside one is outside the zone — and a route may therefore thread it, which is what §10.2's "holes
     * are navigable" means once it is a predicate rather than a sentence.
     */
    fun covers(point: Pt): Boolean {
        // The boundary belongs to the water, not to the zone. A point lying on an edge is exactly the
        // case a ray-cast answers inconsistently, and the design's own record admits a line may run along
        // a boundary where leaving costs more time than it saves (§15) — so "on the boundary" is stated
        // as outside rather than left to a tie.
        if (onAnyBoundary(point)) return false
        if (!pointInRing(point, ringM)) return false
        for (hole in holesM) if (pointInRing(point, hole)) return false
        return true
    }

    /**
     * **The intervals of the leg `a → b` that lie inside the zone's interior — the one answer the rule,
     * the price and the splitter share.**
     *
     * The outer ring's own intervals first, then each hole's subtracted, because a hole is water and the
     * interior is the ring **minus** its holes. Nothing is sampled: a straight segment meets a closed ring
     * in a finite set of parameters, the inside-ness is constant between two consecutive ones, and each
     * sub-interval is therefore decided by one point — which is an exact answer about a segment, not a
     * sample of it. The boundary stays water on both sides of the test, [covers]' rule unchanged.
     */
    fun interiorSpans(a: Pt, b: Pt): List<Span> {
        if (!reachableBy(BoxM.ofSegment(a, b))) return emptyList()
        var spans = ringInteriorSpans(ringM, a, b)
        for (hole in holesM) {
            if (spans.isEmpty()) break
            val cuts = ringInteriorSpans(hole, a, b)
            if (cuts.isNotEmpty()) spans = subtractSpans(spans, cuts)
        }
        return spans
    }

    /** Whether the leg `a → b` enters the zone's interior at all — the rule's own reading. */
    fun entered(a: Pt, b: Pt): Boolean = interiorSpans(a, b).isNotEmpty()

    /** The cheap reject in front of [entered]: a leg that cannot reach the ring cannot enter it. */
    fun reachableBy(segment: BoxM): Boolean = box.overlaps(segment)

    /**
     * How much of the berth's price the leg `a → b` earns, `0` outside the margin and `1` on the boundary
     * — the price rising as the boundary nears (§11.2, §15).
     *
     * It is read at the leg's **own closest approach** to the boundary and not at its middle: an edge
     * *inside* the margin is what the design prices, and a long leg whose midpoint is clear while its
     * water grazes a zone is inside the margin for all that — reading only the midpoint leaves the price
     * silent exactly where the line hugs, which is the behaviour the berth exists to answer.
     *
     * This is the **one home** of the margin's fraction, and the reading that reports the margin's metres
     * is computed from it rather than beside it: the two cannot disagree, because there is only one of
     * them.
     */
    fun berthFractionFor(a: Pt, b: Pt, berthM: Double): Double {
        if (berthM <= 0.0) return 0.0
        if (!box.inflate(berthM).overlaps(BoxM.ofSegment(a, b))) return 0.0
        val distance = distanceToBoundaryM(a, b)
        if (distance >= berthM) return 0.0
        // On the boundary itself the fraction is the whole of it: the price rises as the boundary nears,
        // and a line lying along an edge is as near as a line can be without being inside the zone, which
        // the caller has already excluded.
        return 1.0 - distance.coerceAtLeast(0.0) / berthM
    }

    /** The nearest of the zone's own boundaries — outer ring and holes alike — in the frame's metres. */
    fun distanceToBoundaryM(a: Pt, b: Pt): Double {
        var best = Double.MAX_VALUE
        for (i in 0 until ringM.size - 1) {
            best = min(best, segmentDistance(a, b, ringM[i], ringM[i + 1]))
        }
        for (hole in holesM) {
            for (i in 0 until hole.size - 1) {
                best = min(best, segmentDistance(a, b, hole[i], hole[i + 1]))
            }
        }
        return best
    }

    private fun onAnyBoundary(point: Pt): Boolean {
        if (onRingBoundary(point, ringM)) return true
        for (hole in holesM) if (onRingBoundary(point, hole)) return true
        return false
    }
}

/** Whether the point lies on one of a ring's own boundaries, to a centimetre. */
private fun onRingBoundary(point: Pt, ring: List<Pt>): Boolean {
    for (i in 0 until ring.size - 1) {
        if (pointSegmentDistance(point, ring[i], ring[i + 1]) <= GEOMETRY_EPS_M) return true
    }
    return false
}

/**
 * The intervals of the leg `a → b` lying inside one closed ring, the ring's boundary read as outside.
 *
 * The parameters are the leg's two ends and every boundary **properly** crossed; between two consecutive
 * ones the inside-ness cannot change, so one point decides each sub-interval. A touch, a tangent and a
 * stretch running along an edge all fall out of that: a point on the boundary is not inside, which is
 * [TautZone.covers]' own rule, and a corner clip — two proper crossings with the middle inside — comes
 * back as one interval rather than as an even parity.
 */
private fun ringInteriorSpans(ring: List<Pt>, a: Pt, b: Pt): List<Span> {
    if (ring.size < 4) return emptyList()
    val rx = b.x - a.x
    val ry = b.y - a.y
    if (hypot(rx, ry) <= 0.0) return emptyList()
    val params = ArrayList<Double>(4)
    params.add(0.0)
    params.add(1.0)
    for (i in 0 until ring.size - 1) {
        val c = ring[i]
        val d = ring[i + 1]
        if (!properlyCrosses(a, b, c, d)) continue
        val sx = d.x - c.x
        val sy = d.y - c.y
        val denominator = rx * sy - ry * sx
        if (abs(denominator) < SPAN_EPS) continue
        val t = ((c.x - a.x) * sy - (c.y - a.y) * sx) / denominator
        if (t > 0.0 && t < 1.0) params.add(t)
    }
    params.sort()
    val out = ArrayList<Span>(2)
    for (i in 0 until params.size - 1) {
        val from = params[i]
        val to = params[i + 1]
        if (to - from <= SPAN_EPS) continue
        val midT = (from + to) / 2.0
        val mid = Pt(a.x + rx * midT, a.y + ry * midT)
        if (pointInRing(mid, ring) && !onRingBoundary(mid, ring)) out.add(Span(from, to))
    }
    return out
}

/** [spans] with every part covered by a cut removed — a hole taken out of the ring's own interior. */
private fun subtractSpans(spans: List<Span>, cuts: List<Span>): List<Span> {
    var out = spans
    for (cut in cuts) {
        val next = ArrayList<Span>(out.size + 1)
        for (span in out) {
            if (cut.to <= span.from + SPAN_EPS || cut.from >= span.to - SPAN_EPS) {
                next.add(span)
                continue
            }
            if (cut.from > span.from + SPAN_EPS) next.add(Span(span.from, cut.from))
            if (cut.to < span.to - SPAN_EPS) next.add(Span(cut.to, span.to))
        }
        out = next
    }
    return out
}

/** One part of a leg at a single limit: where it ends, the limit in force over it, and its length (m). */
internal class LegRegion(
    /** The point this part ends at — a split point, or the leg's own far end. */
    val to: RoutePoint,
    /** The limit (kn) in force over this part. */
    val limitKn: Double,
    /** The part's own length (m), taken between the points the drawn clock will be handed. */
    val lengthM: Double
)

/**
 * A parameter span, the limit in force over it and **the zone that put it there**, before it is turned
 * into points.
 *
 * The zone's own index rides with the span because one read of this list serves three readers (§17 item
 * 5's collapse, item 5 of the level above): the price, the rule's own entry test and the margin's
 * exclusion. Reading the zone's intervals three times per candidate pair — once to price, once to ask
 * whether the leg enters, once to exclude it from the berth — is the cost this list removes.
 */
internal class SpanLimit(
    val from: Double,
    val to: Double,
    val limitKn: Double,
    /** The zone whose interior put this limit here, or `-1` for a span no zone owns. */
    val zone: Int = -1
)

/**
 * **The priced zones of one corridor, and the questions the two runs ask of them.**
 *
 * The rule and the price read the same zone list and the same [TautZone.interiorSpans]: run one refuses a
 * leg that **enters an interior**, the price charges the limit in force over each part of a leg, and both
 * runs charge the **margin's seconds** to a leg that runs through it. There is no collar — a hard buffer
 * around a zone walls a strip §11.2 says is crossable, which is the deviation the review found and §15
 * resolved the other way.
 */
internal class TautZoneSet(
    private val zones: List<TautZone>,
    private val frame: Frame
) {

    fun all(): List<TautZone> = zones

    fun zone(index: Int): TautZone = zones[index]

    /** The zone whose **interior** the leg enters, or `-1` — run one's rule, where a way around exists. */
    fun enteredZone(a: RoutePoint, b: RoutePoint): Int {
        val pa = frame.pt(LatLng(a.latitude, a.longitude))
        val pb = frame.pt(LatLng(b.latitude, b.longitude))
        val segment = BoxM.ofSegment(pa, pb)
        for ((index, zone) in zones.withIndex()) {
            // The box test is the cheap reject that keeps the polygon work off the pairs that could not
            // possibly touch a zone — the distance prune §16 step 8 asks for, and it can only ever skip
            // a leg that provably enters nothing.
            if (!zone.reachableBy(segment)) continue
            if (zone.entered(pa, pb)) return index
        }
        return -1
    }

    /**
     * The most restrictive limit in force **at a point** — a zone's own where its interior covers it,
     * `∞` where none does.
     *
     * It is the corner's own reading: a vertex stands somewhere, and the limit it is held to is the one
     * in force at that spot. The leg-shaped questions read [interiorSpans] and [limitSpans], because a
     * leg's limit is a length rather than a point.
     */
    fun limitAt(point: RoutePoint): Double {
        if (zones.isEmpty()) return Double.MAX_VALUE
        val p = frame.pt(LatLng(point.latitude, point.longitude))
        val box = BoxM(p.x, p.x, p.y, p.y)
        var limit = Double.MAX_VALUE
        for (zone in zones) {
            if (!zone.reachableBy(box)) continue
            if (!zone.covers(p)) continue
            if (zone.shape.limitKn < limit) limit = zone.shape.limitKn
        }
        return limit
    }

    /**
     * **The intervals of the leg that stand in a zone, each with the most restrictive limit covering it**
     * — the price's own shape, and the splitter's, read from the one predicate above.
     *
     * Where two zones overlap the stricter limit holds, which is what "most restrictive first" means once
     * it is a length rather than a point.
     */
    fun limitSpans(a: Pt, b: Pt): List<SpanLimit> {
        if (zones.isEmpty()) return emptyList()
        val segment = BoxM.ofSegment(a, b)
        val raw = ArrayList<SpanLimit>(4)
        for ((index, zone) in zones.withIndex()) {
            if (!zone.reachableBy(segment)) continue
            for (span in zone.interiorSpans(a, b)) {
                raw.add(SpanLimit(span.from, span.to, zone.shape.limitKn, index))
            }
        }
        if (raw.isEmpty()) return emptyList()
        val params = ArrayList<Double>(raw.size * 2)
        for (item in raw) {
            params.add(item.from)
            params.add(item.to)
        }
        params.sort()
        val out = ArrayList<SpanLimit>(raw.size)
        for (i in 0 until params.size - 1) {
            val from = params[i]
            val to = params[i + 1]
            if (to - from <= SPAN_EPS) continue
            var limit = Double.MAX_VALUE
            // The stricter limit holds where two zones overlap, and the zone that carried it rides with
            // the span, so the rule's own reading is this list's first entry rather than a second lookup.
            var owner = -1
            for (item in raw) {
                if (item.from <= from + SPAN_EPS && item.to >= to - SPAN_EPS && item.limitKn < limit) {
                    limit = item.limitKn
                    owner = item.zone
                }
            }
            if (owner < 0) continue
            val last = out.lastOrNull()
            if (last != null && last.limitKn == limit && last.to >= from - SPAN_EPS) {
                out[out.size - 1] = SpanLimit(last.from, to, limit, last.zone)
            } else {
                out.add(SpanLimit(from, to, limit, owner))
            }
        }
        return out
    }

    /**
     * The berth's own fraction for a leg — the largest any zone's margin asks of its own closest approach.
     *
     * A fraction rather than seconds, because the seconds depend on the leg's own time and that is the
     * search's to know: the price is `legSeconds × BERTH_MAX_PRICE × fraction`, read in one place.
     *
     * **The exclusion is per piece, and it removes the interior's own span rather than the whole leg**
     * (item 2 of the level above, and the review's item 2). A zone the leg shares an interior with used to
     * be skipped outright, so a leg that clipped a corner and then ran inside the margin paid neither the
     * limit over the clip nor the berth for the rest — and the acceptance pair's `0.00 km`/`0.00 s` could
     * not tell "no leg runs a margin" from "the exclusion forgave it". What is taken out here is the
     * **interval the leg shares with the interior** — the same spans the rule and the price read — and
     * the remainder is priced zone by zone, so the metres inside the zone are charged the zone's limit
     * and the metres outside it pay the margin.
     *
     * The pieces' own fractions are weighted by their share of the leg, so the fraction returned is the
     * leg's **metres** in the margin over its length: `inMarginM` is then exactly that sum, and the price
     * is the same fraction on the leg's seconds — one home, two readers, still.
     */
    fun berthFraction(
        pa: Pt,
        pb: Pt,
        legM: Double,
        berthM: Double,
        interiorSpans: List<SpanLimit>
    ): Double {
        if (berthM <= 0.0 || legM <= 0.0 || zones.isEmpty()) return 0.0
        val segment = BoxM.ofSegment(pa, pb)
        val outside = outsideSpans(interiorSpans)
        var worst = 0.0
        for (zone in zones) {
            // The cheap reject, on the box grown by the berth: a zone whose ring cannot come within the
            // margin of this leg cannot price it.
            if (!zone.reachableBy(segment.inflate(berthM))) continue
            var earned = 0.0
            for (piece in outside) {
                val from = pointOn(pa, pb, piece.from)
                val to = pointOn(pa, pb, piece.to)
                val fraction = zone.berthFractionFor(from, to, berthM)
                if (fraction > 0.0) earned += legM * piece.length * fraction
            }
            if (earned > worst) worst = earned
        }
        return (worst / legM).coerceIn(0.0, 1.0)
    }

    /** The same fraction for a leg named by its own ends — the reading's own door. */
    fun berthFraction(a: RoutePoint, b: RoutePoint, berthM: Double): Double {
        val pa = frame.pt(LatLng(a.latitude, a.longitude))
        val pb = frame.pt(LatLng(b.latitude, b.longitude))
        return berthFraction(pa, pb, metresBetween(a, b), berthM, limitSpans(pa, pb))
    }

    /**
     * **The metres of the route the berth's price is charged on** — the reading §16 step 3 closes on,
     * and it is the same quantity the price is paid on rather than a second one.
     *
     * Each leg contributes its own length weighted by the fraction it earns at its closest approach,
     * which is exactly the fraction `berthPriceSec` multiplies by the leg's seconds: one home, two
     * readers, so a reading that counts the margin's metres and a price that charges the margin's
     * seconds cannot measure two different things (item 5's first should-fix).
     *
     * **The legs are the price's own** (item 4): the search's chain edges, handed in as they were
     * walked. The drawn line is a different, shorter set — it cuts every eased corner and re-reads the
     * fraction on each stub — so a reading taken over it counted metres the price was never paid on,
     * and the two readers stood beside each other while both claimed to be one home.
     */
    fun inMarginM(legs: List<Pair<RoutePoint, RoutePoint>>, berthM: Double): Double {
        if (berthM <= 0.0 || legs.isEmpty()) return 0.0
        var total = 0.0
        for ((from, to) in legs) {
            val fraction = berthFraction(from, to, berthM)
            if (fraction <= 0.0) continue
            total += metresBetween(from, to) * fraction
        }
        return total
    }

    companion object {

        /**
         * The ceiling on the berth's price, as a multiple of a leg's own time.
         *
         * Twice a leg's seconds is the mesh engine's own ceiling, kept rather than re-chosen: a courtesy
         * that could cost more than the leg it is paid on would stop being a preference and start being
         * a wall by another name.
         */
        const val BERTH_MAX_PRICE = 2.0
    }
}

/**
 * **The corridor's own visibility graph: the obstacles' corners, and the lines between them that the
 * water allows.**
 *
 * It is a flat, CSR-indexed graph — one adjacency block per vertex — and **every edge carries its own
 * licence**: its length, its **price** (its own time summed over its limit regions, the number the
 * search's step and the drawn clock both charge), the fraction of the berth's price its own closest
 * approach earns, and the zone whose interior it enters. Those are read once, when the graph is built, so
 * the search's relaxation is arithmetic and no world query is repeated per expansion — and, more to the
 * point, so the drawn line and the search read **the same numbers** rather than two computations that
 * could disagree.
 *
 * **A vertex carries the limit in force where it stands** ([limitAt]), and that is the corner's own
 * reference speed: the dearest limit anywhere along a leg that merely clips a zone is not the boat's
 * limit at the corner, and an edge-level maximum is read by nothing here (§17 item 1's rule, at the
 * corner).
 *
 * A leg is allowed when the wall set does not cross it and its own midpoint stands on water the boat may
 * use. The midpoint is not decoration: two adjacent legs share a wall corner by construction, so the
 * crossing test is the strict interior one, and the open-water question has to be asked separately.
 */
internal class TautGraph private constructor(
    val vertices: List<RoutePoint>,
    val edgeCount: Int,
    val cornerCount: Int,
    /** How many vertex pairs the visibility loop examined — the build's own cost, in pairs (§16 step 1). */
    val candidatePairs: Int,
    val startIndex: Int,
    val aimIndex: Int,
    val destinationMoved: Boolean,
    val zones: TautZoneSet,
    /**
     * **The graph's own pricer**, kept with it: the splitter below and the search's step are two
     * readings of this one object, which is why they cannot disagree about where a limit changes.
     */
    val pricing: LegLimits,
    /** How many coastline segments the band's own index holds — the spans' price, in segments (§18.1). */
    val bandSegmentCount: Int,
    private val nodeOffset: IntArray,
    private val nodeEdges: IntArray,
    private val edgeFrom: IntArray,
    private val edgeTo: IntArray,
    private val edgeLengthM: DoubleArray,
    private val edgePriceSec: DoubleArray,
    /** The limit in force at each vertex — the corner's own reference speed, see [limitAt]. */
    private val vertexLimitKn: DoubleArray,
    private val edgeZone: IntArray,
    private val edgeBerthFraction: DoubleArray,
    private val edgeByPair: HashMap<Long, Int>
) {

    /**
     * The vertex at the far end of [edge] from [from] — the edge is stored once and reached from both
     * of its ends, so "the other end" is only meaningful together with the end the caller stands on.
     */
    fun other(edge: Int, from: Int): Int =
        if (edgeFrom[edge] == from) edgeTo[edge] else edgeFrom[edge]

    /**
     * The edge joining two vertices, or `-1` — the lookup the search needs because a state carries the
     * vertex it arrived from rather than the edge it arrived on, and a corner's price is read from the
     * limits of **both** its legs.
     */
    fun edgeBetween(a: Int, b: Int): Int {
        val low = if (a < b) a else b
        val high = if (a < b) b else a
        return edgeByPair[(low.toLong() shl 32) or (high.toLong() and 0xFFFFFFFFL)] ?: -1
    }

    /** The edge indices leaving [vertex]. */
    fun edgesFrom(vertex: Int): IntArray {
        val from = nodeOffset[vertex]
        val to = nodeOffset[vertex + 1]
        return if (to > from) nodeEdges.copyOfRange(from, to) else EMPTY
    }

    fun lengthM(edge: Int): Double = edgeLengthM[edge]

    /**
     * **The edge's own price**: its time summed over its limit regions, each region's length at the speed
     * in force there. This is the search's step, and the drawn line is cut at the same boundaries and
     * charged at the same limits, so the two charge one number by construction.
     */
    fun priceSec(edge: Int): Double = edgePriceSec[edge]

    /**
     * **The limit in force at the vertex** — the corner's own reference speed, never the price.
     *
     * The corner is at the vertex, so the water it stands in is what sizes it. The dearest limit anywhere
     * along either of its legs is not the boat's limit at the corner: a leg may clip a zone and be priced
     * over that clip alone, and sizing the corner from the leg would slow and ease a turn the water never
     * held it to — the maximum §17 item 1 rejects, read at the wrong place. The value is read once per
     * vertex, at the build: a zone's own limit where its interior covers the vertex, the band's where the
     * vertex hugs the shore, and `∞` where neither does.
     */
    fun limitAt(vertex: Int): Double = vertexLimitKn[vertex]

    /** The zone whose interior the edge enters, or `-1` — the crossing a run two answer is reported by. */
    fun zoneOf(edge: Int): Int = edgeZone[edge]

    /** How much of the berth's price the edge's own closest approach earns, `0..1` — run two's courtesy. */
    fun berthFraction(edge: Int): Double = edgeBerthFraction[edge]

    /**
     * **The splitter: the pieces the drawn leg `a → b` is emitted as, each with the limit it is charged
     * at.**
     *
     * The one home of "where does the limit change along this leg", read by the search's own drawn clock
     * and by the assembly — never a second computation written inside the assembly, which is the very
     * defect this file's note warns against. A straight leg is passed `∞` and the zones and the band
     * supply every limit; a corner's **chord** is passed the corner's own limit, and a boundary falling
     * inside an emitted arc is then cut and re-priced like any other, a drawn corner's limits being per
     * chord off one value for the whole corner.
     */
    fun drawnPieces(a: RoutePoint, b: RoutePoint, nominalLimitKn: Double): List<LegRegion> =
        pricing.regionsOf(a, b, nominalLimitKn)

    companion object {

        private val EMPTY = IntArray(0)

        /**
         * The corridor's rough guess: the A→B segment inflated by **one nautical mile plus the berth**.
         *
         * The berth belongs in the guess because the wall is dilated by it after the harvest, so a box
         * sized without it would clip the dilated wall and drop the very corners the line bends on. It is
         * still a guess rather than a proof — [sizedFromObstacles] widens it from what the harvest
         * actually found — and it is clipped to the depth grid, the only water the soundings reach,
         * always keeping both ends inside.
         */
        fun corridorBox(
            world: TautWorld,
            start: RoutePoint,
            aim: RoutePoint,
            growth: Int,
            berthM: Double
        ): BoundingBox {
            val midLat = (start.latitude + aim.latitude) / 2.0
            val metresPerDegreeLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
            val metresPerDegreeLon = metresPerDegreeLat * cos(midLat * PI / 180.0)
            val marginM =
                CORRIDOR_MARGIN_NM * (1 shl growth) * Units.METRES_PER_NAUTICAL_MILE + berthM
            val marginLat = marginM / metresPerDegreeLat
            val marginLon = marginM / metresPerDegreeLon
            val box = BoundingBox(
                latSouth = min(start.latitude, aim.latitude) - marginLat,
                latNorth = max(start.latitude, aim.latitude) + marginLat,
                lonWest = min(start.longitude, aim.longitude) - marginLon,
                lonEast = max(start.longitude, aim.longitude) + marginLon
            )
            return clippedToDepth(world, box, start, aim)
        }

        /**
         * **The box sized from the obstacles it must contain** (§16 step 2).
         *
         * The corridor is the guess unioned with the extent of everything the harvest was handed, grown
         * by the berth the walls will be dilated by — so an obstacle whose own reach crosses the guess is
         * held whole, and the tangent corners of its dilated wall are inside the corridor by
         * construction rather than by luck.
         *
         * The union is **clamped to a bounded reach** past the guess before it is taken: a coast that runs
         * on past the corridor is water the corridor runs *beside* rather than an obstacle it must
         * contain, and following it would size every corridor from the region and every graph from the
         * region's coastline — which is the failure mode this correction must not introduce while fixing
         * the other one. An island or a zone the corridor straddles lies inside the reach and is held. The
         * clamp is measured from the **guess** rather than from the running box, so the sizing is one
         * step rather than a chase, and it is clipped to the depth grid like the guess.
         *
         * **That clamp is also what bounds this function**, and [coldBoundBox] is the bound: no extent,
         * however far it reaches, can carry the box past the clamp, so a box computed from the clamp alone
         * is a superset of every answer this function can give for the same guess. A reuse is licensed on
         * exactly that box (§19.5 C2).
         */
        fun sizedFromObstacles(
            world: TautWorld,
            rough: BoundingBox,
            extent: BoundingBox?,
            start: RoutePoint,
            aim: RoutePoint,
            berthM: Double
        ): BoundingBox {
            if (extent == null) return rough
            val frame = Frame(rough.centerLat, rough.centerLon)
            val reach = OBSTACLE_REACH_NM * Units.METRES_PER_NAUTICAL_MILE + berthM
            val roughMin = Pt(frame.x(rough.lonWest), frame.y(rough.latSouth))
            val roughMax = Pt(frame.x(rough.lonEast), frame.y(rough.latNorth))
            // The union is grown by the berth, because what the corridor must hold is the **dilated**
            // wall: a box holding the obstacle's own edge exactly clips the berth that will be drawn
            // around it, and the corner a route bends on falls outside — which is the refusal this
            // correction exists to remove. The centimetre is the dilation's own leeway.
            val held = BoxM(
                minX = max(frame.x(extent.lonWest), roughMin.x - reach),
                maxX = min(frame.x(extent.lonEast), roughMax.x + reach),
                minY = max(frame.y(extent.latSouth), roughMin.y - reach),
                maxY = min(frame.y(extent.latNorth), roughMax.y + reach)
            )
                .union(BoxM(roughMin.x, roughMax.x, roughMin.y, roughMax.y))
                .inflate(berthM + GEOMETRY_EPS_M)
            val southWest = frame.latLng(Pt(held.minX, held.minY))
            val northEast = frame.latLng(Pt(held.maxX, held.maxY))
            val grown = BoundingBox(
                latSouth = southWest.latitude,
                latNorth = northEast.latitude,
                lonWest = southWest.longitude,
                lonEast = northEast.longitude
            )
            return clippedToDepth(world, grown, start, aim)
        }

        /**
         * **The largest box a cold search could build from this rough corridor** (§19.5, C2).
         *
         * A reuse hands the graph the **kept** box, so a reused search may build a larger graph than a
         * cold one would; the reuse licence therefore cannot be "the new corridor lies inside the kept
         * one" read on the rough guess. What a cold search really builds is [sizedFromObstacles], and the
         * clamp argued there is what bounds it from above: the guess unioned with anything the harvest
         * found, clamped to the obstacle reach, inflated by the berth and the dilation's leeway, and
         * clipped to the depth grid. This function is that upper bound, computed **by the very arithmetic
         * the sizing uses** — same frame, same reach, same inflation, same clip — so a kept box harvested
         * from the clamp holds it to the digit rather than to a rounding. A kept box that holds this box
         * is never narrower than a cold search's, which is what a reuse must be licensed on; the price is
         * that the licence then fires only where the new corridor *with that reach* lies inside the kept
         * one, a rate the C2 reading reports rather than assumes.
         */
        fun coldBoundBox(
            world: TautWorld,
            rough: BoundingBox,
            start: RoutePoint,
            aim: RoutePoint,
            berthM: Double
        ): BoundingBox {
            val frame = Frame(rough.centerLat, rough.centerLon)
            val reach = OBSTACLE_REACH_NM * Units.METRES_PER_NAUTICAL_MILE + berthM
            val roughMin = Pt(frame.x(rough.lonWest), frame.y(rough.latSouth))
            val roughMax = Pt(frame.x(rough.lonEast), frame.y(rough.latNorth))
            // The clamp's own box: what the sizing holds when the harvest's extent reaches past it in
            // every direction, and therefore the widest box the sizing can return for this guess.
            val widest = BoxM(
                minX = roughMin.x - reach,
                maxX = roughMax.x + reach,
                minY = roughMin.y - reach,
                maxY = roughMax.y + reach
            ).inflate(berthM + GEOMETRY_EPS_M)
            val southWest = frame.latLng(Pt(widest.minX, widest.minY))
            val northEast = frame.latLng(Pt(widest.maxX, widest.maxY))
            val grown = BoundingBox(
                latSouth = southWest.latitude,
                latNorth = northEast.latitude,
                lonWest = southWest.longitude,
                lonEast = northEast.longitude
            )
            return clippedToDepth(world, grown, start, aim)
        }

        /**
         * The grid's own box clipping. A clip that would drop an end is no corridor at all, so the
         * unclipped box stands rather than a corridor that cannot contain the route it is asked for.
         */
        private fun clippedToDepth(
            world: TautWorld,
            box: BoundingBox,
            start: RoutePoint,
            aim: RoutePoint
        ): BoundingBox {
            val depth = world.depthBox ?: return box
            val clipped = BoundingBox(
                latSouth = max(box.latSouth, depth.latSouth),
                latNorth = min(box.latNorth, depth.latNorth),
                lonWest = max(box.lonWest, depth.lonWest),
                lonEast = min(box.lonEast, depth.lonEast)
            )
            return if (clipped.latSouth < clipped.latNorth && clipped.lonWest < clipped.lonEast &&
                contains(clipped, start) && contains(clipped, aim)
            ) {
                clipped
            } else {
                box
            }
        }

        private fun contains(box: BoundingBox, point: RoutePoint): Boolean =
            point.latitude in box.latSouth..box.latNorth &&
                point.longitude in box.lonWest..box.lonEast

        /**
         * **The graph over a corridor's terrain** — handed in rather than harvested here (§19.4).
         *
         * The terrain is what a corridor's obstacles, zones and band are, and it is a function of the box
         * and of the world and of nothing else — so it belongs to whoever can keep it across searches,
         * which is the engine. What this function adds is the part that *is* the answer: the two ends, the
         * vertices, the visibility pairs and the prices.
         *
         * The vertex set is the **bends a taut line can take**: the start and the aim, then every corner
         * of every wall that protrudes into the water, every such corner of a zone's outer ring, and
         * every such corner of a **hole** — a hole is water, so threading one is legal and its corners
         * are where a line through it bends. A corner where an obstacle bends away from the water is not
         * a node, because a line touching it would run inside the obstacle.
         *
         * @param terrain the corridor's obstacles, zones and band; its own box is the box this graph is
         *        built over, so the terrain and the corridor can never disagree about which water is held.
         * @param cruiseSpeedKn the pace the edges are priced at, taken here rather than at search time so
         *        that an edge's price is its own time summed over its limit regions — one number the
         *        search's step and the drawn clock both charge.
         */
        fun build(
            world: TautWorld,
            terrain: TautTerrain,
            start: RoutePoint,
            aim: RoutePoint,
            cruiseSpeedKn: Double,
            /**
             * **Asked between vertices, so an abandoned build stops rather than finishing** (§19.2 item 4).
             *
             * The pair loop is quadratic — 92 665 pairs on the acceptance pair — and a check only between
             * phases makes a drag wait for all of it; one check per outer vertex costs nanoseconds and puts
             * the abort inside the half-millisecond.
             */
            cancelCheck: () -> Unit = {}
        ): TautGraph {
            val obstacles = terrain.obstacles
            val box = terrain.box
            val frame = obstacles.frame
            val zones = terrain.zones

            val resolvedAim = if (obstacles.traversable(aim.latitude, aim.longitude)) {
                aim
            } else {
                nearestTraversable(obstacles, aim, box) ?: aim
            }
            val destinationMoved = resolvedAim != aim

            // One node per distinct corner: two obstacles that meet at a point share it, and a shared
            // corner counted twice would double the edges that leave it without adding a single bend.
            val vertices = ArrayList<RoutePoint>(obstacles.corners.size + 8)
            val seen = HashSet<Long>(obstacles.corners.size * 2)
            vertices.add(start)
            seen.add(vertexKey(start))
            vertices.add(resolvedAim)
            seen.add(vertexKey(resolvedAim))
            var cornerCount = 0
            for (corner in obstacles.corners) {
                if (!obstacles.traversable(corner.latitude, corner.longitude)) continue
                if (!seen.add(vertexKey(corner))) continue
                vertices.add(corner)
                cornerCount++
            }
            for (zone in zones.all()) {
                for (corner in zone.corners) {
                    if (!seen.add(vertexKey(corner))) continue
                    vertices.add(corner)
                }
            }

            // **The band's span source now rides in the terrain** (§19.4): the coastline segments within
            // the band's reach of the box, indexed so a leg asks only about the coast near the cells it
            // passes through — the answer is the world's own distance predicate, solved rather than
            // sampled, and built off the **obstacles' own frame** so the corridor keeps one projection.
            // Null when the band is switched off, which is the shape the tests take.
            val band = terrain.band
            val legLimits = LegLimits(world, zones, band, frame, cruiseSpeedKn)
            // **The corner's own reading, taken once per vertex**: the limit in force where the vertex
            // stands. It is not the dearest limit over either leg, which is what used to size a corner
            // against a zone the leg merely clipped (item 2).
            val vertexLimits = DoubleArray(vertices.size) { index ->
                limitInForceAt(world, zones, vertices[index])
            }
            val from = ArrayList<Int>()
            val to = ArrayList<Int>()
            val lengths = ArrayList<Double>()
            val prices = ArrayList<Double>()
            val zoneOf = ArrayList<Int>()
            val berthOf = ArrayList<Double>()

            val starts = HashMap<Int, ArrayList<Int>>()
            val aims = HashMap<Int, ArrayList<Int>>()
            val byPair = HashMap<Long, Int>()
            var candidatePairs = 0
            for (i in vertices.indices) {
                cancelCheck()
                for (j in i + 1 until vertices.size) {
                    val a = vertices[i]
                    val b = vertices[j]
                    candidatePairs++
                    if (!allowed(obstacles, a, b)) continue
                    // **One read of the zone's intervals per candidate pair** (item 5 of the level above):
                    // the price, the rule's own entry test and the margin's exclusion all read this one
                    // list, where the build used to compute the same intervals three times over.
                    val pa = frame.pt(LatLng(a.latitude, a.longitude))
                    val pb = frame.pt(LatLng(b.latitude, b.longitude))
                    val zoneSpans = zones.limitSpans(pa, pb)
                    val price = legLimits.priceOf(a, b, zoneSpans)
                    val zone = if (zoneSpans.isEmpty()) -1 else zoneSpans.first().zone
                    val berth = zones.berthFraction(pa, pb, metres(a, b), obstacles.berthM, zoneSpans)
                    val edge = from.size
                    from.add(i)
                    to.add(j)
                    lengths.add(metres(a, b))
                    prices.add(price)
                    zoneOf.add(zone)
                    berthOf.add(berth)
                    starts.getOrPut(i) { ArrayList(2) }.add(edge)
                    aims.getOrPut(j) { ArrayList(2) }.add(edge)
                    byPair[(i.toLong() shl 32) or (j.toLong() and 0xFFFFFFFFL)] = edge
                }
            }

            val nodeOffset = IntArray(vertices.size + 1)
            for (i in vertices.indices) {
                nodeOffset[i + 1] = nodeOffset[i] + (starts[i]?.size ?: 0) + (aims[i]?.size ?: 0)
            }
            val nodeEdges = IntArray(nodeOffset[vertices.size])
            for (i in vertices.indices) {
                var cursor = nodeOffset[i]
                starts[i]?.let { for (edge in it) nodeEdges[cursor++] = edge }
                aims[i]?.let { for (edge in it) nodeEdges[cursor++] = edge }
            }
            return TautGraph(
                vertices = vertices,
                edgeCount = from.size,
                cornerCount = cornerCount,
                candidatePairs = candidatePairs,
                startIndex = 0,
                aimIndex = 1,
                destinationMoved = destinationMoved,
                zones = zones,
                pricing = legLimits,
                bandSegmentCount = band?.segmentCount ?: 0,
                nodeOffset = nodeOffset,
                nodeEdges = nodeEdges,
                edgeFrom = IntArray(from.size) { from[it] },
                edgeTo = IntArray(to.size) { to[it] },
                edgeLengthM = DoubleArray(lengths.size) { lengths[it] },
                edgePriceSec = DoubleArray(prices.size) { prices[it] },
                vertexLimitKn = vertexLimits,
                edgeZone = IntArray(zoneOf.size) { zoneOf[it] },
                edgeBerthFraction = DoubleArray(berthOf.size) { berthOf[it] },
                edgeByPair = byPair
            )
        }

        /**
         * Whether the water lets the line `a → b` be drawn: no wall crosses it, and its own middle stands
         * on water the boat may use. The strict crossing test is deliberate — two legs of a taut line
         * share a wall corner, and a closed test would refuse every bend.
         */
        private fun allowed(obstacles: TautObstacles, a: RoutePoint, b: RoutePoint): Boolean {
            if (obstacles.crossesWall(a, b)) return false
            val midLat = (a.latitude + b.latitude) / 2.0
            val midLon = (a.longitude + b.longitude) / 2.0
            return obstacles.traversable(midLat, midLon)
        }

        /**
         * The nearest corner the boat may stand on, for a destination that resolves onto land.
         *
         * It is read by [build] **and** by the engine, which is why it is not private: whether the aim's
         * own water exists at all is the difference between a destination that was resolved and one that
         * stands off the water, and the engine is where that refusal is named (§17 item 4's second half).
         */
        internal fun nearestTraversable(
            obstacles: TautObstacles,
            aim: RoutePoint,
            box: BoundingBox
        ): RoutePoint? {
            var best: RoutePoint? = null
            var bestDistance = Double.MAX_VALUE
            for (corner in obstacles.corners) {
                if (corner.latitude !in box.latSouth..box.latNorth) continue
                if (corner.longitude !in box.lonWest..box.lonEast) continue
                if (!obstacles.traversable(corner.latitude, corner.longitude)) continue
                val distance = metres(aim, corner)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = corner
                }
            }
            return best
        }

        /**
         * The corridor's priced zones, each in the frame's metres with its holes.
         *
         * The corners kept are the ring's **own first vertex included**: a ring is closed on itself, so
         * index 0 is a corner like any other, and starting the walk at 1 — as the built engine did —
         * dropped the island's south-west tangent point and, with it, the whole way round that side. A
         * hole's corners are kept the same way, with the water on the **inside** of the hole.
         *
         * It is read by [`TautTerrain.of`] rather than by [build], because a zone harvest is a function of
         * the box and of the world like the rest of the corridor's terrain (§19.4) — the graph no longer
         * harvests anything at all.
         *
         * [cancelCheck] is **asked per shape**, so an abandoned corridor stops inside the zone walk the
         * way it stops inside the walls' and the band's own (§19.5 C6): a terrain harvest is one stretch of
         * work, and a promise a caller makes about it has to hold for every part of it.
         */
        internal fun harvestZones(
            world: TautWorld,
            box: BoundingBox,
            frame: Frame,
            cancelCheck: () -> Unit = {}
        ): TautZoneSet {
            val zones = ArrayList<TautZone>()
            for (shape in world.zoneShapesIn(box)) {
                cancelCheck()
                val ring = closedRing(shape.outerRing) ?: continue
                if (ring.size < 4) continue
                val ringM = frame.pts(ring)
                val holesM = shape.holes
                    .mapNotNull { hole -> closedRing(hole)?.let { frame.pts(it) } }
                    .filter { it.size >= 4 }
                val outside = ringSign(ringM, inside = false)
                val corners = ArrayList<RoutePoint>()
                collectCorners(ringM, outside, frame, box, corners)
                for (hole in holesM) {
                    val inside = ringSign(hole, inside = true)
                    collectCorners(hole, inside, frame, box, corners)
                }
                zones.add(TautZone(shape, ringM, holesM, corners))
            }
            return TautZoneSet(zones, frame)
        }

        /** Every corner of a closed ring ([ringM]'s last vertex repeating its first) that protrudes. */
        private fun collectCorners(
            ringM: List<Pt>,
            waterSign: Double,
            frame: Frame,
            box: BoundingBox,
            out: MutableList<RoutePoint>
        ) {
            val distinct = ringM.size - 1
            for (i in 0 until distinct) {
                val previous = ringM[(i - 1 + distinct) % distinct]
                val next = ringM[(i + 1) % distinct]
                if (!protrudesIntoWater(previous, ringM[i], next, waterSign)) continue
                val point = frame.latLng(ringM[i])
                if (point.latitude !in box.latSouth..box.latNorth) continue
                if (point.longitude !in box.lonWest..box.lonEast) continue
                out.add(RoutePoint(point.latitude, point.longitude))
            }
        }

        /**
         * Which side of the ring's travel its water lies on — `+1` left, `-1` right.
         *
         * It is read from the ring's **own shape** rather than from a query: the two sides of the ring's
         * longest segment are probed a metre out, and the side [inside] names — outside the ring for a
         * zone's outer boundary, within it for a hole — is the water side. No world call, and no
         * dependence on a layer having landed.
         */
        private fun ringSign(ringM: List<Pt>, inside: Boolean): Double {
            var bestIndex = 0
            var longest = -1.0
            for (i in 0 until ringM.size - 1) {
                val length = hypot(ringM[i + 1].x - ringM[i].x, ringM[i + 1].y - ringM[i].y)
                if (length > longest) {
                    longest = length
                    bestIndex = i
                }
            }
            if (longest <= 0.0) return -1.0
            val a = ringM[bestIndex]
            val b = ringM[bestIndex + 1]
            val mid = Pt((a.x + b.x) / 2.0, (a.y + b.y) / 2.0)
            val dx = (b.x - a.x) / longest
            val dy = (b.y - a.y) / longest
            val left = Pt(mid.x - dy * RING_PROBE_M, mid.y + dx * RING_PROBE_M)
            val right = Pt(mid.x + dy * RING_PROBE_M, mid.y - dx * RING_PROBE_M)
            val leftIsWater = pointInRing(left, ringM) == inside
            val rightIsWater = pointInRing(right, ringM) == inside
            if (leftIsWater != rightIsWater) return if (leftIsWater) 1.0 else -1.0
            return -1.0
        }

        /** A ring closed on itself, so the dilation and the containment tests may assume it. */
        private fun closedRing(points: List<LatLng>): List<LatLng>? {
            if (points.size < 3) return null
            val first = points.first()
            val last = points.last()
            return if (abs(first.latitude - last.latitude) < 1e-12 &&
                abs(first.longitude - last.longitude) < 1e-12
            ) {
                points
            } else {
                points + first
            }
        }

        private fun metres(from: RoutePoint, to: RoutePoint): Double = SpatialOperations.haversine(
            LatLng(from.latitude, from.longitude),
            LatLng(to.latitude, to.longitude)
        )

        /** The corridor's rough starting margin: one nautical mile, doubled once on an empty answer. */
        const val CORRIDOR_MARGIN_NM = 1.0

        /**
         * How far past the rough box an obstacle's own extent is followed before the corridor stops
         * containing it — see [sizedFromObstacles], which is where the bounded reach is argued.
         */
        const val OBSTACLE_REACH_NM = 1.0

        /** How far the water side is probed off a ring to decide which side it is on (m). */
        private const val RING_PROBE_M = 1.0
    }
}

/** The geodesic metres between two drawn points — the same measure the drawn clock uses. */
private fun metresBetween(from: RoutePoint, to: RoutePoint): Double = SpatialOperations.haversine(
    LatLng(from.latitude, from.longitude),
    LatLng(to.latitude, to.longitude)
)

/** The point a fraction of the way along `a → b` — the linear interpolation both readers use. */
private fun pointAt(a: RoutePoint, b: RoutePoint, t: Double): RoutePoint = RoutePoint(
    a.latitude + (b.latitude - a.latitude) * t,
    a.longitude + (b.longitude - a.longitude) * t
)

/** The same point in the frame's own metres — the margin's per-piece exclusion walks a leg's spans. */
internal fun pointOn(a: Pt, b: Pt, t: Double): Pt =
    Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/**
 * **The leg's parameters outside every span given** — the pieces the berth is charged on.
 *
 * The spans arrive merged and sorted (that is what [TautZoneSet.limitSpans] returns), so the complement
 * is one walk: everything left of the first span, every gap between two of them, and everything right of
 * the last. An empty list is the whole leg, which is the ordinary case of a leg that enters no interior.
 */
internal fun outsideSpans(inside: List<SpanLimit>): List<Span> {
    if (inside.isEmpty()) return listOf(Span(0.0, 1.0))
    val out = ArrayList<Span>(inside.size + 1)
    var cursor = 0.0
    for (span in inside) {
        if (span.from > cursor + SPAN_EPS) out.add(Span(cursor, span.from))
        if (span.to > cursor) cursor = span.to
    }
    if (cursor < 1.0 - SPAN_EPS) out.add(Span(cursor, 1.0))
    return out
}

/**
 * **Whether the leg lies inside the coastal band, read at its middle.**
 *
 * The leg-shaped questions no longer come through here: a leg's limit is a length, and the band's own
 * source answers it as intervals ([`TautBand.spansOf`]) rather than as one boolean. What is left is the
 * one reading that really is about a leg as a whole — the dossier's "the route runs inside the band at
 * all" — and a drawn piece, being cut at the band's boundary, is wholly inside or outside it, so its
 * middle is an exact answer for it.
 */
internal fun inCoastalBand(world: TautWorld, a: RoutePoint, b: RoutePoint): Boolean =
    inCoastalBandAt(world, (a.latitude + b.latitude) / 2.0, (a.longitude + b.longitude) / 2.0)

/** The same question, asked of one point — the band's own query, and a vertex's own reference limit. */
internal fun inCoastalBandAt(world: TautWorld, latitude: Double, longitude: Double): Boolean =
    world.distanceToCoastM(latitude, longitude) <= world.coastalBandWidthM

/**
 * **The limit in force at a point** — the one reading a corner's reference speed is taken at (item 2).
 *
 * A zone's own limit where its **interior** covers the point (ring minus holes, read through the zone's
 * predicate), the band's own limit where the point hugs the shore, and `∞` where neither applies. It is
 * deliberately **not** the most restrictive limit over either leg: a leg that merely clips a zone is
 * priced over the clipped metres alone, and the corner at its end stands where the boat really is.
 * Sizing that corner from the leg is the maximum §17 item 1 rejects, read at the wrong place — it both
 * slows and eases a turn the water never held the boat to.
 */
internal fun limitInForceAt(world: TautWorld, zones: TautZoneSet, point: RoutePoint): Double {
    var limit = Double.MAX_VALUE
    if (inCoastalBandAt(world, point.latitude, point.longitude)) {
        limit = min(limit, world.coastalBandSpeedLimitKn)
    }
    val zone = zones.limitAt(point)
    return min(limit, zone)
}

/**
 * **The limit in force over a leg, and the price of it — read once, at the build.**
 *
 * The price is the whole point (§17 item 1): a leg's time is **summed over its own limit regions**, each
 * region's length at the speed in force there — the zone's legal limit inside a zone, the band's own
 * limit inside the band, the cruise everywhere else. A single limit read at the leg's middle (or, worse,
 * the most restrictive limit anywhere along it) is not the time a boat spends, and a maximum is *dearer*
 * than the line it prices, which is how the midpoint rule charged a leg clipping a zone at the zone's
 * limit over its whole length.
 *
 * That makes this class the one home of **two** answers, both read from the same region list: [regionsOf]
 * is the splitter (where the limit changes, and to what) and [priceSec] is the search's step and the
 * drawn clock's own sum. There is no separate band term and no aversion factor: the clock is the whole of
 * the price. The corner's reference speed is deliberately **not** a third one: it is the limit in force
 * at the vertex, read once per vertex at the build, never the dearest limit over a leg.
 *
 * **The band is a span source beside the zones, not a boolean beside them** (§18.1). Its boundary is a
 * distance rather than a ring, so it reaches this class as the leg's own inside intervals
 * ([`TautBand.spansOf`]) in exactly the shape [TautZoneSet.limitSpans] returns — and the two lists are
 * merged into one breakpoint list, so a piece lies wholly inside or outside each source by construction
 * and one membership question per piece is exact. The per-leg boolean that stood here asked the band's
 * question at the leg's **middle**, so a leg straddling the 300 m line was charged one value over both
 * halves: the last midpoint rule in the engine, and the one the epic's "each leg at the limit in force
 * over it" could not be read off.
 */
internal class LegLimits(
    private val world: TautWorld,
    private val zones: TautZoneSet,
    /** The band's own spans, or null when the band is switched off — a width of zero has no strip. */
    private val band: TautBand?,
    private val frame: Frame,
    private val cruiseSpeedKn: Double
) {

    /**
     * The leg's own regions, in order, each ending at the point the next one starts from.
     *
     * [nominalLimitKn] is the limit the caller's own line is being charged at where no zone and no band
     * applies: `∞` for a straight leg, whose limits all come from the world, and the corner's own speed
     * for an emitted chord, which the world knows nothing about.
     *
     * The breakpoints are the leg's own ends and **every** boundary of either source; between two
     * consecutive ones the piece stands wholly inside or outside each of them, so its limit is one
     * reading — the most restrictive of the sources it stands in, which is what "most restrictive where
     * two overlap" means once it is a length rather than a point.
     */
    fun regionsOf(a: RoutePoint, b: RoutePoint, nominalLimitKn: Double = Double.MAX_VALUE): List<LegRegion> =
        regionsOf(a, b, nominalLimitKn, zones.limitSpans(framePoint(a), framePoint(b)))

    /**
     * The same regions from a **span list already read** — the build's own door, so **one** computation of
     * the zone's intervals per candidate pair serves the price, the rule's entry test and the margin alike
     * (item 5 of the level above). The band's own spans are still read here, the band being a source the
     * build's pair loop does not otherwise ask about.
     */
    fun regionsOf(
        a: RoutePoint,
        b: RoutePoint,
        nominalLimitKn: Double,
        zoneSpans: List<SpanLimit>
    ): List<LegRegion> = regionsOf(a, b, nominalLimitKn, zoneSpans, bandSpansOf(a, b), Double.MAX_VALUE)

    /**
     * **The leg's price: its time summed over its own limit regions.** The same numbers the drawn clock
     * charges for the same pieces, because both read [regionsOf].
     */
    fun priceSec(a: RoutePoint, b: RoutePoint): Double = price(regionsOf(a, b))

    /** The same price over a span list the caller has already read — the build's own path. */
    fun priceOf(a: RoutePoint, b: RoutePoint, zoneSpans: List<SpanLimit>): Double =
        price(regionsOf(a, b, Double.MAX_VALUE, zoneSpans, bandSpansOf(a, b), Double.MAX_VALUE))

    /**
     * **The leg's price under the rule §18.1 removed** — the band resolved once, at the leg's own middle,
     * and charged over all of it.
     *
     * It is kept for one reader: the harness prices **one chain twice**, under the band's spans and under
     * this rule, so the gap between the two figures is the band's own share at fixed geometry rather than
     * the seconds a different line moves. The zones are still spans in both, so only the band's rule
     * differs — which is the quantity the reading is after.
     */
    fun midpointBandPriceSec(a: RoutePoint, b: RoutePoint): Double = price(
        regionsOf(
            a = a,
            b = b,
            nominalLimitKn = Double.MAX_VALUE,
            zoneSpans = zones.limitSpans(framePoint(a), framePoint(b)),
            bandSpans = emptyList(),
            flatBandLimitKn = midpointBandLimitKn(a, b)
        )
    )

    /** The one sum: a leg's regions at their own limits and the cruise pace. */
    private fun price(regions: List<LegRegion>): Double {
        var total = 0.0
        for (region in regions) {
            val seconds = RoutePlanTiming.legSeconds(region.lengthM, region.limitKn, cruiseSpeedKn)
            if (!seconds.isFinite()) return Double.POSITIVE_INFINITY
            total += seconds
        }
        return total
    }

    private fun framePoint(point: RoutePoint): Pt =
        frame.pt(LatLng(point.latitude, point.longitude))

    private fun bandSpansOf(a: RoutePoint, b: RoutePoint): List<Span> =
        band?.spansOf(framePoint(a), framePoint(b)) ?: emptyList()

    /** The band's limit where the leg's **middle** stands in it — the removed rule's own reading. */
    private fun midpointBandLimitKn(a: RoutePoint, b: RoutePoint): Double =
        if (band != null && inCoastalBand(world, a, b)) world.coastalBandSpeedLimitKn else Double.MAX_VALUE

    /**
     * The splitter itself, over breakpoints handed in rather than computed here: a piece is one limit once
     * the breakpoints of every source are in the list, and [flatBandLimitKn] carries the removed rule's own
     * shape — the band as **one value over the whole leg** — for the harness's two-price reading alone.
     */
    private fun regionsOf(
        a: RoutePoint,
        b: RoutePoint,
        nominalLimitKn: Double,
        zoneSpans: List<SpanLimit>,
        bandSpans: List<Span>,
        flatBandLimitKn: Double
    ): List<LegRegion> {
        val params = ArrayList<Double>(zoneSpans.size * 2 + bandSpans.size * 2 + 2)
        params.add(0.0)
        params.add(1.0)
        for (span in zoneSpans) {
            params.add(span.from)
            params.add(span.to)
        }
        for (span in bandSpans) {
            params.add(span.from)
            params.add(span.to)
        }
        params.sort()
        val bandLimit = world.coastalBandSpeedLimitKn
        val out = ArrayList<LegRegion>(params.size)
        var point = a
        for (i in 0 until params.size - 1) {
            val from = params[i]
            val to = params[i + 1]
            if (to - from <= SPAN_EPS) continue
            val mid = (from + to) / 2.0
            var limit = min(nominalLimitKn, flatBandLimitKn)
            for (span in bandSpans) {
                if (mid >= span.from && mid <= span.to) {
                    limit = min(limit, bandLimit)
                    break
                }
            }
            for (span in zoneSpans) {
                if (mid >= span.from && mid <= span.to) limit = min(limit, span.limitKn)
            }
            // The leg's own far end is named as itself rather than as a parameter reading of it, so the
            // drawn line ends where the vertex is and not a floating-point step away from it.
            val next = if (to >= 1.0 - SPAN_EPS) b else pointAt(a, b, to)
            out.add(LegRegion(to = next, limitKn = limit, lengthM = metresBetween(point, next)))
            point = next
        }
        if (out.isEmpty()) {
            out.add(LegRegion(b, min(nominalLimitKn, flatBandLimitKn), metresBetween(a, b)))
        }
        return out
    }
}

/** A vertex key rounded to ~1 cm, so two harvests of the same corner are one node. */
internal fun vertexKey(point: RoutePoint): Long {
    val lat = (point.latitude * 1e7).roundToLong()
    val lon = (point.longitude * 1e7).roundToLong()
    return (lat shl 21) xor (lon and 0x1FFFFF)
}

/**
 * The parameter-space slack of the two span readings — exactness, not a borrowed tolerance.
 *
 * A zone's interval is decided by the parameters at which the leg properly crosses its ring, so two
 * parameters closer than this are the same crossing to the arithmetic's own precision. It is **not**
 * `GRID_RES_M` and not a metre: the depth grid's resolution has nothing to say about the zone geometry,
 * and a per-pair sample would land inside the all-pairs loop the splitter exists to feed.
 */
private const val SPAN_EPS = 1e-12
