package ykws.android.maro.spatial.multipass

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.abs
import kotlin.math.hypot

/** A rock's slow disc pins an exit north of the destination and a needless bend far out at sea. */
class RouteApproachPassTest {
    private val rock = point(0.0, 0.0)
    private val original = listOf(point(0.0, 150.0), point(0.0, 310.0), point(2500.0, 200.0), point(3000.0, -900.0))
    private val bounds = GateTestFixtures.box(200.0, 2200.0, 0.0, 3600.0)

    @Test
    fun movesTheSlowCrossingAndRemovesTheOpenWaterBendTogether() {
        val setup = setup(field())
        val swept = MultipassPull.sweep(setup, original, allowanceShare = 0.1, traceLive = false)
        assertEquals("deletion alone cannot improve this approach", original, swept)
        val result = RouteApproachPass.refine(setup, swept, bounds, 20.0)

        assertTrue("the offshore bend is gone", result.size < swept.size)
        assertTrue("a new approach point was found", result.any { it !in original })
        assertTrue("moving the approach saves meaningful time", cost(setup, result) < cost(setup, original) - 5.0)
        assertSafeImprovement(setup, result)
    }

    @Test
    fun keepsCoastClearanceEvenWhenACheaperApproachWouldCrossAnotherRock() {
        val freeSetup = setup(field())
        val free = RouteApproachPass.refine(freeSetup, original, bounds, 20.0)
        val obstacle = between(free[1], free.last(), 0.3)
        val guarded = setup(field(extraHard = RouteCostSource.Hard(distanceAt = {
            (SpatialOperations.haversine(it, obstacle) - 60.0).coerceAtLeast(0.0)
        })))
        assertClear(guarded, original)
        assertFalse("the tempting replacement crosses the added rock", clear(guarded, free))

        assertSafeImprovement(guarded, RouteApproachPass.refine(guarded, original, bounds, 20.0))
    }

    @Test
    fun keepsTheMeasuredDepthWall() {
        val free = RouteApproachPass.refine(setup(field()), original, bounds, 20.0)
        val shoal = between(free[1], free.last(), 0.3)
        val shoalNorth = (shoal.latitude - GateTestFixtures.LAT0) * GateTestFixtures.M_PER_DEG_LAT
        val shoalEast = (shoal.longitude - GateTestFixtures.LON0) * GateTestFixtures.M_PER_DEG_LON
        val raster = GateTestFixtures.raster(130, 190, 20.0) { n, e ->
            if (hypot(n - shoalNorth, e - shoalEast) < 80.0) 2.0 else 15.0
        }
        val depth = GateTestFixtures.depthOf(raster)
        val gate = GateTestFixtures.gateOver(raster, hardM = 20.0)
        val guarded = setup(field(extraHard = depthGateSource(3.0) { depth(it.latitude, it.longitude) }), gate)
        assertClear(guarded, original)
        assertFalse("the tempting replacement crosses the measured shoal", clear(guarded, free))

        assertSafeImprovement(guarded, RouteApproachPass.refine(guarded, original, bounds, 20.0))
    }

    @Test
    fun preservesBothEndsAndFindsTheSameApproachInEitherDirection() {
        val setup = setup(field())
        val result = RouteApproachPass.refine(setup, original, bounds, 20.0)
        assertEquals(result, RouteApproachPass.refine(setup, original, bounds, 20.0))
        assertEquals(result.reversed(), RouteApproachPass.refine(setup.reversed(), original.reversed(), bounds, 20.0))
        assertEquals(original.first(), result.first())
        assertEquals(original.last(), result.last())
        assertTrue(result.all { inBox(bounds, it) })
    }

    @Test
    fun keepsCandidateMovesInsideTheCorridor() {
        // Candidates north of the old approach are outside this tighter corridor.
        val tight = bounds.copy(latNorth = original[1].latitude)
        val setup = setup(field())
        val result = RouteApproachPass.refine(setup, original, tight, 20.0)
        assertTrue(result.all { inBox(tight, it) })
        assertSafeImprovement(setup, result)
    }

    @Test
    fun markerPricesAloneDoNotTriggerSlowWaterRefinement() {
        val setup = setup(field(arm = PriceArm.MARKER))
        val trace = mutableListOf<String>()
        assertEquals(original, RouteApproachPass.refine(setup, original, bounds, 20.0, trace = { trace += it() }))
        assertTrue(trace.single().contains("candidates=0"))
    }

    /**
     * **The pass's line says where its time and reads went, and changes nothing it reports on.** Every window
     * evaluates its seed once and each candidate once, a refusal is one of those evaluations, and the reads
     * the line counts are the proofs' and the prices' own — while the line drawn with the memo and the
     * instrument is the bare call's.
     */
    @Test
    fun theApproachLineSaysWhereThePassSpentItsReads() {
        val setup = setup(field())
        val trace = mutableListOf<String>()
        val traced = RouteApproachPass.refine(setup, original, bounds, 20.0, memo = MarkMemo(), trace = { trace += it() })
        assertEquals("the report changes nothing it reports on", RouteApproachPass.refine(setup, original, bounds, 20.0), traced)
        val line = summary(trace)
        fun figure(name: String): Double = Regex("(?:^| )$name=([0-9.]+)").find(line)!!.groupValues[1].toDouble()
        assertTrue(line, line.startsWith("APPROACH moved="))
        assertTrue("the fixture searches a window", figure("windows") > 0.0)
        assertEquals(
            "each window's seed and every candidate is one evaluation",
            figure("windows") + figure("candidates"), figure("evals"), 0.0
        )
        // Every candidate ends outside, cut, lost or proved; the seeds share only the `outside` count.
        val accounted = figure("outside") + figure("cutTravel") + figure("cutLeg") + figure("lost") + figure("proofs")
        assertTrue(
            "every candidate is outside, cut, lost or proved ($accounted for ${figure("candidates")})",
            accounted >= figure("candidates") && accounted <= figure("candidates") + figure("windows")
        )
        assertTrue("a refusal is one of the evaluations", figure("outside") + figure("refusedClear") <= figure("evals"))
        assertTrue("the proofs test fine marks and read the wall", figure("clearMarks") > 0.0 && figure("hardReads") > 0.0)
        assertTrue("and the candidates' legs are priced", figure("priceMarks") > 0.0 && figure("priceReads") > 0.0)
        assertTrue("the slow-water test read the line's legs", figure("slowLegs") > 0.0)
        assertTrue("every clock is reported", listOf("ms", "clearMs", "priceMs", "slowMs").all { figure(it) >= 0.0 })
        val passEvals = Regex("(?:^| )passEvals=([0-9/]+)").find(line)!!.groupValues[1].split("/").map { it.toDouble() }
        assertEquals("each pass's evaluations sum to the line's", figure("evals"), passEvals.sum(), 0.0)
        // Each read clock is the paid side of one read inside the price or the clearance clock; the slack is
        // the one-decimal rounding of the six printed figures.
        val reads = listOf("nearEdgeMs", "farEdgeMs", "fieldPriceMs", "fieldClearMs").sumOf { figure(it) }
        assertTrue("the read clocks sit inside the walk's own", reads <= figure("priceMs") + figure("clearMs") + 0.3)
        assertTrue("the proofs read the field's clearance", figure("fieldClears") > 0.0)
    }

