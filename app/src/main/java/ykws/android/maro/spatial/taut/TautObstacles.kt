package ykws.android.maro.spatial.taut

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import ykws.android.maro.data.depth.DepthConstants
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint

/** What a wall is: **land**, or the **2 m contour** where the soundings are fine. Both are geometry. */
internal enum class TautWallKind { LAND, CONTOUR }

/** One dilated obstacle: the ring the visibility test walks, and which side of it the water is on. */
internal class TautWall(
    val kind: TautWallKind,
    val closed: Boolean,
    val waterSign: Double,
    val points: List<LatLng>
)

/**
 * **The obstacle set, harvested for one corridor and abstracted safely.**
 *
 * Three things make a point unreachable, and only two of them are geometry:
 *
 * - **Land** — the coastline polylines, dilated outward by the berth.
 * - **Water shallower than the 2 m gate where the soundings are fine** — the depth feature's own 2 m
 *   contour, traced at its own fine-level rule (a shallow level is never faked from a coarse cell), and
 *   dilated the same way.
 * - **Water with no sounding at all, or with one too coarse to trace a contour** — resolved by the
 *   **distance from the coastline**: inside [shoreOffsetM] of it, the water is walled. That is the rule
 *   the design settled for "where the soundings stop, the shore supplies the wall", and the offset is
 *   the one key it added.
 *
 * A priced zone is deliberately **not** here: its interior is a rule of the search and its boundary a
 * price, never a wall, because a zone must stay crossable at its limit where a way around does not
 * exist. The zone rings are read by the graph itself, which is where the traversal rule lives.
 *
 * The abstraction is the design's licence: a wall no line may come within the berth of does not need its
 * detail below a quarter of that berth, so the ring is simplified **outward only**, then dilated by the
 * whole berth, and the result is checked by the **exact distance between segments** — never by sampling,
 * which is what let the retired string-pull miss a hole the gate had closed. What this costs is the
 * water the abstraction deletes, and it is a printed reading ([deletedAreaM2]) rather than an assumption.
 */
