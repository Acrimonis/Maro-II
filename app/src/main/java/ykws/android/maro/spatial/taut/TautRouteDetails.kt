package ykws.android.maro.spatial.taut

import ykws.android.maro.data.model.RouteEngineDetails

/**
 * **The tracer's own readings about the route it answered** — its dossier, and nothing the plan needs.
 *
 * It is the counterpart of the mesh engine's own, and it is deliberately a *different* set: a corridor
 * tracer has no mesh vertices, no chain to price against and no fillet, and it has numbers the mesh never
 * had — the corridor's own vertex, edge and wall counts, the pairs its visibility loop examined, the area
 * the abstraction deletes, the corners the easing had to refuse, and the precision its chord emission
 * costs.
 *
 * **Its cost is reported in three parts rather than one** (§16 step 1). The harvest, the graph build and
 * the search are different machines — the world's own queries, an all-pairs visibility loop, and an A\*
 * — and a single "search milliseconds" folded the three together and named the last of them, which is
 * what made a 3 653 ms answer unreadable. [harvestMillis], [graphMillis] and [searchMillis] are those
 * three, with [candidatePairs] and [vertexCount] beside them so the cost can be read against the size of
 * the thing that produced it.
 *
 * They are instrumentation and stay instrumentation: the harness reads them to judge the engine, and no
 * reader of the plan — the trip figure, the panel, the saved track — has a use for one.
 */