    /**
     * **The skip fires where a window survives a move elsewhere.** The original approach — whose bend pass 1
     * removes — continues down a straight chain into a second slow disc, to an end walled inside a thin ring: every
     * replacement there crosses the ring and is refused, so pass 1 searches that crossing's windows without a
     * move, and the chain keeps them out of reach of the first crossing's splices. Pass 2 skips them, and draws
     * the full second pass's line.
     */
    @Test
    fun theSecondPassSkipsTheWindowsAMoveLeftAlone() {
        val second = point(2500.0, -900.0)
        val pocket = point(2500.0, -750.0)
        val chain = (0 until 8).map { point(2500.0, 125.0 - 75.0 * it) }
        val line = listOf(original[0], original[1], original[2]) + chain + listOf(point(2500.0, -550.0), pocket)
        val ring = RouteCostSource.Hard(distanceAt = { abs(SpatialOperations.haversine(it, pocket) - 100.0) })
        val setup = setup(field(extraHard = ring, secondDisc = second))
        val full = RouteApproachPass.refine(setup, line, bounds, 20.0, memo = MarkMemo(), skipRepeats = false)
        val trace = mutableListOf<String>()
        val skipping = RouteApproachPass.refine(setup, line, bounds, 20.0, memo = MarkMemo(), trace = { trace += it() })
        assertEquals("a skipped repeat moves nothing", full, skipping)
        val repeats = Regex("(?:^| )repeats=([0-9]+)").find(summary(trace))!!.groupValues[1].toInt()
        assertTrue("the second pass skips the straight crossing's windows (${summary(trace)})", repeats > 0)
    }

    /**
     * **Pricing first and proving only the winners answers the very line the proof-first order did.** Eight
     * seeded lines around the rock, four seeded S lines (a turn right, then left) leaving the rock's disc and the S
     * fixture's line and its mirror on open water, each refined over six waters
     * — free water, a mild slow disc whose best is
     * mostly travel time (where the travel cut fires), a second rock standing on the
     * free answer (so a would-be winner is refused by its proof), a shoal behind the measured gate, the same
     * shoal with the gate's berth priced (the one price whose sign rests on a config clamp), and a marker-only
     * price whose slow gate opens nothing — return the same points in both orders. Every path the new order can take is
     * shown to run somewhere on these lines, asserted as **present** rather than counted so a fixture tweak does
     * not break it: the travel cut, the first-leg cut, a full price that loses, a proof that refuses, a search over
     * berth-priced water, a split, an S's crossing, that crossing slid along its legs to a move, and a lone turn slid
     * along its own legs to a move.
     */
    @Test
    fun pricingFirstAnswersTheProofFirstLine() {
        val random = java.util.Random(20261027L)
        val setups = sixWaters()
        val paths = linkedMapOf("cutTravel" to 0.0, "cutLeg" to 0.0, "lost" to 0.0, "refusedClear" to 0.0)
        var berthWindows = 0.0
        var splitEvals = 0.0
        var crossings = 0.0
        var crossingSlides = 0.0
        var slidCrossings = 0.0
        var loneSlides = 0.0
        var slidLones = 0.0
        val lines = List(8) {
            listOf(
                original[0],
                point(random.nextDouble() * 200.0 - 100.0, 260.0 + random.nextDouble() * 120.0),
                point(1800.0 + random.nextDouble() * 1200.0, random.nextDouble() * 700.0 - 100.0),
                original[3]
            )
        } + List(4) {
            // An S off the rock's disc — a turn right, then left — whose window the slow gate opens: its crossing is
            // read after the window's own search, in both orders.
            listOf(
                original[0],
                point(1500.0 + random.nextDouble() * 200.0 - 100.0, 250.0 + random.nextDouble() * 200.0 - 100.0),
                point(1800.0 + random.nextDouble() * 200.0 - 100.0, -300.0 + random.nextDouble() * 200.0 - 100.0),
                original[3]
            )
        } + listOf(sLine, sLine.map(::mirrored)) + // S lines on open water, whose crossing wins and slides
            listOf(loneLine, loneLine.map(::mirrored)) // a lone turn past the rock's disc, which slides along its legs
        for (line in lines) {
            for ((name, setup) in setups) {
                val proofFirst = mutableListOf<String>()
                val old = RouteApproachPass.refine(
                    setup, line, bounds, 20.0, memo = MarkMemo(), trace = { proofFirst += it() }, incumbentFirst = false
                )
                val priceFirst = mutableListOf<String>()
                val new = RouteApproachPass.refine(setup, line, bounds, 20.0, memo = MarkMemo(), trace = { priceFirst += it() })
                assertEquals("$name: the two orders draw one line", old, new)
                fun figure(lines: List<String>, field: String) =
                    Regex("(?:^| )$field=([0-9.]+)").find(summary(lines))?.groupValues?.get(1)?.toDouble() ?: 0.0
                assertEquals("$name: the same windows and candidates", figure(proofFirst, "evals"), figure(priceFirst, "evals"), 0.0)
                for (path in paths.keys) paths[path] = paths.getValue(path) + figure(priceFirst, path)
                if (name == "berth") berthWindows += figure(priceFirst, "windows")
                splitEvals += figure(priceFirst, "splitEvals")
                crossings += figure(priceFirst, "crossings")
                crossingSlides += figure(priceFirst, "crossingSlides")
                slidCrossings += figure(priceFirst, "slidCrossings")
                loneSlides += figure(priceFirst, "loneSlides")
                slidLones += figure(priceFirst, "slidLones")
            }
        }
        for ((path, count) in paths) {
            assertTrue("the new order takes its $path path on these lines ($paths)", count > 0.0)
        }
        assertTrue("the berth-priced water is searched, not skipped", berthWindows > 0.0)
        assertTrue("splits are searched in both orders on these lines", splitEvals > 0.0)
        assertTrue("an S's crossing is evaluated in both orders on these lines", crossings > 0.0)
        assertTrue("and slid along its legs", crossingSlides > 0.0)
        assertTrue("a slid crossing placing a move", slidCrossings > 0.0)
        assertTrue("a lone turn slid along its legs", loneSlides > 0.0)
        assertTrue("and placing a move", slidLones > 0.0)
    }

