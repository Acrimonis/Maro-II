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
        val line = trace.single()
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
        assertTrue("and the cleared legs are priced", figure("priceMarks") > 0.0 && figure("priceReads") > 0.0)
        assertTrue("the slow-water test read the line's legs", figure("slowLegs") > 0.0)
        assertTrue("every clock is reported", listOf("ms", "clearMs", "priceMs", "slowMs").all { figure(it) >= 0.0 })
    }

    /**
     * **Pricing first and proving only the winners answers the very line the proof-first order did.** Over
     * forty seeded lines around the rock — free water, a second rock that refuses tempting replacements, a
     * shoal behind the measured gate, and a marker-only price that searches nothing — the two orders return
     * the same points, while the new order proves no more candidates than it priced in full and fewer than
     * the old one evaluated.
     */
    @Test
    fun pricingFirstAnswersTheProofFirstLine() {
        val random = java.util.Random(20261027L)
        val shoalRaster = GateTestFixtures.raster(130, 190, 20.0) { n, e ->
            if (hypot(n - 1500.0, e - 1500.0) < 80.0) 2.0 else 15.0
        }
        val shoalDepth = GateTestFixtures.depthOf(shoalRaster)
        val setups = listOf(
            "free" to setup(field()),
            "rock" to setup(field(extraHard = RouteCostSource.Hard(distanceAt = {
                (SpatialOperations.haversine(it, point(1200.0, 700.0)) - 60.0).coerceAtLeast(0.0)
            }))),
            "shoal" to setup(
                field(extraHard = depthGateSource(3.0) { shoalDepth(it.latitude, it.longitude) }),
                GateTestFixtures.gateOver(shoalRaster, hardM = 20.0)
            ),
            "marker" to setup(field(arm = PriceArm.MARKER))
        )
        var compared = 0
        var proofsSaved = 0
        repeat(10) {
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
                    Regex("(?:^| )$field=([0-9.]+)").find(lines.single())?.groupValues?.get(1)?.toDouble() ?: 0.0
                assertEquals("$name: the same windows and candidates", figure(proofFirst, "evals"), figure(priceFirst, "evals"), 0.0)
                val evaluatedBefore = figure(proofFirst, "evals") - figure(proofFirst, "outside")
                val provedNow = figure(priceFirst, "proofs") + figure(priceFirst, "windows")
                assertTrue("$name: no more proofs than the old order ran", provedNow <= evaluatedBefore)
                proofsSaved += (evaluatedBefore - provedNow).toInt()
                compared++
            }
        }
        assertEquals(40, compared)
        assertTrue("the reordering skips proofs on these lines ($proofsSaved)", proofsSaved > 0)
    }

    @Test(expected = CancellationException::class)
    fun cancellationInterruptsCandidateExploration() {
        var checks = 0
        RouteApproachPass.refine(setup(field()), original, bounds, 20.0, checkCancelled = {
            if (++checks == 8) throw CancellationException("cancelled")
        })
    }

    private fun field(extraHard: RouteCostSource.Hard? = null, arm: PriceArm = PriceArm.BAND): RouteCostField {
        fun price(p: LatLng) = if (SpatialOperations.haversine(p, rock) <= 300.0) 0.35 else 0.0
        return RouteCostField(listOfNotNull(
            RouteCostSource.Hard(distanceAt = { (SpatialOperations.haversine(it, rock) - 30.0).coerceAtLeast(0.0) }),
            extraHard,
            RouteCostSource.Soft(
                priceSec = ::price, tag = MultipassCellState.BAND,
                clearanceAt = { abs(SpatialOperations.haversine(it, rock) - 300.0) },
                armAt = { arm to price(it) }
            )
        ))
    }

    private fun setup(field: RouteCostField, gate: GateRule? = null) = PullSetup(
        marginM = 25.0, coarseStepM = 20.0, priceStepM = 100.0,
        field = field, start = original.first(), aim = original.last(), gate = gate, paceMps = 15.0
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

    private fun point(eastM: Double, northM: Double) = GateTestFixtures.at(northM + 1400.0, eastM + 200.0)

    private fun between(a: LatLng, b: LatLng, t: Double) = LatLng(
        a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t
    )
}