internal class TautObstacles private constructor(

    /** The corridor's own metres frame — one projection for every measurement in the search. */
    val frame: Frame,

    /**
     * **The extent of everything the harvest was handed**, in degrees, or null when it was handed
     * nothing.
     *
     * It is what the corridor is sized from (§16 step 2): the box must contain the obstacles it
     * harvested — the union of their own extents, clipped to the band — and that union is only
     * measurable where the shapes arrive, before the clip cuts them. The walls themselves answer a
     * different question (where the berth is), so the two are read from the two places that own them.
     */
    val harvestedExtent: BoundingBox?,

    /**
     * **The obstacles as they were harvested**, undilated — the licence a *curve* is held to.
     *
     * The two sets answer two different questions. A taut line keeps the berth, so the graph's edges are
     * tested against the **dilated** wall; a corner is rounded by cutting *towards* the inside of the
     * turn, which is exactly where the berth is — so a fit test against the dilated wall would refuse
     * every easing and leave the whole mechanism inert, which is the retired engine's own defect in
     * another dress. A rounded corner therefore **spends the berth** and is held to the water the boat
     * may actually use: land, and the 2 m contour where the soundings are fine.
     */
    val rawWalls: List<TautWall>,

    /** Every corner of every wall that **protrudes into the water** — the only kind a taut line bends on. */
    val corners: List<RoutePoint>,

    /** The water the abstraction and the dilation deleted, in m² — the number the rule is judged by. */
    val deletedAreaM2: Double,

    /** How many dilated segments could not be pushed clear of the abstraction's own tolerance. */
    val pushedShortSegments: Int,

    /** How many wall segments the visibility test walks in total. */
    val wallSegmentCount: Int,

    private val wallIndex: WallIndex,

    /** The same index over the undilated obstacles, for the curve's own fit test. */
    private val rawIndex: WallIndex,

    /** The berth (m) the exclusion geometry was dilated by — `route.zoneBerthM`. */
    val berthM: Double,

    /** The shore offset (m) standing in where the soundings stop — `route.shoreOffsetM`. */
    val shoreOffsetM: Double,

    private val world: TautWorld
) {

    // ── The judge's own memo, for the length of one harvest ───────────────────

    /**
     * **The judge's answers, remembered for one harvest** (§19.2 item 2 — measured, not assumed).
     *
     * Both questions behind [curveOnWater] are pure functions of the place (or the chord) and of the world,
     * and the search asks them about far fewer places than it asks *times*: a corner's ladder of candidates
     * shares its own ends, and every chord's two endpoints are points the point question sees as well. The
     * keys are **exact and never quantised**, because the water's answer is not constant over any cell a key
     * could round to.
     *
     * It is a **wager on repetition**, and the two counters beside it are how the wager is judged: a run
     * whose questions are mostly about new places pays the maps' own cost and gets nothing back.
     *
     * **Both maps are concurrent, and that is a rule rather than a preference** (§19.4, and the claim the
     * review corrected in §19.5 C1). A search is *started* one at a time but never *running* one at a time —
     * the predecessor is cancelled and not joined — and since §19.4 a corridor's terrain is **kept and
     * reused**, so the instance a later search reads is by construction the one an abandoned search may
     * still be writing: an attempt **does** read another's answers, and one map can be filled by two
     * searches at once. It is read in the two independent directions that makes honest — a question
     * answered by another search is answered, because a place has one water answer whoever asks, and a
     * plain map here would be a data race over state a cancelled search is still reading, which is the one
     * way this cache can corrupt an answer rather than merely slow one. A `ConcurrentHashMap` costs nothing
     * measurable and makes the sharing safe; a re-harvest of a grown box still starts empty, because a
     * grown box is a **new** terrain rather than a reused one.
     *
     * The **counters below are deliberately plain** for the same sharing read the other way: they are
     * instrumentation, and a lost increment under an overlap at worst misreports the memo's hit rate. It
     * is stated here rather than made atomic because an answer can never depend on them — measuring must
     * not become the cost being measured.
     */
    private val waterAnswers = ConcurrentHashMap<PointKey, Boolean>()

    /** The same memo for the chord question — see [curveBlocked], and [waterAnswers] for the sharing rule. */
    private val chordCrosses = ConcurrentHashMap<ChordKey, Boolean>()

    private var waterAsked = 0
    private var waterMemoHits = 0
    private var chordAsked = 0
    private var chordMemoHits = 0

    /**
     * **How many judge questions the run asked, and how many the memo answered without the world.**
     *
     * The pair is the whole finding of §19.2 item 2: it says whether the water's milliseconds are
     * *repeated work* — which a memo removes — or genuinely new work, which only a cheaper question or a
     * shorter candidate list can remove. A hit rate near zero is an answer, not a failed experiment.
     */
    val judgeAsked: Int get() = waterAsked + chordAsked
    val judgeMemoHits: Int get() = waterMemoHits + chordMemoHits

    /**
     * **The judge's two kinds, counted apart — the split §19.3's row c is chosen on** (walk item 5).
     *
     * The point query and the chord walk cost different things and answer different questions: the point
     * is the coast index's own rule over the depth grid's sounding, the chord is one walk of the wall
     * index's own cells. Priced apart on the acceptance pair they read ≈ 4.9 µs against ≈ 0.37 µs, and
     * **92 % of the water's milliseconds are in the points** — so a lever on the chords is worth less than
     * it looks, and the one that was built is reported as what it is: the point questions the early exit
     * of [curveOnWater] stops asking, never a cheaper chord.
     */
    val pointsAsked: Int get() = waterAsked
    val chordsAsked: Int get() = chordAsked

    /**
     * Whether the field lets the boat stand here.
     *
     * The three-way rule of the class note, read at a point: land is out; a **trusted** sounding is out
     * below the 2 m gate; an untrusted one — no data, or a source too coarse to contour — is out when the
     * point is inside the shore offset. It is a query rather than a polygon test on purpose: the contour
     * supplies the *geometry* (dilated, for the graph to bend on) and this supplies the *field*, and the
     * two cannot disagree about where the gate is because they read the same number and the same rule.
     */
    fun traversable(latitude: Double, longitude: Double): Boolean {
        val key = PointKey(latitude, longitude)
        waterAsked++
        waterAnswers[key]?.let {
            waterMemoHits++
            return it
        }
        val answer = fieldLetsTheBoatStand(latitude, longitude)
        waterAnswers[key] = answer
        return answer
    }

    /**
     * The field's own answer, unmemoised — see [traversable], which is the door every caller uses.
     *
     * **The second coast question is the point walk's own, and that is where item 12's lever was taken**
     * (§19.8): the capped radius question this branch once asked was priced, found **dearer** than the
     * distance it replaced on the water where the mass of the questions sits, and refused — while the walk
     * both branches go through was made to visit each grid cell once, which is the lever that landed. So
     * the branch reads as it always did, and its cost is the index's own.
     */
    private fun fieldLetsTheBoatStand(latitude: Double, longitude: Double): Boolean {
        if (!world.isWater(latitude, longitude)) return false
        val sounding = world.depthSampleAt(latitude, longitude)
        val trusted = sounding.hasData && sounding.source.nominalResM <= FINE_RES_M
        return if (trusted) {
            sounding.depthM.toDouble() >= SHALLOW_GATE_M
        } else {
            world.distanceToCoastM(latitude, longitude) > shoreOffsetM
        }
    }

    /**
     * Whether the straight line between two points crosses a wall.
     *
     * **A touch is not a crossing.** The line the graph draws bends *on* wall corners, so two consecutive
     * legs share that corner with the wall by construction; a closed intersection test would refuse the
     * only line the construction exists to produce. The test is therefore the strict interior crossing,
     * and the open-water question is asked separately, of the line's own midpoint.
     */
    fun crossesWall(a: RoutePoint, b: RoutePoint): Boolean {
        val first = frame.pt(LatLng(a.latitude, a.longitude))
        val second = frame.pt(LatLng(b.latitude, b.longitude))
        return wallIndex.crossedBy(first, second)
    }

    /**
     * Whether the drawn curve between two points leaves the water — the easing's own fit test.
     *
     * It is asked of the **undilated** obstacles, for the reason [rawWalls] gives: rounding a corner is
     * spending the berth, and the one thing it may never spend is the water itself.
     */
    fun curveBlocked(a: RoutePoint, b: RoutePoint): Boolean {
        val key = ChordKey(a.latitude, a.longitude, b.latitude, b.longitude)
        chordAsked++
        chordCrosses[key]?.let {
            chordMemoHits++
            return it
        }
        val first = frame.pt(LatLng(a.latitude, a.longitude))
        val second = frame.pt(LatLng(b.latitude, b.longitude))
        val answer = rawIndex.crossedBy(first, second)
        chordCrosses[key] = answer
        return answer
    }

    /**
     * **Whether a candidate curve stands on water the boat may use — each point asked with the chord it
     * leaves, so the first refusal ends the walk.**
     *
     * The two questions are an **AND**, so their order cannot change the answer: each is a pure function of
     * the place (or the chord) and the world, and the memo behind it is exact-keyed, so a curve is refused
     * by the same question whether the walk reaches it first or last. What the order decides is **how many
     * questions a refused curve ever pays for**, and asking the chord with the point it leaves means a wall
     * refuses at that chord rather than after the whole point walk — the places past it are never asked
     * about. On the acceptance pair that is **18 819 point questions** — harvested **95 627 → 76 808** —
     * against the price's **22 % · 15 % of the water's milliseconds** and **19 % · 11 % of the search**
     * (§19.3's row c, priced as candidate **B** and built as walk item 5), with the six metrics and the
     * drawn line identical to the digit — identical *by construction* rather than by measurement, which is
     * what §19.3's guard on this row rests on. The chords move **the other way and far smaller**, +2 173:
     * a chord is now asked *before* the point that would have ended the walk, and at a seventh of a point
     * query's cost that trade is not close.
     *
     * The counters move with it — fewer point questions mean fewer memo entries — so [pointsAsked],
     * [chordsAsked], [judgeAsked] and [judgeMemoHits] all ride the sampling and none of them can be what
     * this change is accepted on: they **pre-screen** whether there is anything to measure, and the six
     * metrics and the drawn line are the guard.
     */
    fun curveOnWater(points: List<RoutePoint>): Boolean {
        for (i in points.indices) {
            val point = points[i]
            if (!traversable(point.latitude, point.longitude)) return false
            if (i < points.size - 1 && curveBlocked(point, points[i + 1])) return false
        }
        return true
    }

    /**
     * The corridor's obstacles, harvested from the world and made safe.
     *
     * The tolerance is a fraction of the berth — `TOLERANCE_FRACTION`, stated where it is read — and the
     * threshold for "a fine sounding" is the depth feature's own, so the tracer inherits the depth layer's
     * distrust of a coarse cell rather than contradicting it.
     */
    companion object {

        /**
         * The shallow gate: water shallower than this is out where the sounding is trusted.
         *
         * **The tracer carries its own 2 m** rather than the low-depth warning pair, which answers a
         * different question (where a boat should be *warned*, not where it may *not* go), and rather
         * than the mesh's 2.5 m bake gate, which is the retired engine's own number. [ISOBATH_LEVELS]
         * already includes 2 m, so this is the same level the depth feature draws.
         */
        const val SHALLOW_GATE_M = 2.0

        /** A sounding is trusted where its source resolution is at most this — the depth feature's rule. */
        val FINE_RES_M: Double = DepthConstants.ISOBATH_FINE_MAX_RES_M

        /**
         * The abstraction's tolerance as a fraction of the berth — **half the berth, and it is a ceiling as
         * much as a value.**
         *
         * A wall may only ever **grow** with this number, so the drawn route stands further off the land
         * than the exact answer and never closer: the error is always the safe way. But the same number is
         * what the wall's clearance is measured down from — a harvested corner stands `berth − tolerance`
         * off the undilated obstacle it came from, which `TautAssertionsTest` asserts — so a tolerance of
         * one berth would spend the berth entirely and leave the guarantee vacuous. That assertion is the
         * ceiling, and it is not a formality: raising this to **1.0** in a latency experiment made it fail.
         *
         * **And it is a weak lever on the search, measured rather than assumed.** On the acceptance pair,
         * doubling it from a half (12.5 m) to one (25 m) took the vertices from 431 to 391 and the search
         * from 1 181 ms to 967; doubling again to 50 m took them to 366 and 814 ms, while the water the
         * abstraction deletes grew 1.47 → 1.52 → 1.71 km² and the drawn line gained a sharp corner and
         * twenty degrees of turning. A search near half a second is therefore **not** reachable this way:
         * the curve flattens long before the cost is paid, and the per-corner easing work inside the search
         * — not the vertex count — is what the search's milliseconds are actually made of.
         */
        const val TOLERANCE_FRACTION = 0.5

        /** How far from a ring the water side is probed, on top of the dilation (m). */
        private const val PROBE_MARGIN_M = 40.0

        /** The visibility grid's cell (m) — a few times the berth, so a lookup walks a handful of cells. */
        private const val INDEX_CELL_M = 100.0

        fun harvest(
            world: TautWorld,
            box: BoundingBox,
            berthM: Double,
            shoreOffsetM: Double,
            /**
             * **Asked between shapes, so an abandoned harvest stops rather than finishing** (§19.2 item 4).
             *
             * The engine passes a coroutine's own check here and into the graph's pair scan: a cancel is
             * otherwise only seen *between* the phases, which means a drag waits for a whole harvest or a
             * whole 92 665-pair build before the newest aim can be served.
             */
            cancelCheck: () -> Unit = {}
        ): TautObstacles {
            val frame = Frame(box.centerLat, box.centerLon)
            val tolerance = berthM * TOLERANCE_FRACTION
            val probeM = max(berthM * 2.0 + PROBE_MARGIN_M, PROBE_MARGIN_M)
            val boxM = BoxM(
                minX = frame.x(box.lonWest),
                maxX = frame.x(box.lonEast),
                minY = frame.y(box.latSouth),
                maxY = frame.y(box.latNorth)
            )

            val walls = ArrayList<TautWall>()
            val rawWalls = ArrayList<TautWall>()
            val corners = ArrayList<RoutePoint>()
            var deletedAreaM2 = 0.0
            var shortSegments = 0

            // Everything the harvest was handed is measured before it is clipped: the union of these
            // extents is the box's own licence to hold them (§16 step 2), and a shape the clip cut would
            // no longer say how far it reached.
            var extentSouth = Double.MAX_VALUE
            var extentNorth = -Double.MAX_VALUE
            var extentWest = Double.MAX_VALUE
            var extentEast = -Double.MAX_VALUE

            fun absorb(points: List<LatLng>) {
                for (point in points) {
                    if (point.latitude < extentSouth) extentSouth = point.latitude
                    if (point.latitude > extentNorth) extentNorth = point.latitude
                    if (point.longitude < extentWest) extentWest = point.longitude
                    if (point.longitude > extentEast) extentEast = point.longitude
                }
            }

            val harvested = ArrayList<Pair<TautWallKind, List<LatLng>>>()
            for (polyline in world.landPolylinesIn(box)) {
                absorb(polyline)
                if (polyline.size >= 2) harvested.add(TautWallKind.LAND to polyline)
            }
            for (contour in world.shallowContoursIn(box)) {
                absorb(contour)
                if (contour.size >= 2) harvested.add(TautWallKind.CONTOUR to contour)
            }
            // A priced zone is not a wall, but it is an obstacle the corridor must contain: a ring the
            // box cut would leave the graph a zone whose own boundary it cannot bend around.
            for (shape in world.zoneShapesIn(box)) {
                absorb(shape.outerRing)
                for (hole in shape.holes) absorb(hole)
            }
            val extent = if (extentSouth <= extentNorth && extentWest <= extentEast) {
                BoundingBox(extentSouth, extentNorth, extentWest, extentEast)
            } else {
                null
            }

            // Each shape is clipped to the corridor before it is measured, so the region's own coastline
            // costs a few kilometres of work rather than a hundred — and the clipping's ends are safe,
            // the corridor being convex.
            for ((kind, polyline) in harvested) {
                cancelCheck()
                for (piece in clipPolyline(frame.pts(polyline), boxM)) {
                    if (piece.size < 2) continue
                    // A shape with no length is not a wall: repeated vertices are common in coastline
                    // data, and a piece made only of them would give the offset nothing to stand on.
                    if (polylineLength(piece) < GEOMETRY_EPS_M) continue
                    val closed = piece.size >= 4 && piece.first() == piece.last()
                    val waterSign = waterSignOf(piece, frame, kind, probeM, world)
                    val simplified = simplifyOutward(piece, tolerance, waterSign)
                    val dilated = dilateOutward(simplified, berthM, waterSign, closed)
                    val (validated, short) = validateDilation(
                        piece, dilated, berthM, tolerance, waterSign, closed
                    )
                    if (validated.size < 2) continue
                    if (short > 0) shortSegments++
                    deletedAreaM2 += deletedArea(piece, validated, closed)
                    walls.add(TautWall(kind, closed, waterSign, frame.latLngs(validated)))
                    rawWalls.add(TautWall(kind, closed, waterSign, frame.latLngs(piece)))
                    corners.addAll(cornersOf(validated, waterSign, frame, closed, box))
                }
            }

            val index = WallIndex(walls, frame)
            val rawIndex = WallIndex(rawWalls, frame)
            return TautObstacles(
                frame = frame,
                harvestedExtent = extent,
                rawWalls = rawWalls,
                corners = corners,
                deletedAreaM2 = deletedAreaM2,
                pushedShortSegments = shortSegments,
                wallSegmentCount = index.segmentCount,
                wallIndex = index,
                rawIndex = rawIndex,
                berthM = berthM,
                shoreOffsetM = shoreOffsetM,
                world = world
            )
        }

        /**
         * Which side of the ring the water lies on — `+1` left of travel, `-1` right.
         *
         * The direction is **known rather than guessed**, as the design demands: for land and for the
         * contour the two sides are probed [probeM] out from the ring's longest segment and the water
         * side is the one that reads as water (for the contour, the deeper one); where both sides read
         * alike — a contour line with no sounding either side — the tie goes to the side further from the
         * coastline, which is the direction away from land the coastline index already answers.
         */
        private fun waterSignOf(
            polyline: List<Pt>,
            frame: Frame,
            kind: TautWallKind,
            probeM: Double,
            world: TautWorld
        ): Double {
            var bestIndex = 0
            var longest = -1.0
            for (i in 0 until polyline.size - 1) {
                val a = polyline[i]
                val b = polyline[i + 1]
                val length = hypot(b.x - a.x, b.y - a.y)
                if (length > longest) {
                    longest = length
                    bestIndex = i
                }
            }
            if (longest <= 0.0) return -1.0
            val a = polyline[bestIndex]
            val b = polyline[bestIndex + 1]
            val mid = Pt((a.x + b.x) / 2.0, (a.y + b.y) / 2.0)
            val dx = (b.x - a.x) / longest
            val dy = (b.y - a.y) / longest
            val left = frame.latLng(Pt(mid.x - dy * probeM, mid.y + dx * probeM))
            val right = frame.latLng(Pt(mid.x + dy * probeM, mid.y - dx * probeM))
            val leftWater = readsAsWater(left, kind, world)
            val rightWater = readsAsWater(right, kind, world)
            if (leftWater != rightWater) return if (leftWater) 1.0 else -1.0
            val leftShore = world.distanceToCoastM(left.latitude, left.longitude)
            val rightShore = world.distanceToCoastM(right.latitude, right.longitude)
            return if (leftShore >= rightShore) 1.0 else -1.0
        }

        /** Whether a probe point reads as water for a ring of [kind] — deeper, further out, or just water. */
        private fun readsAsWater(point: LatLng, kind: TautWallKind, world: TautWorld): Boolean =
            when (kind) {
                TautWallKind.LAND -> world.isWater(point.latitude, point.longitude)
                TautWallKind.CONTOUR -> {
                    val sample = world.depthSampleAt(point.latitude, point.longitude)
                    if (sample.hasData) sample.depthM.toDouble() >= SHALLOW_GATE_M
                    else world.isWater(point.latitude, point.longitude)
                }
            }

        /** Every corner of [ring] that protrudes into the water, kept inside the corridor's box. */
        private fun cornersOf(
            ring: List<Pt>,
            waterSign: Double,
            frame: Frame,
            closed: Boolean,
            box: BoundingBox
        ): List<RoutePoint> {
            val out = ArrayList<RoutePoint>()
            // A ring is closed on itself, so its own first vertex is a corner like any other: the walk
            // starts at it and stops before the repeated closing vertex, reaching every distinct index
            // once. Starting at 1 would drop the island's south-west tangent point and, with it, the
            // whole way round that side.
            val first = if (closed) 0 else 1
            val last = ring.size - 1
            for (i in first until last) {
                val previous = ring[(i - 1 + ring.size) % ring.size]
                val next = ring[(i + 1) % ring.size]
                if (!protrudesIntoWater(previous, ring[i], next, waterSign)) continue
                val point = frame.latLng(ring[i])
                if (point.latitude < box.latSouth || point.latitude > box.latNorth) continue
                if (point.longitude < box.lonWest || point.longitude > box.lonEast) continue
                out.add(RoutePoint(point.latitude, point.longitude))
            }
            return out
        }

        /**
         * The water the wall deletes: the area between the harvested shape and the wall drawn around it.
         *
         * Each polyline is closed with its own ends and the two areas are differenced — the ribbon the
         * abstraction and the dilation took out of the water. It is an area and not a count on purpose:
         * a coarser ring is safe by construction, and this is the number that says what safe cost.
         */
        private fun deletedArea(source: List<Pt>, dilated: List<Pt>, closed: Boolean): Double {
            if (source.size < 2 || dilated.size < 2) return 0.0
            if (closed) {
                val sourceRing =
                    if (source.first() != source.last()) source + source.first() else source
                val dilatedRing =
                    if (dilated.first() != dilated.last()) dilated + dilated.first() else dilated
                if (sourceRing.size < 3 || dilatedRing.size < 3) return 0.0
                return abs(polygonAreaM2(dilatedRing) - polygonAreaM2(sourceRing))
            }
            // The ribbon between an open shape and its own dilated copy: out along the wall and back
            // along the water, which is a closed polygon whose area is the water the wall took.
            val ribbon = source + dilated.asReversed()
            if (ribbon.size < 3) return 0.0
            return abs(polygonAreaM2(ribbon))
        }
    }
}