    /**
     * **An S standing for one turn is merged at its crossing.** The line leaves just north of the rock's slow disc
     * eastward, its chord to the second point cutting the disc, then turns right and, 626 m on, left past a second slow
     * disc its chord to the aim would cut: two price turns on open water, the 2026-10-28 La Salis → Le Village Englouti
     * "Balanced" S. No leg is slow, so the slow gate opens nothing; the S read offers the crossing of the outer legs,
     * which is one 24° turn, 189 m shorter, and clear of both discs — and the pass takes it.
     */
    @Test
    fun anSStandingForOneTurnIsMergedAtItsCrossing() {
        val setup = sSetup()
        val line = sLine
        assertClear(setup, line)
        val ctx = setup.context()
        for (i in 1..2) {
            assertEquals("no land turn at $i", null, MultipassPull.legClearCause(line[i - 1], line[i + 1], ctx))
            assertTrue(
                "but a price turn at $i",
                MultipassPull.totalSec(line[i - 1], line[i + 1], ctx) >
                    MultipassPull.totalSec(line[i - 1], line[i], ctx) + MultipassPull.totalSec(line[i], line[i + 1], ctx)
            )
        }
        assertTrue(
            "the two turn opposite ways",
            RouteCornerPass.turnDegreesAt(line[0], line[1], line[2]) * RouteCornerPass.turnDegreesAt(line[1], line[2], line[3]) < 0.0
        )

        val trace = mutableListOf<String>()
        val merged = RouteApproachPass.refine(
            setup, line, bounds, 20.0, memo = MarkMemo(), trace = { trace += it() }, slideLoneTurns = false
        )
        val summary = summary(trace)
        assertEquals("the slow gate opened nothing ($summary)", 0, count(summary, "windows"))
        assertEquals("the S read opened its window", 1, count(summary, "sWindows"))
        assertEquals("and offered its one crossing", 1, count(summary, "crossings"))
        assertTrue(
            "the move is a merge of two price turns ($trace)",
            trace.any { it.startsWith("APPROACH move ") && it.contains(" kind=merge ") && it.split(":PRICE").size == 3 }
        )
        assertEquals("one point stands for the two ($merged)", 3, merged.size)
        val crossing = RouteCornerPass.linesCrossing(line[0], line[1], line[2], line[3])!!
        // The crossing wins, then slides along its own legs (P5b): the point stands on one of them, no dearer.
        assertTrue(
            "on one of the crossing's legs ($merged)",
            onSegment(merged[1], line.first(), crossing) || onSegment(merged[1], crossing, line.last())
        )
        assertTrue(
            "no dearer than the crossing",
            cost(setup, merged) <= cost(setup, listOf(line.first(), crossing, line.last()))
        )
        assertEquals("and the slide placed it here", 1, count(summary, "slidCrossings"))
        assertEquals(line.first(), merged.first())
        assertEquals(line.last(), merged.last())
        assertClear(setup, merged)
        assertTrue("the line is cheaper", cost(setup, merged) < cost(setup, line))
        assertTrue("and shorter", polylineLengthM(merged) < polylineLengthM(line) - 150.0)
        assertEquals(
            "proof-first draws the same line",
            merged, RouteApproachPass.refine(
                setup, line, bounds, 20.0, memo = MarkMemo(), incumbentFirst = false, slideLoneTurns = false
            )
        )
        assertEquals(
            "a second pass searching every window draws the same line",
            merged, RouteApproachPass.refine(
                setup, line, bounds, 20.0, memo = MarkMemo(), skipRepeats = false, slideLoneTurns = false
            )
        )
        assertEquals(
            "and the pair armed from the other end the same line, turned back",
            merged.reversed(), RouteApproachPass.refine(
                setup.reversed(), line.reversed(), bounds, 20.0, memo = MarkMemo(), slideLoneTurns = false
            )
        )
        // The line as shipped: the merged turn is itself a lone turn, which a later window may slide on — clear, no
        // dearer than the merge alone, and the same from either end and with every window searched again.
        val shipped = RouteApproachPass.refine(setup, line, bounds, 20.0, memo = MarkMemo())
        assertClear(setup, shipped)
        assertTrue("the shipped line is no dearer than the merge", cost(setup, shipped) <= cost(setup, merged))
        assertEquals(
            "and is drawn the same from the other end",
            shipped.reversed(), RouteApproachPass.refine(setup.reversed(), line.reversed(), bounds, 20.0, memo = MarkMemo())
        )
        assertEquals(
            "and with every window searched again",
            shipped, RouteApproachPass.refine(setup, line, bounds, 20.0, memo = MarkMemo(), skipRepeats = false)
        )
    }

    /**
     * **An S the slow gate opens keeps its search's own answer where its crossing loses.** The S leaves inside the
     * rock's slow disc, so its window is searched around its seed as before P4; its crossing is cheaper than that seed
     * and dearer than the search's best — the one case where offering the crossing *before* the search would move the
     * search's centre and its answer. Read after, it moves nothing: the line is the one the pass drew before the S
     * read existed, recorded from that build (2026-10-28), and the crossing was offered.
     */
    @Test
    fun aSlowOpenedSKeepsItsSearchsAnswerWhereItsCrossingLoses() {
        val setup = setup(field())
        val line = listOf(original[0], point(1500.0, 250.0), point(1800.0, -300.0), original[3])
        val ctx = setup.context()
        fun sec(route: List<LatLng>): Double {
            assertClear(setup, route)
            return route.zipWithNext().sumOf { (u, w) -> MultipassPull.totalSec(u, w, ctx) }
        }
        val crossing = RouteCornerPass.linesCrossing(line[0], line[1], line[2], line[3])!!
        assertTrue(
            "the crossing beats the window's seed",
            sec(listOf(line[0], crossing, line[3])) < sec(listOf(line[0], line[1], line[3]))
        )

        val trace = mutableListOf<String>()
        val refined = RouteApproachPass.refine(setup, line, bounds, 20.0, memo = MarkMemo(), trace = { trace += it() })
        assertEquals("the crossing was offered (${summary(trace)})", 1, count(summary(trace), "crossings"))
        assertTrue("and lost to the search's best", sec(refined) < sec(listOf(line[0], crossing, line[3])))
        // The crossing lost, yet it slid along its legs (D7) — last, so the search's answer stands where no slid point
        // beats it.
        assertTrue("its legs were read", count(summary(trace), "crossingSlides") > 0)
        assertEquals("and no slid point beat the search", 0, count(summary(trace), "slidCrossings"))
        assertEquals(
            "the line is the search's own, as before the S read",
            listOf(original[0], LatLng(43.51543615969988, 7.003072978971424), original[3]), refined
        )
    }

    /**
     * **Only an S of two real turns is offered a crossing.** Two lines past the rock's disc alone, no leg in slow
     * water, so no other move changes them: one whose second turn is the other way but flatter than the corner
     * pass's floor, and one whose two turns go the same way. Neither window is an S, so neither is offered its
     * crossing.
     */
    @Test
    fun onlyAnSOfTwoRealTurnsIsOfferedACrossing() {
        val (start, first, _, aim) = sLine
        val setup = setup(field(), start = start, aim = aim)
        val underTheFloor = listOf(start, first, point(2288.0, -365.7), aim)
        val sameWay = listOf(start, first, point(2439.7, -92.0), aim)
        fun turns(l: List<LatLng>) = Pair(
            RouteCornerPass.turnDegreesAt(l[0], l[1], l[2]), RouteCornerPass.turnDegreesAt(l[1], l[2], l[3])
        )
        turns(underTheFloor).let { (t1, t2) ->
            assertTrue("opposite turns ($t1, $t2)", t1 * t2 < 0.0)
            assertTrue("the second under the floor", abs(t2) < RouteCornerPass.MIN_TURN_DEG)
        }
        turns(sameWay).let { (t1, t2) ->
            assertTrue("one way twice ($t1, $t2)", t1 * t2 > 0.0)
            assertTrue("both real turns", minOf(abs(t1), abs(t2)) >= RouteCornerPass.MIN_TURN_DEG)
        }
        for (line in listOf(underTheFloor, sameWay)) {
            assertClear(setup, line)
            val trace = mutableListOf<String>()
            val kept = RouteApproachPass.refine(
                setup, line, bounds, 20.0, memo = MarkMemo(), trace = { trace += it() }, slideLoneTurns = false
            )
            assertEquals("no crossing is offered on $line (${summary(trace)})", 0, count(summary(trace), "crossings"))
            assertEquals("and the line is kept", line, kept)
        }
    }