internal data class TautRouteDetails(

    /** The graph's own size: vertices (the start, the aim and every corner the line may bend on). */
    val vertexCount: Int = 0,

    /** How many of those vertices are harvested obstacle corners rather than the route's two ends. */
    val cornerCount: Int = 0,

    /** The visibility edges the water allowed. */
    val edgeCount: Int = 0,

    /**
     * How many vertex pairs the visibility loop examined — the quadratic term's own size.
     *
     * It is reported because it is the build's cost in the only unit that is stable across machines: the
     * wall index and the middle's water test are asked once per pair, so the pair count says what the
     * build is paying for before any timing is believed.
     */
    val candidatePairs: Int = 0,

    /** The dilated wall segments the visibility test walked — the map's own weight. */
    val wallSegmentCount: Int = 0,

    /**
     * **The coastline segments the band's own span source holds** — the scale of what the splitter's
     * band side pays (§18.1 step 7). Zero when the band is switched off.
     */
    val bandSegmentCount: Int = 0,

    /**
     * How many dilated segments had to be pushed further out to clear the abstraction's own tolerance.
     * Zero is the expected reading, and a number here says the one-sided abstraction could not be honoured
     * at that seam.
     */
    val pushedShortSegments: Int = 0,

    /**
     * **The water the abstraction and the dilation deleted** (m²) — the number the rule is judged by. A
     * wall that may only ever grow is safe by construction, and this is what safety cost.
     */
    val deletedAreaM2: Double = 0.0,

    /** The seconds the search itself accumulated — the ceiling the drawn line may not exceed. */
    val pricedSec: Double = 0.0,

    /**
     * How much of [pricedSec] is the **berth's courtesy** rather than the clock (§15).
     *
     * The zone margin is priced in seconds — a leg inside it pays extra time rising as the boundary nears
     * — so it is part of what the search minimises, and it is **not** part of what the trip will take.
     * Reported apart for exactly that reason: the drift between the drawn clock and the accumulated price
     * is the berth plus §12.3's arc savings, and neither is visible while the two are one number.
     */
    val berthPriceSec: Double = 0.0,

    /** The seconds §12.3's brake-and-accelerate pair cost over the whole route, on the drawn clock too. */
    val longitudinalSec: Double = 0.0,

    /** How many drawn legs entered a priced zone's interior — §16 step 3's crossing count. */
    val crossings: Int = 0,

    /**
     * Metres of the route's own legs inside a zone's margin and outside every zone — §16 step 3's
     * reading, and **the legs are the ones the margin's price is paid on** (item 4): the search's own
     * chain edges, never the drawn line's shorter pieces.
     */
    val inMarginM: Double = 0.0,

    /**
     * The limit the engine produced for each drawn leg, one per leg — the values handed to the app's own
     * clock ([`ykws.android.maro.spatial.RoutePlanTiming.drawnLegSeconds`]), so the supply is a reading
     * rather than a claim about the code.
     */
    val legLimitKn: List<Double> = emptyList(),

    /** The corners drawn as an easing spiral, as a plain arc, and not at all. */
    val spiralCorners: Int = 0,
    val arcCorners: Int = 0,
    val sharpCorners: Int = 0,

    /**
     * **The sharp corners' causes, counted apart** (§17 item 2, five causes since item 2 of the level
     * above).
     *
     * The guard, a curve that cannot meet its own legs, the cutback against a leg too short, the water,
     * and the run's own rule — only the water's is the water's own answer, and only for it is the charge
     * a bound read off a failed radius at all. A rule refusal carries no charge, which is exactly why it
     * has its own number rather than being counted with the water.
     */
    val sharpByGuard: Int = 0,
    val sharpByBuild: Int = 0,
    val sharpByCutback: Int = 0,
    val sharpByWater: Int = 0,
    val sharpByRule: Int = 0,

    /**
     * **The seconds the sharp corners' own slowdown came to** — the charge §17 item 2 exists to make.
     *
     * It is the largest single term in item 4's re-read, so it is printed rather than left inside the
     * ETA: a figure of its size is the user's to accept knowingly.
     */
    val sharpChargeSec: Double = 0.0,

    /** The largest radius a corner actually held (m) — 0 when no arc fitted anywhere. */
    val largestTurnRadiusM: Double = 0.0,

    /** The largest deviation between the emitted chords and the curve they describe (m). */
    val chordDeviationM: Double = 0.0,

    /** The seconds the chord emission costs against the curve it stands for. */
    val samplingSec: Double = 0.0,

    /** True when the answer came from the second run — zones priced rather than forbidden. */
    val zonePricedRun: Boolean = false,

    /** How many times the corridor's rough guess had to be grown before a route was found. */
    val corridorGrowth: Int = 0,

    /** True when the corridor was sized from the obstacles its own harvest found — §16 step 2. */
    val corridorExpanded: Boolean = false,

    /**
     * **True when the terrain that produced this answer was one the engine had already harvested**
     * (§19.4) — the readable half of b1, whose whole point is that it changes nothing a reader of the
     * line can see.
     *
     * It is the **answering attempt's** flag and not the loop's history (§19.5 C6): a reuse whose own
     * search finds no way is followed by a grown attempt that harvests afresh, and the dossier must
     * describe the attempt the line came from — a flag left true across that boundary would put "kept"
     * beside a harvest that ran in full, which is exactly the pair this field exists to make unreadable.
     *
     * The change is invisible, so the reading that proves it *happened* is the pair this flag makes with
     * [harvestMillis]: the flag says the corridor's coast was kept, and the milliseconds beside it say
     * what was not paid for it. A second search of one engine on a corridor the kept terrain holds answers
     * `true` and reads a harvest of milliseconds; a cold engine on the same aim answers `false` and reads
     * the full coast work. Neither number means anything alone.
     */
    val terrainReused: Boolean = false,

    /** The obstacle harvest's own wall clock (ms) — `TautTerrain.of` plus any re-harvest, 0 on a reuse. */
    val harvestMillis: Long = 0,

    /** The graph's wall clock (ms) — the all-pairs visibility loop, the zone harvest having moved out. */
    val graphMillis: Long = 0,

    /** The search's wall clock (ms) — the A\* alone, and the figure the incumbent's worst case meets. */
    val searchMillis: Long = 0,

    /**
     * **The inside of a station, priced** (§19.2 item 1) — the corner curves the search actually built and
     * the milliseconds they cost, and the drawn-clock evaluations each candidate paid for with what those
     * cost.
     *
     * They answer the question a phase figure cannot: whether the search's seconds are in *doing less work*
     * (stations, candidate turns) or in *making the work cheaper* (the fit and the clock). The vertex levers
     * and the per-corner lever are chosen on that answer rather than on an estimate — which is how the
     * abstraction tolerance was found to be a weak lever after it had been assumed a strong one.
     */
    val fitsBuilt: Int = 0,
    val fitMillis: Long = 0,
    val clockCalls: Int = 0,
    val clockMillis: Long = 0,

    /**
     * **And the fit's own inside** — the candidates the ladder built, and the milliseconds spent drawing
     * them against the milliseconds spent judging them against the water (§19.2 item 2).
     *
     * They point at different fixes, so they are counted apart: a dear draw wants a cheaper curve, a dear
     * judge wants a cheaper water test. This is the level at which a latency target can actually be aimed
     * rather than guessed at.
     */
    val candidatesBuilt: Int = 0,
    val candidateBuildMillis: Long = 0,
    val candidateWaterMillis: Long = 0,

    /**
     * **How much of [candidateWaterMillis] was repeated work** (§19.2 item 2) — the judge questions the
     * harvest asked, and the ones its own memo answered without consulting the world.
     *
     * A hit rate near zero is itself the finding: the water's milliseconds are then *new* work per
     * candidate, and no memo can remove them — only a cheaper question or a shorter candidate list can.
     * Printed beside the two figures above for exactly that verdict.
     */
    val judgeAsked: Int = 0,
    val judgeMemoHits: Int = 0,

    /** The tracer's own A* count — see [`RouteEngineDetails.nodesExpanded`]. */
    override val nodesExpanded: Int = 0
) : RouteEngineDetails