/**
 * **The visibility index over the dilated walls.**
 *
 * The corridor's wall set is the region's coastline, which is thousands of segments, and the graph asks
 * the crossing question once per candidate edge — so the segments are bucketed into a uniform grid and a
 * query walks **only the cells the line itself passes through**. Nothing about the answer depends on the
 * bucket order: the test is an existence question.
 *
 * **The walk is the lever §17 item 7 sanctions, and it is exact.** The built index covered the query
 * segment's **bounding box** — up to a hundred cells where a diagonal line touches twenty — and deduped
 * the segments it met in a fresh `HashSet` per pair, which the eager all-pairs loop pays 780 000 times.
 * The walk below enumerates the cells the line actually visits (a DDA step through the grid) and marks
 * what it has seen in a stamp array, so the per-pair cost is the line's own length in cells rather than
 * the box's area, and nothing can be missed: a wall segment bucketed in any cell the query touches is in
 * that cell's bucket, and the walk visits every cell the query passes through.
 */
/**
 * **A point's exact identity as a map key** — both doubles, bit for bit, and never a rounded cell.
 *
 * The water's answer is not constant over any cell a key could round to, so a quantised key would answer
 * about a *neighbouring* place: a memo is only allowed to be forgetful, never approximate.
 */
private data class PointKey(val latitude: Double, val longitude: Double)