    /**
     * **A lone turn slides along its legs where a leg leaves it room, and stays where none does.** One turn past a slow
     * disc, both legs clean and the chord skipping it through the disc: neither the slow gate nor the S read opens its
     * window, so it is offered the slide along its own legs (lone-turns P1). Far off a wide disc the turn moves along
     * its legs, cheaper and clear, and the line left reads no more than a step or two of slack
     * (P0's count on the line handed on); a turn whose legs graze the wide disc is searched and kept, and so is one past
     * a small disc whose slide saves less than `route.approach.lone.minSavedSec` — until the floor is lifted. The small disc's
     * window with the turn under the corner pass's floor, or with a rock on the chord — a land turn — is never offered.
     * Skipping repeats draws the same line in every case.
     */
    @Test
    fun aLoneTurnSlidesAlongItsLegsOrStays() {
        val a = point(1400.0, -500.0)
        val e = point(2600.0, -500.0)
        val small = setup(field(smallDiscs = listOf(point(2000.0, -480.0) to 50.0)), start = a, aim = e)
        val wide = setup(field(moreDiscs = listOf(point(2000.0, -300.0))), start = a, aim = e)
        val rockOnTheChord = setup(
            field(
                extraHard = RouteCostSource.Hard(distanceAt = {
                    (SpatialOperations.haversine(it, point(2000.0, -500.0)) - 20.0).coerceAtLeast(0.0)
                }),
                smallDiscs = listOf(point(2000.0, -480.0) to 50.0)
            ),
            start = a, aim = e
        )
        data class Case(val name: String, val setup: PullSetup, val v: LatLng, val offered: Boolean, val slides: Boolean)
        val cases = listOf(
            // Searched, but its slide saves under `route.approach.lone.minSavedSec`: the grid's rounding, kept.
            Case("past a small disc", small, point(2000.0, -600.0), offered = true, slides = false),
            Case("far off a wide disc", wide, point(2000.0, -900.0), offered = true, slides = true),
            Case("grazing a wide disc", wide, point(2000.0, -609.0), offered = true, slides = false),
            Case("under the floor", small, point(2000.0, -545.0), offered = false, slides = false),
            Case("land turn", rockOnTheChord, point(2000.0, -600.0), offered = false, slides = false)
        )
        for (case in cases) {
            val line = listOf(a, case.v, e)
            assertClear(case.setup, line)
            // Each case is what it is named for: clean legs — travel alone — and the reason it is or is not offered.
            val ctx = case.setup.context()
            for ((u, w) in line.zipWithNext()) {
                assertEquals(
                    "${case.name}: a clean leg", SpatialOperations.haversine(u, w) / ctx.paceMps,
                    MultipassPull.totalSec(u, w, ctx), 1e-9
                )
            }
            val turn = abs(RouteCornerPass.turnDegreesAt(a, case.v, e))
            assertEquals(
                "${case.name}: under the floor or not ($turn°)",
                case.name == "under the floor", turn < RouteCornerPass.MIN_TURN_DEG
            )
            assertEquals(
                "${case.name}: a land turn or not",
                case.name == "land turn", MultipassPull.legClearCause(a, e, ctx) == ChordRefusal.LAND
            )
            val trace = mutableListOf<String>()
            val refined = RouteApproachPass.refine(case.setup, line, bounds, 20.0, memo = MarkMemo(), trace = { trace += it() })
            val summary = summary(trace)
            assertEquals("${case.name}: offered ($summary)", case.offered, count(summary, "loneWindows") > 0)
            assertEquals("${case.name}: slid", case.slides, count(summary, "slidLones") > 0)
            if (case.slides) {
                assertEquals("${case.name}: still one turn", 3, refined.size)
                assertTrue("${case.name}: cheaper", cost(case.setup, refined) < cost(case.setup, line))
                assertClear(case.setup, refined)
                assertTrue(
                    "${case.name}: a move line names it",
                    trace.any { it.startsWith("APPROACH move ") && it.contains("kind=lone") }
                )
                for (left in trace.filter { it.startsWith("APPROACH lone ") }) {
                    assertTrue("${case.name}: slid to within two steps of the water ($left)", count(left, "slackM") < 40)
                }
            } else {
                assertEquals("${case.name}: and the line is kept", line, refined)
            }
            val full = RouteApproachPass.refine(case.setup, line, bounds, 20.0, memo = MarkMemo(), skipRepeats = false)
            assertEquals("${case.name}: skipping repeats draws the same line", full, refined)
            val back = RouteApproachPass.refine(case.setup.reversed(), line.reversed(), bounds, 20.0, memo = MarkMemo())
            assertEquals("${case.name}: armed from the other end, the same line turned back", refined.reversed(), back)
        }
        // With no floor, the small disc's turn slides too: the floor, not the search, kept it.
        val floor = AppConfig.routeApproachLoneMinSavedSec
        AppConfig.routeApproachLoneMinSavedSec = 0.0
        try {
            val line = listOf(a, point(2000.0, -600.0), e)
            val trace = mutableListOf<String>()
            val refined = RouteApproachPass.refine(small, line, bounds, 20.0, memo = MarkMemo(), trace = { trace += it() })
            assertTrue("with no floor it slides (${summary(trace)})", count(summary(trace), "slidLones") > 0)
            assertTrue("and saves under the shipped floor", cost(small, line) - cost(small, refined) < floor)
        } finally {
            AppConfig.routeApproachLoneMinSavedSec = floor
        }
    }

    /**
     * **An S whose crossing is priced keeps its two turns.** The same S, with a third slow disc standing on the
     * crossing's second leg, off all three of the line's own: the crossing is offered and loses on price, and the line
     * is the one handed in.
     */
    @Test
    fun anSWhoseCrossingIsPricedKeepsItsTwoTurns() {
        val setup = sSetup(moreDiscs = listOf(point(1103.0, -231.0)))
        val line = sLine
        assertClear(setup, line)
        val ctx = setup.context()
        val crossing = RouteCornerPass.linesCrossing(line[0], line[1], line[2], line[3])!!
        assertTrue(
            "the crossing's second leg pays the third disc's price",
            MultipassPull.totalSec(crossing, line[3], ctx) >
                SpatialOperations.haversine(crossing, line[3]) / setup.paceMps + 60.0
        )
        assertTrue("which no leg of the S does", line.zipWithNext().all { (u, w) ->
            MultipassPull.totalSec(u, w, ctx) <= SpatialOperations.haversine(u, w) / setup.paceMps + 1e-9
        })

        val trace = mutableListOf<String>()
        val kept = RouteApproachPass.refine(
            setup, line, bounds, 20.0, memo = MarkMemo(), trace = { trace += it() }, slideLoneTurns = false
        )
        assertEquals("the crossing is offered (${summary(trace)})", 1, count(summary(trace), "crossings"))
        assertEquals("and the S is kept", line, kept)
        assertTrue("with no move", trace.none { it.startsWith("APPROACH move ") })
    }

    /**
     * **An S holding a land turn is not merged.** The same S, with an island standing on the chord that skips its
     * first point — the island forces that turn — and on the crossing's second leg: the window is skipped by the
     * land-turn rule before its crossing is read, and the S is kept.
     */
    @Test
    fun anSHoldingALandTurnIsNotMerged() {
        val islandCentre = point(900.0, 5.0)
        val island = RouteCostSource.Hard(distanceAt = {
            (SpatialOperations.haversine(it, islandCentre) - 150.0).coerceAtLeast(0.0)
        })
        val setup = sSetup(extraHard = island)
        val line = sLine
        assertClear(setup, line)
        assertEquals(
            "the island forces the first turn",
            ChordRefusal.LAND, MultipassPull.legClearCause(line[0], line[2], setup.context())
        )

        val trace = mutableListOf<String>()
        val kept = RouteApproachPass.refine(
            setup, line, bounds, 20.0, memo = MarkMemo(), trace = { trace += it() }, slideLoneTurns = false
        )
        val summary = summary(trace)
        assertTrue("the window is skipped for its land turn ($summary)", count(summary, "landTurnWindows") > 0)
        assertEquals("before its crossing is read", 0, count(summary, "crossings"))
        assertEquals("and the S is kept", line, kept)
    }

