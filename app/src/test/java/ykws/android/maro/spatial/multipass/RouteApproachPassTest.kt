package ykws.android.maro.spatial.multipass

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
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
     * seeded lines around the rock, each refined over six waters — free water, a mild slow disc whose best is
     * mostly travel time (where the travel cut fires), a second rock standing on the
     * free answer (so a would-be winner is refused by its proof), a shoal behind the measured gate, the same
     * shoal with the gate's berth priced (the one price whose sign rests on a config clamp), and a marker-only
     * price that searches nothing — return the same points in both orders. Every path the new order can take is
     * shown to run somewhere on these lines, asserted as **present** rather than counted so a fixture tweak does
     * not break it: the travel cut, the first-leg cut, a full price that loses, a proof that refuses, and a
     * search over berth-priced water.
     */
    @Test
    fun pricingFirstAnswersTheProofFirstLine() {
        val random = java.util.Random(20261027L)
        val setups = sixWaters()
        val paths = linkedMapOf("cutTravel" to 0.0, "cutLeg" to 0.0, "lost" to 0.0, "refusedClear" to 0.0)
        var berthWindows = 0.0
        var splitEvals = 0.0
        repeat(8) {
            val line = listOf(
                original[0],
                point(random.nextDouble() * 200.0 - 100.0, 260.0 + random.nextDouble() * 120.0),
                point(1800.0 + random.nextDouble() * 1200.0, random.nextDouble() * 700.0 - 100.0),
                original[3]
            )
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
            }
        }
        for ((path, count) in paths) {
            assertTrue("the new order takes its $path path on these lines ($paths)", count > 0.0)
        }
        assertTrue("the berth-priced water is searched, not skipped", berthWindows > 0.0)
        assertTrue("splits are searched in both orders on these lines", splitEvals > 0.0)
    }

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
     */
    @Test
    fun aTurnRoundOneHeadlandIsNotSplit() {
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
        secondDisc: LatLng? = null
    ): RouteCostField {
        val centres = listOfNotNull(rock, secondDisc)
        fun price(p: LatLng) = if (centres.any { SpatialOperations.haversine(p, it) <= 300.0 }) rate else 0.0
        return RouteCostField(listOfNotNull(
            RouteCostSource.Hard(distanceAt = { (SpatialOperations.haversine(it, rock) - 30.0).coerceAtLeast(0.0) }),
            extraHard,
            RouteCostSource.Soft(
                priceSec = ::price, tag = MultipassCellState.BAND,
                clearanceAt = { p -> centres.minOf { abs(SpatialOperations.haversine(p, it) - 300.0) } },
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