/** **A chord's exact identity as a map key** — its two ends, bit for bit, in the order they were asked. */
private data class ChordKey(
    val fromLatitude: Double,
    val fromLongitude: Double,
    val toLatitude: Double,
    val toLongitude: Double
)

private class WallIndex(private val walls: List<TautWall>, frame: Frame) {

    private val segments = ArrayList<Pair<Pt, Pt>>()
    private val cells = HashMap<Long, MutableList<Int>>()
    private var minX = 0.0
    private var minY = 0.0

    /** One stamp per segment, so a pair's dedupe needs no allocation — see [crossedBy]. */
    private var seen = IntArray(0)

    /**
     * **One stamp per query, taken atomically** (§19.5 C1) — the dedupe mark a query writes into [seen].
     *
     * This index is shared: since §19.4 a corridor's terrain is kept and reused, so the search that owns
     * it and the cancelled, unjoined predecessor the engine no longer waits for read the same instance.
     * A stamp handed out twice — which a plain `stamp++` does whenever two threads interleave its read
     * and its write — would make one query read the other's marks as its own and **skip the very segment
     * that crosses the line**, which is a leg over land accepted as an answer. `incrementAndGet` gives
     * every query a stamp of its own, so a mark left in [seen] by another query is never equal to this
     * one's and the segment is simply re-tested; the marks may be overwritten, which costs a repeated
     * test, and can never lose one.
     */
    private val stamp = AtomicInteger(0)