    /**
     * **A crossing slides along its incoming leg.** The S above mirrored north to south, so the pass runs it from the
     * rock's end and the rock's leg is the crossing's incoming one. The merged corner stands where the outer legs cross,
     * 630 m east of the rock; the rock's disc pins only that leg's far end, so the corner belongs back along it, as near
     * the rock as the chord to the aim still clears the disc — the 2026-10-28 "Balanced" corner at La Fourmigue, whose
     * line the pass runs from its aim beside the rock. The point stands on that leg, cheaper than the crossing, and every
     * order, skip mode and direction draws it.
     */
    @Test
    fun aCrossingSlidesAlongItsIncomingLeg() {
        val line = sLine.map(::mirrored)
        val setup = setup(
            field(secondDisc = mirrored(point(2060.0, -60.0))), start = line.first(), aim = line.last()
        )
        assertTrue("the pass runs this line from the rock's end", sortsFirst(line.first(), line.last()))
        val (_, slid) = assertSlidAlong(setup, line, bounds, toward = line.first())
        // The corner the incoming leg's search reaches, recorded from the 2026-10-28 build: the leg searched first, and
        // after the crossing, as P5b orders them — another order would stop elsewhere on the leg.
        assertEquals("where the incoming leg's search stops", LatLng(43.50981677959504, 7.002968116273581), slid[1])
    }

    /**
     * **A crossing slides along its outgoing leg.** The S as drawn, which the pass runs from its southern end, so the
     * rock's leg is the crossing's outgoing one; the corridor ends 20 m east of the crossing, so every point on the
     * incoming leg stands outside it and is refused before any read. The corner slides along the outgoing leg, toward
     * the rock.
     */
    @Test
    fun aCrossingSlidesAlongItsOutgoingLeg() {
        assertTrue("the pass runs this line from the aim's end", !sortsFirst(sLine.first(), sLine.last()))
        val crossing = RouteCornerPass.linesCrossing(sLine[0], sLine[1], sLine[2], sLine[3])!!
        val tight = bounds.copy(lonEast = crossing.longitude + 20.0 / GateTestFixtures.M_PER_DEG_LON)
        val (summary, _) = assertSlidAlong(sSetup(), sLine, tight, toward = sLine.first())
        assertTrue("the incoming leg's points stood outside the corridor ($summary)", count(summary, "outside") > 0)
    }

    /**
     * **A crossing that loses by a rock still slides** (D7). The same S, with a small rock standing on the crossing's
     * second leg 150 m out from it, off every leg of the S: the crossing is refused, yet a corner on one of its legs,
     * whose other leg clears both the rock and the disc, wins — the near miss the slide reaches whether the crossing won
     * or lost.
     */
    @Test
    fun aCrossingThatLosesStillSlides() {
        val line = sLine
        val crossing = RouteCornerPass.linesCrossing(line[0], line[1], line[2], line[3])!!
        val rockOnTheLeg = between(crossing, line.last(), 150.0 / SpatialOperations.haversine(crossing, line.last()))
        val setup = sSetup(extraHard = RouteCostSource.Hard(distanceAt = {
            (SpatialOperations.haversine(it, rockOnTheLeg) - 20.0).coerceAtLeast(0.0)
        }))
        assertClear(setup, line)
        assertFalse("the crossing's second leg is refused", MultipassPull.legClear(crossing, line.last(), setup.context()))

        val trace = mutableListOf<String>()
        val slid = RouteApproachPass.refine(
            setup, line, bounds, 20.0, memo = MarkMemo(), trace = { trace += it() }, slideLoneTurns = false
        )
        assertEquals("the crossing was offered (${summary(trace)})", 1, count(summary(trace), "crossings"))
        assertEquals("and the slide placed the move", 1, count(summary(trace), "slidCrossings"))
        assertEquals(3, slid.size)
        assertTrue(
            "on one of the crossing's legs ($slid)",
            onSegment(slid[1], line.first(), crossing) || onSegment(slid[1], crossing, line.last())
        )
        assertClear(setup, slid)
        assertTrue("the line is cheaper", cost(setup, slid) < cost(setup, line))
        assertEquals(
            "proof-first draws the same line",
            slid, RouteApproachPass.refine(
                setup, line, bounds, 20.0, memo = MarkMemo(), incumbentFirst = false, slideLoneTurns = false
            )
        )
        assertEquals(
            "a second pass searching every window draws the same line",
            slid, RouteApproachPass.refine(setup, line, bounds, 20.0, memo = MarkMemo(), skipRepeats = false, slideLoneTurns = false)
        )
        assertEquals(
            "and the pair armed from the other end the same line, turned back",
            slid.reversed(), RouteApproachPass.refine(
                setup.reversed(), line.reversed(), bounds, 20.0, memo = MarkMemo(), slideLoneTurns = false
            )
        )
    }

    /**
     * **A crossing that loses on price is still searched past its leg's middle** (D7, §13 of the plan's P5 review). The
     * mirrored S, run from the rock's end, with a small slow disc 60 m across lying on the crossing's second leg and on
     * the line from the incoming leg's middle to the aim: the crossing loses on that price, and so does the leg's middle
     * — dearer than the S itself — while a point further toward the rock clears it. A search that moved only on points
     * beating the window would stop at the middle and read only toward the crossing; following the leg's own best, it
     * reads past the middle and finds the corner.
     */
    @Test
    fun aCrossingThatLosesOnPriceIsSearchedPastItsMiddle() {
        val line = sLine.map(::mirrored)
        val setup = setup(
            field(secondDisc = mirrored(point(2060.0, -60.0)), smallDiscs = listOf(point(1000.0, -45.0) to 60.0)),
            start = line.first(), aim = line.last()
        )
        assertClear(setup, line)
        val ctx = setup.context()
        val crossing = RouteCornerPass.linesCrossing(line[0], line[1], line[2], line[3])!!
        val (a, aim) = line.first() to line.last()
        assertTrue(
            "the crossing's second leg pays the small disc",
            MultipassPull.totalSec(crossing, aim, ctx) > SpatialOperations.haversine(crossing, aim) / setup.paceMps + 5.0
        )
        assertTrue("so the crossing loses to the S", cost(setup, listOf(a, crossing, aim)) > cost(setup, line))
        val middle = between(crossing, a, 0.5)
        assertTrue("and so does the leg's middle", cost(setup, listOf(a, middle, aim)) > cost(setup, line))

        val trace = mutableListOf<String>()
        val slid = RouteApproachPass.refine(
            setup, line, bounds, 20.0, memo = MarkMemo(), trace = { trace += it() }, slideLoneTurns = false
        )
        assertEquals("the slide placed the move (${summary(trace)})", 1, count(summary(trace), "slidCrossings"))
        assertEquals(3, slid.size)
        assertTrue("on the incoming leg ($slid)", onSegment(slid[1], a, crossing))
        assertTrue(
            "past its middle",
            SpatialOperations.haversine(crossing, slid[1]) > SpatialOperations.haversine(crossing, a) / 2.0
        )
        assertClear(setup, slid)
        assertTrue("the line is cheaper", cost(setup, slid) < cost(setup, line))
        assertEquals(
            "proof-first draws the same line",
            slid, RouteApproachPass.refine(
                setup, line, bounds, 20.0, memo = MarkMemo(), incumbentFirst = false, slideLoneTurns = false
            )
        )
        assertEquals(
            "and the pair armed from the other end the same line, turned back",
            slid.reversed(), RouteApproachPass.refine(
                setup.reversed(), line.reversed(), bounds, 20.0, memo = MarkMemo(), slideLoneTurns = false
            )
        )
    }

