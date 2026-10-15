package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.RungSlowWater
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import ykws.android.maro.spatial.rungSlowWater

/**
 * **The verdict's own figure** — the seconds a line spends under a regulated limit, **weighed by that
 * limit's own shortfall against the pace** ([slowWaterSeverity]), as the engine settles it for the
 * ranking: the per-limit table's plain seconds ([slowTimeByLimit]: a ring's interior and the 300 m band,
 * each leg charged the part of its own time that lies on the water) weighed bucket by bucket, **plus**
 * the ramps that serve a limit ([rampSlowSeconds], which that table charges to no entry at all), each
 * riding the limit of the slow run it serves.
 *
 * The fixture is one coastal leg, and the readings are told apart leg by leg: a 5 kn ring's leg and a
 * 5 kn band's leg stand in the table, the 7 kn open-water leg running up to the band stands in the ramps
 * alone — its far end on the band's own edge, so it rides the band's limit — and the 5 kn leg at a bend,
 * slow because of the boat's own cornering ceiling with no limit in force, stands in **neither**, which
 * is what keeps the boat's own floor out of the verdict.
 *
 * The law itself is pinned beside it — 0.8 / 0.6 / 0.2 at a 25 kn pace — and the correction with it: a
 * ramp whose own ends meet **no** limit is left out of the figure rather than counted whole.
 */
class SlowWaterSecondsTest {

    private val paceKn = 10.0
    private val bandLimitKn = 5.0

    /**
     * **Six points, five legs** along one meridian: the ring's leg (43.00–43.01), a fast leg, the ramp
     * into the band (43.02–43.03, 7 kn on water carrying no limit), the band's own leg (43.03–43.04), and
     * the bend's floor (43.04–43.05, 5 kn under the corner pass's own ceiling and nothing else).
     */
    private fun timedLine(lat0: Double = 43.0): TimedLine {
        val points = (0..5).map { LatLng(lat0 + 0.01 * it, 7.0) }
        val d = SpatialOperations.haversine(points[0], points[1])
        val legTimes = listOf(
            d / Units.knotsToMps(5.0),
            d / Units.knotsToMps(paceKn),
            d / Units.knotsToMps(7.0),
            d / Units.knotsToMps(5.0),
            d / Units.knotsToMps(5.0)
        )
        return TimedLine(points, legTimes, legTimes.map { 0.0 })
    }

    private val inZone: (LatLng) -> Boolean = { p -> p.latitude > 43.00 && p.latitude < 43.01 }

    /**
     * The band's own water — the test is the engine's own width read ([insideBandWidthM]), which is
     * **inclusive at the outer edge**, so the ramp's far end standing there answers the band's limit.
     */
    private val inBand: (LatLng) -> Boolean = { p -> p.latitude >= 43.03 && p.latitude < 43.04 }

    /**
     * **The clock's own read, as the engine folds it** (`limitAtFor`): the strictest of the ring's limit
     * and the band's where either is in force, and `null` on the boat's own open water.
     */
    private val limitAt: (LatLng) -> Double? = { p ->
        val zone = if (inZone(p)) 5.0 else null
        val band = if (inBand(p)) bandLimitKn else null
        when {
            zone == null -> band
            band == null -> zone
            else -> minOf(zone, band)
        }
    }

    /** The corner pass's own ceiling — the bend at 43.05, and no other water capped by the boat. */
    private val bendCeiling: (LatLng) -> Double? = { p -> if (p.latitude == 43.05) 5.0 else null }

    /** **One short leg of the boat's own open water** (~10 m), the fixture every single-leg read is built on. */
    private val shortLeg = listOf(LatLng(43.0, 7.0), LatLng(43.00009, 7.0))

    private fun oneLeg(legSeconds: Double) = TimedLine(shortLeg, listOf(legSeconds), listOf(0.0))

    /**
     * **The engine's own cost site**, asked with the fixture's line: [rungSlowWater] is the very
     * expression the avoid engine settles a rung's cost with, so these readings are the engine's own
     * rather than a sum re-derived beside it.
     */
    private fun engineReading(timed: TimedLine): RungSlowWater =
        rungSlowWater(timed, paceKn, limitAt, inZone, inBand, bandLimitKn, bendCeiling)

    /** The figure the engine hands the ranking: the weighed table's buckets, plus the weighed ramps. */
    private fun slowWaterSeconds(timed: TimedLine): Double = engineReading(timed).cost.slowWaterSec