    init {
        for (wall in walls) {
            val points = wall.points
            val last = if (wall.closed) points.size else points.size - 1
            for (i in 0 until last) {
                val a = frame.pt(points[i])
                val b = frame.pt(points[(i + 1) % points.size])
                segments.add(a to b)
                if (segments.size == 1) {
                    minX = min(a.x, b.x)
                    minY = min(a.y, b.y)
                } else {
                    minX = min(minX, min(a.x, b.x))
                    minY = min(minY, min(a.y, b.y))
                }
            }
        }
        for ((index, segment) in segments.withIndex()) {
            forEachGridCell(segment.first, segment.second, minX, minY) { cell, _, _ ->
                cells.getOrPut(cell) { ArrayList(2) }.add(index)
            }
        }
        seen = IntArray(segments.size)
    }

    val segmentCount: Int get() = segments.size

    fun crossedBy(a: Pt, b: Pt): Boolean {
        if (segments.isEmpty()) return false
        val query = stamp.incrementAndGet()
        var hit = false
        forEachGridCell(a, b, minX, minY) { cell, _, _ ->
            if (!hit) {
                val bucket = cells[cell]
                if (bucket != null) {
                    for (index in bucket) {
                        if (seen[index] == query) continue
                        seen[index] = query
                        val segment = segments[index]
                        if (properlyCrosses(a, b, segment.first, segment.second)) {
                            hit = true
                            break
                        }
                    }
                }
            }
        }
        return hit
    }
}