    /**
     * Refines [line] — an S whose crossing wins — over [setup] in [corridor], and asserts the slide moved its crossing
     * along the leg toward [toward]: on that leg, cheaper than the crossing, the move named, and the same line in both
     * orders, both skip modes and from the other end. Returns the pass's summary line and the slid line.
     */
    private fun assertSlidAlong(
        setup: PullSetup,
        line: List<LatLng>,
        corridor: BBox,
        toward: LatLng
    ): Pair<String, List<LatLng>> {
        assertClear(setup, line)
        val crossing = RouteCornerPass.linesCrossing(line[0], line[1], line[2], line[3])!!
        val viaCrossing = listOf(line.first(), crossing, line.last())
        assertClear(setup, viaCrossing)
        assertTrue("the crossing beats the S", cost(setup, viaCrossing) < cost(setup, line))

        val trace = mutableListOf<String>()
        val slid = RouteApproachPass.refine(
            setup, line, corridor, 20.0, memo = MarkMemo(), trace = { trace += it() }, slideLoneTurns = false
        )
        val summary = summary(trace)
        assertEquals("the slide placed the move ($summary)", 1, count(summary, "slidCrossings"))
        assertTrue("reading candidates along the legs", count(summary, "crossingSlides") > 0)
        assertEquals(3, slid.size)
        assertTrue("on the leg toward $toward ($slid)", onSegment(slid[1], toward, crossing))
        assertTrue(
            "off the crossing by more than the pass's step",
            SpatialOperations.haversine(slid[1], crossing) > 20.0
        )
        assertClear(setup, slid)
        assertTrue("cheaper than the crossing", cost(setup, slid) < cost(setup, viaCrossing))
        assertTrue(
            "the move is a merge ($trace)",
            trace.any { it.startsWith("APPROACH move ") && it.contains(" kind=merge ") }
        )
        assertEquals(
            "proof-first draws the same line",
            slid, RouteApproachPass.refine(
                setup, line, corridor, 20.0, memo = MarkMemo(), incumbentFirst = false, slideLoneTurns = false
            )
        )
        assertEquals(
            "a second pass searching every window draws the same line",
            slid, RouteApproachPass.refine(
                setup, line, corridor, 20.0, memo = MarkMemo(), skipRepeats = false, slideLoneTurns = false
            )
        )
        assertEquals(
            "and the pair armed from the other end the same line, turned back",
            slid.reversed(), RouteApproachPass.refine(
                setup.reversed(), line.reversed(), corridor, 20.0, memo = MarkMemo(), slideLoneTurns = false
            )
        )
        return summary to slid
    }

    /**
     * Whether [p] stands on the segment [u]–[v]: no turn there in the corner pass's own frame, within 1e-4° — the
     * arccosine's rounding near zero, under 2 mm at a kilometre.
     */
    private fun onSegment(p: LatLng, u: LatLng, v: LatLng): Boolean = abs(RouteCornerPass.turnDegreesAt(u, p, v)) < 1e-4

    /** [p] reflected north to south in [point]'s frame. */
    private fun mirrored(p: LatLng): LatLng = metres(p).let { (east, north) -> point(east, -north) }

    /**
     * A lone turn past the rock's disc: both legs clean, the chord from the first point to the last through the disc,
     * the turn standing well off it — the equivalence test's lone line.
     */
    private val loneLine = listOf(point(-180.0, 600.0), point(600.0, 600.0), point(400.0, -400.0))

    /** The S the tests above read: right past the rock's disc, then left past a second disc, to the aim. */
    private val sLine = listOf(point(0.0, 310.0), point(1500.0, 250.0), point(1800.0, -300.0), point(3000.0, -900.0))

    /** The S's water: the rock's disc, a second disc its second point avoids, and whatever a test adds. */
    private fun sSetup(extraHard: RouteCostSource.Hard? = null, moreDiscs: List<LatLng> = emptyList()) = setup(
        field(extraHard = extraHard, secondDisc = point(2060.0, -60.0), moreDiscs = moreDiscs),
        start = sLine.first(), aim = sLine.last()
    )

    /** A whole count the summary line carries. */
    private fun count(summary: String, name: String) = Regex("(?:^| )$name=([0-9]+)").find(summary)!!.groupValues[1].toInt()

    /**
     * **A merge never deletes a land turn.** The line leaves the slow disc round an island's north side — a turn
     * the island forces — then bends at sea round a second slow disc, a turn kept for price: one land turn and one
     * price turn in the one merge window, so a rule that read either alone, or every inner point together, would
     * merge them. Without the rule the pass merges both into one point south of the island, and its move line names
     * the land turn it replaced; with the rule the merge window is skipped and the line still turns round the
     * island, no dearer than it was.
     */
    @Test
    fun aMergeNeverDeletesALandTurn() {
        val islandCentre = point(700.0, 250.0)
        val island = RouteCostSource.Hard(distanceAt = {
            (SpatialOperations.haversine(it, islandCentre) - 150.0).coerceAtLeast(0.0)
        })
        val setup = setup(field(extraHard = island, secondDisc = point(1850.0, -215.0)))
        val line = listOf(original[0], point(700.0, 470.0), original[2], original[3])
        assertClear(setup, line)
        val ctx = setup.context()
        assertEquals(
            "the island forces the first turn", ChordRefusal.LAND, MultipassPull.legClearCause(line[0], line[2], ctx)
        )
        assertEquals("the second is no land turn", null, MultipassPull.legClearCause(line[1], line[3], ctx))
        assertTrue(
            "but a price turn: its skipping chord crosses the second disc",
            MultipassPull.totalSec(line[1], line[3], ctx) >
                MultipassPull.totalSec(line[1], line[2], ctx) + MultipassPull.totalSec(line[2], line[3], ctx)
        )

        val mergedTrace = mutableListOf<String>()
        val merged = RouteApproachPass.refine(
            setup, line, bounds, 20.0, memo = MarkMemo(), trace = { mergedTrace += it() }, keepLandTurns = false
        )
        assertEquals("without the rule the merge deletes the land turn", 0, landTurns(setup, merged))
        assertTrue(
            "and its move line names the land turn it replaced ($mergedTrace)",
            mergedTrace.any { it.startsWith("APPROACH move ") && it.contains(":LAND") }
        )

        val trace = mutableListOf<String>()
        val kept = RouteApproachPass.refine(setup, line, bounds, 20.0, memo = MarkMemo(), trace = { trace += it() })
        assertTrue("with it the line still turns round the island ($kept)", landTurns(setup, kept) >= 1)
        assertTrue(
            "the merge window is skipped (${summary(trace)})",
            Regex("landTurnWindows=([0-9]+)").find(summary(trace))!!.groupValues[1].toInt() > 0
        )
        assertEquals(line.first(), kept.first())
        assertEquals(line.last(), kept.last())
        assertClear(setup, kept)
        assertTrue("no dearer than it was", cost(setup, kept) <= cost(setup, line))
    }