    /**
     * **The law itself** — a slow second's weight is its own limit's shortfall against the pace:
     * `(pace − limit) / pace`. At a 25 kn pace a second under 5 kn weighs 0.8, one under 10 kn weighs
     * 0.6 and one under 20 kn weighs 0.2.
     */
    @Test
    fun theLawWeighsASecondByItsLimitsShortfall() {
        assertEquals("5 kn at a 25 kn pace", 0.8, slowWaterSeverity(25.0, 5.0), 1e-9)
        assertEquals("10 kn at a 25 kn pace", 0.6, slowWaterSeverity(25.0, 10.0), 1e-9)
        assertEquals("20 kn at a 25 kn pace", 0.2, slowWaterSeverity(25.0, 20.0), 1e-9)
    }

    /**
     * **A limit at or above the pace weighs nothing** — no shortfall, no weight — both in the law and in
     * the figure the cost site hands the ranking.
     */
    @Test
    fun aLimitAtOrAboveThePaceWeighsNothing() {
        assertEquals("at the pace", 0.0, slowWaterSeverity(25.0, 25.0), 1e-9)
        assertEquals("above the pace is no negative weight", 0.0, slowWaterSeverity(25.0, 30.0), 1e-9)
        assertEquals(
            "and a second on such a limit's water adds nothing to the crown's figure",
            0.0,
            oneSecondUnder(limitKn = 30.0, paceKn = 25.0),
            1e-9
        )
    }

    /**
     * **The cost site weighs a second by its own limit**: one second at 5 kn against one at 20 kn, on the
     * very same clock, reads 0.8 against 0.2 at a 25 kn pace.
     */
    @Test
    fun theCostSiteWeighsASecondByItsOwnLimit() {
        assertEquals("one second at 5 kn", 0.8, oneSecondUnder(limitKn = 5.0, paceKn = 25.0), 1e-9)
        assertEquals("one second at 20 kn", 0.2, oneSecondUnder(limitKn = 20.0, paceKn = 25.0), 1e-9)
    }

    /**
     * **A ramp rides the limit of the slow run it serves** — read at its own ends, the stricter where
     * both carry one and the only one where a single end does.
     */
    @Test
    fun aRampRidesTheLimitOfTheSlowRunItServes() {
        assertEquals(
            "its far end stands on the 5 kn run it serves, so the second weighs 0.8",
            0.8,
            rampSeconds(endAtA = null, endAtB = 5.0, paceKn = 25.0),
            1e-9
        )
        assertEquals(
            "and where both its ends carry a limit the stricter is what it rides",
            0.8,
            rampSeconds(endAtA = 20.0, endAtB = 5.0, paceKn = 25.0),
            1e-9
        )
        assertEquals(
            "so a ramp onto a 20 kn run weighs 0.2",
            0.2,
            rampSeconds(endAtA = null, endAtB = 20.0, paceKn = 25.0),
            1e-9
        )
    }

    /**
     * **A ramp whose own ends meet no limit stays out of the figure** — no limit, no shortfall, no
     * weight — and no bucket is invented for it: the leg is the boat's own water, exactly as the
     * committed code leaves it.
     */
    @Test
    fun aRampWhoseEndsMeetNoLimitStaysOutOfTheFigure() {
        assertEquals(
            "the ramp reading charges it nothing rather than counting it whole",
            0.0,
            rampSeconds(endAtA = null, endAtB = null, paceKn = 25.0),
            1e-9
        )
        val reading = rungSlowWater(
            oneLeg(1.0), 25.0, { null }, { false }, { false }, bandLimitKn, { null }
        )
        assertEquals(
            "and the crown's own figure is zero beside it",
            0.0,
            reading.cost.slowWaterSec,
            1e-9
        )
        assertTrue("with no bucket invented for it", reading.byLimit.isEmpty())
    }

    /**
     * **The cost site's own wiring is what this fixture pins.** The engine's one expression feeds the
     * per-limit table **and** the ramps into the slow-water seconds the crown is weighed by, each bucket
     * weighed by its own limit's severity, and hands that table on to the answer beside it **in plain
     * seconds**: the table the answer publishes is the very one the cost was read from, and the settled
     * clock rides the cost untouched. Reverting the wiring — the table alone, or the ramps alone —
     * reddens the first assertion.
     */
    @Test
    fun theCostSiteFeedsTheWeighedTableAndTheRampsIntoTheRankingsFigure() {
        val timed = timedLine()
        val reading = engineReading(timed)
        val d = SpatialOperations.haversine(timed.points[0], timed.points[1])
        val table = slowTimeByLimit(timed, paceKn, limitAt, inZone, inBand, bandLimitKn)
        val weighedTable = table.sumOf { it.seconds * slowWaterSeverity(paceKn, it.limitKn) }
        val rampSeconds = rampSlowSeconds(timed, paceKn, limitAt, inZone, inBand, bendCeiling)

        assertTrue("the fixture's ramps are real, not zero", rampSeconds > 0.0)
        assertEquals(
            "the ranking's figure is the table's weighed buckets plus the ramps, and nothing else",
            weighedTable + rampSeconds,
            reading.cost.slowWaterSec,
            1e-9
        )
        assertEquals(
            "and the table the answer publishes stays plain seconds — the panel's own currency",
            d / Units.knotsToMps(5.0) + d / Units.knotsToMps(5.0),
            reading.byLimit.sumOf { it.seconds },
            1e-6
        )
        assertEquals(
            "the settled clock rides the cost untouched",
            timed.durationSec,
            reading.cost.durationSec,
            1e-9
        )
    }

