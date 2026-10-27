package ykws.android.maro.spatial.multipass

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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
            }
        }
        for ((path, count) in paths) {
            assertTrue("the new order takes its $path path on these lines ($paths)", count > 0.0)
        }
        assertTrue("the berth-priced water is searched, not skipped", berthWindows > 0.0)
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

    private fun setup(field: RouteCostField, gate: GateRule? = null, berth: BerthLaw? = null) = PullSetup(
        marginM = 25.0, coarseStepM = 20.0, priceStepM = 100.0,
        field = field, start = original.first(), aim = original.last(), gate = gate,
        berth = berth, berthLambda = if (berth != null) 3.0 else 0.0, paceMps = 15.0
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