    /**
     * **A turn standing for two features is split in two.** The line leaves the rock's slow disc eastward past an
     * island it must not cross, then runs south-east past a second slow disc; its one point stands where the two
     * tangent legs cross, at sea and off both — the 2026-10-27 "Balanced" bend. No slide can round both, so the pass
     * splits it: the line gains a point, is cheaper and clear, and turns nearer the island and nearer the disc than
     * the one point did.
     */
    @Test
    fun aTurnStandingForTwoFeaturesIsSplitInTwo() {
        assertSplitInTwo(point(290.0, 0.0), point(2281.8, -181.8))
    }

    /**
     * **A turn the slide must move first is split on the legs it moved to.** The same water, the bend 200 m back
     * along the same first leg, so its second leg cuts 50 m into the disc, and the start just outside the rock's disc,
     * so only that second leg is slow: the slide moves it off the disc's price, and a split of the original bend would
     * keep part of that leg — the 2026-10-27 "Balanced" run, where the split of the seed lost to the slide and the
     * slid window, its legs clean, was never searched again. The split of the slide's answer still turns once by each
     * feature.
     */
    @Test
    fun aTurnTheSlideMovesIsSplitOnTheLegsItFound() {
        val start = point(310.0, 0.0)
        val bend = point(2082.5, -163.6)
        val setup = twoFeatureSetup(start)
        val travelSec = SpatialOperations.haversine(bend, twoFeatureAim) / setup.paceMps
        assertTrue(
            "the bend's second leg pays the disc's price",
            MultipassPull.totalSec(bend, twoFeatureAim, setup.context()) > travelSec + 1.0
        )
        val split = assertSplitInTwo(start, bend)
        // The split's first point stands off the line's own first leg: it lies on the leg the slide found.
        val firstLeg = MultipassPull.legClearCause(start, bend, setup.context())
        assertEquals("the first leg is clear", null, firstLeg)
        val (sx, sy) = metres(start)
        val (bx, by) = metres(bend)
        val (px, py) = metres(split[1])
        val offLegM = abs((bx - sx) * (py - sy) - (by - sy) * (px - sx)) / hypot(bx - sx, by - sy)
        assertTrue("the split's first point is off the original first leg ($offLegM m)", offLegM > 5.0)
    }

    private val twoFeatureAim = point(3000.0, -900.0)
    private val twoFeatureIsland = point(1103.0, -270.0)
    private val twoFeatureDisc = point(2322.5, -675.0)

    /** An island the first leg must pass and a slow disc the second must, between the rock's disc and the aim. */
    private fun twoFeatureSetup(start: LatLng): PullSetup {
        val island = RouteCostSource.Hard(distanceAt = {
            (SpatialOperations.haversine(it, twoFeatureIsland) - 150.0).coerceAtLeast(0.0)
        })
        return setup(field(extraHard = island, secondDisc = twoFeatureDisc), start = start, aim = twoFeatureAim)
    }

    /** Splits the line `start → bend → aim` over the two features, asserts what a split owes, and returns the line. */
    private fun assertSplitInTwo(start: LatLng, bend: LatLng): List<LatLng> {
        val aim = twoFeatureAim
        val setup = twoFeatureSetup(start)
        val line = listOf(start, bend, aim)
        assertClear(setup, line)
        assertEquals("the island forces a turn", ChordRefusal.LAND, MultipassPull.legClearCause(start, aim, setup.context()))

        val trace = mutableListOf<String>()
        val split = RouteApproachPass.refine(setup, line, bounds, 20.0, memo = MarkMemo(), trace = { trace += it() })
        assertTrue("the move is a split ($trace)", trace.any { it.startsWith("APPROACH move ") && it.contains(" kind=split ") })
        assertTrue("the line gains a point ($split)", split.size > line.size)
        assertEquals(start, split.first())
        assertEquals(aim, split.last())
        assertClear(setup, split)
        assertTrue("the split is cheaper", cost(setup, split) < cost(setup, line))
        val inner = split.subList(1, split.lastIndex)
        val byIsland = inner.minBy { SpatialOperations.haversine(it, twoFeatureIsland) }
        val byDisc = inner.minBy { SpatialOperations.haversine(it, twoFeatureDisc) }
        assertTrue("each feature has its own turn ($split)", byIsland != byDisc)
        assertTrue(
            "the island's turn stands nearer it than the bend did",
            SpatialOperations.haversine(byIsland, twoFeatureIsland) < SpatialOperations.haversine(bend, twoFeatureIsland)
        )
        assertTrue(
            "and the disc's nearer the disc",
            SpatialOperations.haversine(byDisc, twoFeatureDisc) < SpatialOperations.haversine(bend, twoFeatureDisc)
        )

        // The split is one line whichever order evaluates it and whether or not the second pass skips repeats.
        assertEquals(
            "proof-first draws the same split",
            split, RouteApproachPass.refine(setup, line, bounds, 20.0, memo = MarkMemo(), incumbentFirst = false)
        )
        assertEquals(
            "a second pass searching every window draws the same split",
            split, RouteApproachPass.refine(setup, line, bounds, 20.0, memo = MarkMemo(), skipRepeats = false)
        )
        return split
    }

    /**
     * **A turn round one headland is not split, by the corner pass's floor.** The line leaves the rock's slow disc
     * eastward and turns 60° round a land wedge whose edges run 100 m inside its two 300 m legs. The half cut — one
     * point halfway down each leg — keeps the wedge clear and is cheaper, so only the floor refuses it: its two
     * points stand closer than the tangent a pace-speed curve needs for 60°, and the corner pass would draw them back
     * as the one corner. A cut wide enough to pass that floor crosses the wedge, so the line keeps its one turn.
     * Built at `lateralAccelMps2` 0.33 m/s², pinned here ([TurnConfigFixture]): the floor's span is that value's.
     */
    @Test
    fun aTurnRoundOneHeadlandIsNotSplit() {
        TurnConfigFixture.withTurn(TurnConfigFixture.BUILT_AT_MPS2) {
            val start = point(290.0, 0.0)
            val bend = point(590.0, 0.0)
            val aim = point(740.0, -259.8)
            // The wedge's tip and its two edges, away from the tip: west along the first leg, south-east along the second.
            val tip = Pair(532.3, -100.0)
            val edges = listOf(Pair(-1.0, 0.0), Pair(0.5, -0.866))
            val wedge = RouteCostSource.Hard(distanceAt = { p ->
                val (east, north) = metres(p)
                val inside = north <= -100.0 && 0.866 * (east - 590.0) + 0.5 * north <= -100.0
                if (inside) {
                    0.0
                } else {
                    edges.minOf { (ux, uy) ->
                        val t = ((east - tip.first) * ux + (north - tip.second) * uy).coerceAtLeast(0.0)
                        hypot(east - tip.first - t * ux, north - tip.second - t * uy)
                    }
                }
            })
            val setup = setup(field(extraHard = wedge), start = start, aim = aim)
            val line = listOf(start, bend, aim)
            assertClear(setup, line)
            assertEquals("the wedge forces the turn", ChordRefusal.LAND, MultipassPull.legClearCause(start, aim, setup.context()))
            val halfCut = listOf(start, point(440.0, 0.0), point(665.0, -129.9), aim)
            assertClear(setup, halfCut)
            assertTrue("the half cut is cheaper", cost(setup, halfCut) < cost(setup, line))
            val paceRadiusM = 15.0 * 15.0 / AppConfig.routeTurnLateralAccelMps2
            val summedDeg = abs(RouteCornerPass.turnDegreesAt(halfCut[0], halfCut[1], halfCut[2])) +
                abs(RouteCornerPass.turnDegreesAt(halfCut[1], halfCut[2], halfCut[3]))
            assertTrue(
                "but the corner pass would draw it back as one turn",
                RouteCornerPass.pairIsOneTurn(SpatialOperations.haversine(halfCut[1], halfCut[2]), summedDeg, paceRadiusM)
            )

            val trace = mutableListOf<String>()
            val kept = RouteApproachPass.refine(setup, line, bounds, 20.0, memo = MarkMemo(), trace = { trace += it() })
            assertTrue("no move is a split ($trace)", trace.none { it.contains(" kind=split ") })
            assertEquals("the line keeps its one turn", line.size, kept.size)
            assertTrue(
                "the cuts under the corner pass's span are refused (${summary(trace)})",
                Regex("splitFloor=([0-9]+)").find(summary(trace))!!.groupValues[1].toInt() > 0
            )
            assertClear(setup, kept)
        }
    }