    /**
     * **The band, the ring and the ramp that serves a limit are inside the sum**, while the bend's own
     * floor is outside it: the figure is each limit's own seconds weighed by its own shortfall, and the
     * ramp by the shortfall of the run it rides.
     */
    @Test
    fun theRingTheBandAndTheRampStandInsideTheSumAndTheBendFloorOutside() {
        val timed = timedLine()
        val d = SpatialOperations.haversine(timed.points[0], timed.points[1])
        val sum = slowWaterSeconds(timed)
        val weighed5 = slowWaterSeverity(paceKn, 5.0) * d / Units.knotsToMps(5.0)
        val weighedRamp = slowWaterSeverity(paceKn, 5.0) * d / Units.knotsToMps(7.0)

        assertEquals(
            "the ring's leg and the band's leg are weighed at 5 kn, and the ramp rides the band's limit",
            2 * weighed5 + weighedRamp,
            sum,
            1e-6
        )
        assertTrue(
            "the bend's own floor of 5 kn is no leg of the sum",
            sum < 2 * weighed5 + weighedRamp + weighed5
        )
    }

    /**
     * **A line slowed only by the boat's own cornering spends nothing under a limit**: with the ceiling
     * below the pace at the leg's own end and no ring, band or limit anywhere, both readings answer zero.
     */
    @Test
    fun aBendFloorAloneCountsNowhere() {
        val points = listOf(LatLng(43.0, 7.0), LatLng(43.01, 7.0))
        val d = SpatialOperations.haversine(points[0], points[1])
        val floor = TimedLine(
            points,
            listOf(d / Units.knotsToMps(5.0)),
            listOf(Units.knotsToMps(5.0))
        )
        val ceilingOnTheLeg: (LatLng) -> Double? = { p -> if (p.latitude == 43.01) 5.0 else null }

        assertEquals("the table charges the floor to no entry", 0, slowTimeByLimit(floor, paceKn, { null }, { false }, { false }, bandLimitKn).size)
        assertEquals(
            "and the ramps leave it out as the boat's own water",
            0.0,
            rampSlowSeconds(floor, paceKn, { null }, { false }, { false }, ceilingOnTheLeg),
            1e-9
        )
    }

    /**
     * **The ramp reading never charges the limit's own water twice**: a leg inside a ring or the band is
     * the table's entry, so [rampSlowSeconds] passes it by and answers the open-water legs alone, each
     * ridden by the limit of the run it serves.
     */
    @Test
    fun theRampReadingLeavesTheLimitWaterToTheTable() {
        val timed = timedLine()
        val d = SpatialOperations.haversine(timed.points[0], timed.points[1])
        val ramps = rampSlowSeconds(timed, paceKn, limitAt, inZone, inBand, bendCeiling)
        val table = slowTimeByLimit(timed, paceKn, limitAt, inZone, inBand, bandLimitKn)

        assertEquals(
            "the zones' and the band's legs are no ramp: the reading is the one open-water leg, weighed",
            slowWaterSeverity(paceKn, 5.0) * d / Units.knotsToMps(7.0),
            ramps,
            1e-6
        )
        assertEquals("the table keeps the ring's and the band's legs", 2, table.size)
        assertTrue("and the band's entry stands first", table.first().isBand)
    }

    /**
     * **One slow second on [limitKn]'s own water**, as the engine's cost site weighs it: a single leg of
     * one second inside a ring of that limit, at [paceKn], with nothing else on the line.
     */
    private fun oneSecondUnder(limitKn: Double, paceKn: Double): Double =
        rungSlowWater(
            oneLeg(1.0), paceKn, { limitKn }, { true }, { false }, bandLimitKn, { null }
        ).cost.slowWaterSec

    /**
     * **One slow second on open water**, with the limit the fixture puts on the leg's own two ends —
     * `null` where an end meets none — read by the engine's own ramp site.
     */
    private fun rampSeconds(endAtA: Double?, endAtB: Double?, paceKn: Double): Double {
        val reader: (LatLng) -> Double? = { p ->
            if (p.latitude == shortLeg[0].latitude) endAtA else endAtB
        }
        return rampSlowSeconds(oneLeg(1.0), paceKn, reader, { false }, { false }, { null })
    }
}