    @Test(expected = CancellationException::class)
    fun cancellationInterruptsCandidateExploration() {
        var checks = 0
        RouteApproachPass.refine(setup(field()), original, bounds, 20.0, checkCancelled = {
            if (++checks == 8) throw CancellationException("cancelled")
        })
    }

    /**
     * The six waters the equivalence tests refine over: free water, a second rock standing on the free answer,
     * a shoal behind the measured gate, the same shoal with the gate's berth priced, a marker-only price, and a
     * mild slow disc whose best is mostly travel time.
     */
    private fun sixWaters(): List<Pair<String, PullSetup>> {
        val free = RouteApproachPass.refine(setup(field()), original, bounds, 20.0)
        val onTheFreeAnswer = between(free[1], free.last(), 0.3)
        val shoalRaster = GateTestFixtures.raster(130, 190, 20.0) { n, e ->
            if (hypot(n - 1500.0, e - 1500.0) < 80.0) 2.0 else 15.0
        }
        val shoalDepth = GateTestFixtures.depthOf(shoalRaster)
        val shoalField = field(extraHard = depthGateSource(3.0) { shoalDepth(it.latitude, it.longitude) })
        return listOf(
            "free" to setup(field()),
            "rock" to setup(field(extraHard = RouteCostSource.Hard(distanceAt = {
                (SpatialOperations.haversine(it, onTheFreeAnswer) - 60.0).coerceAtLeast(0.0)
            }))),
            "shoal" to setup(shoalField, GateTestFixtures.gateOver(shoalRaster, hardM = 20.0)),
            "berth" to setup(
                shoalField,
                GateTestFixtures.gateOver(shoalRaster, hardM = 10.0, preferredM = 20.0, berthOuterM = 120.0),
                BerthLaw(marginM = 20.0, outerM = 120.0, secPerM = 0.2, floorM = 10.0, innerSecPerM = 0.6)
            ),
            "marker" to setup(field(arm = PriceArm.MARKER)),
            // Slow water dearer than open water by a fraction of the travel: a detour's travel alone can beat
            // the best, which is what the travel cut exists for.
            "mild" to setup(field(rate = 0.02))
        )
    }

    private fun field(
        extraHard: RouteCostSource.Hard? = null,
        arm: PriceArm = PriceArm.BAND,
        /** The slow disc's price (s/m) — dear by default; a mild one leaves the best mostly travel time. */
        rate: Double = 0.35,
        /** A second slow disc of the same radius and price, centred here — none by default. */
        secondDisc: LatLng? = null,
        /** Further slow discs of the same radius and price — none by default. */
        moreDiscs: List<LatLng> = emptyList(),
        /** Slow discs of their own radius (m), at the same price — none by default. */
        smallDiscs: List<Pair<LatLng, Double>> = emptyList()
    ): RouteCostField {
        val discs = (listOfNotNull(rock, secondDisc) + moreDiscs).map { it to 300.0 } + smallDiscs
        fun price(p: LatLng) = if (discs.any { (c, r) -> SpatialOperations.haversine(p, c) <= r }) rate else 0.0
        return RouteCostField(listOfNotNull(
            RouteCostSource.Hard(distanceAt = { (SpatialOperations.haversine(it, rock) - 30.0).coerceAtLeast(0.0) }),
            extraHard,
            RouteCostSource.Soft(
                priceSec = ::price, tag = MultipassCellState.BAND,
                clearanceAt = { p -> discs.minOf { (c, r) -> abs(SpatialOperations.haversine(p, c) - r) } },
                armAt = { arm to price(it) }
            )
        ))
    }

    private fun setup(
        field: RouteCostField,
        gate: GateRule? = null,
        berth: BerthLaw? = null,
        start: LatLng = original.first(),
        aim: LatLng = original.last()
    ) = PullSetup(
        marginM = 25.0, coarseStepM = 20.0, priceStepM = 100.0,
        field = field, start = start, aim = aim, gate = gate,
        berth = berth, berthLambda = if (berth != null) 3.0 else 0.0, paceMps = 15.0
    )

    /** A point's east and north metres in [point]'s own frame. */
    private fun metres(p: LatLng): Pair<Double, Double> = Pair(
        (p.longitude - GateTestFixtures.LON0) * GateTestFixtures.M_PER_DEG_LON - 200.0,
        (p.latitude - GateTestFixtures.LAT0) * GateTestFixtures.M_PER_DEG_LAT - 1400.0
    )

    private fun assertSafeImprovement(setup: PullSetup, result: List<LatLng>) {
        assertEquals(original.first(), result.first())
        assertEquals(original.last(), result.last())
        assertTrue("the complete replacement is no dearer", cost(setup, result) <= cost(setup, original))
        assertClear(setup, result)
    }

    private fun assertClear(setup: PullSetup, line: List<LatLng>) = assertTrue("all legs keep clearance", clear(setup, line))

    private fun clear(setup: PullSetup, line: List<LatLng>): Boolean {
        val ctx = setup.context()
        return line.zipWithNext().all { (a, b) -> MultipassPull.legClear(a, b, ctx) }
    }

    private fun cost(setup: PullSetup, line: List<LatLng>) = RouteFinePass().lineCostSec(setup, line)

    /** The pass's own summary line among its move lines. */
    private fun summary(trace: List<String>) = trace.single { it.startsWith("APPROACH moved=") }

    /** The line's land turns — inner points whose skipping chord the clearance proof refuses for land. */
    private fun landTurns(setup: PullSetup, line: List<LatLng>): Int {
        val ctx = setup.context()
        return (1 until line.lastIndex).count {
            MultipassPull.legClearCause(line[it - 1], line[it + 1], ctx) == ChordRefusal.LAND
        }
    }

    private fun point(eastM: Double, northM: Double) = GateTestFixtures.at(northM + 1400.0, eastM + 200.0)

    private fun between(a: LatLng, b: LatLng, t: Double) = LatLng(
        a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t
    )
}
