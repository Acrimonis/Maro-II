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
 * **The verdict's own figure** — the seconds a line spends under a regulated limit, as the engine
 * settles it for the ranking: the per-limit table's own seconds ([slowTimeByLimit]: a ring's interior and
 * the 300 m band, each leg charged the part of its own time that lies on the water) **plus** the ramps
 * that serve a limit ([rampSlowSeconds], which that table charges to no entry at all).
 *
 * The fixture is one coastal leg, and the two readings are told apart leg by leg: a 5 kn ring's leg and a
 * 5 kn band's leg stand in the table; the 7 kn open-water leg running up to the band stands in the ramps
 * alone, since a ramp is no limit's water; and the 5 kn leg at a bend — slow because of the boat's own
 * cornering ceiling, with no limit in force — stands in **neither**, which is what keeps the boat's own
 * floor out of the verdict.
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
    private val inBand: (LatLng) -> Boolean = { p -> p.latitude > 43.03 && p.latitude < 43.04 }
    private val zoneLimit: (LatLng) -> Double? = { p -> if (p.latitude > 43.00 && p.latitude < 43.01) 5.0 else null }

    /** The corner pass's own ceiling — the bend at 43.05, and no other water capped by the boat. */
    private val bendCeiling: (LatLng) -> Double? = { p -> if (p.latitude == 43.05) 5.0 else null }

    /**
     * **The engine's own cost site**, asked with the fixture's line: [rungSlowWater] is the very
     * expression the avoid engine settles a rung's cost with, so these readings are the engine's own
     * rather than a sum re-derived beside it.
     */
    private fun engineReading(timed: TimedLine): RungSlowWater =
        rungSlowWater(timed, paceKn, zoneLimit, inZone, inBand, bandLimitKn, bendCeiling)

    /** The figure the engine hands the ranking: the table's seconds, plus the ramps'. */
    private fun slowWaterSeconds(timed: TimedLine): Double = engineReading(timed).cost.slowWaterSec

    /**
     * **The cost site's own wiring is what this fixture pins.** The engine's one expression feeds the
     * per-limit table **and** the ramps into the slow-water seconds the crown is weighed by, and hands
     * that same table on to the answer beside it: the ranking's figure is the table's sum plus the
     * ramps', the table the answer publishes is the very one the cost was read from, and the settled
     * clock rides the cost untouched. Reverting the wiring — the table alone, or the ramps alone —
     * reddens the first assertion.
     */
    @Test
    fun theCostSiteFeedsTheTableAndTheRampsIntoTheRankingsFigure() {
        val timed = timedLine()
        val reading = engineReading(timed)
        val tableSeconds = slowTimeByLimit(timed, paceKn, zoneLimit, inZone, inBand, bandLimitKn)
            .sumOf { it.seconds }
        val rampSeconds = rampSlowSeconds(timed, paceKn, inZone, inBand, bendCeiling)

        assertTrue("the fixture's ramps are real, not zero", rampSeconds > 0.0)
        assertEquals(
            "the ranking's figure is the per-limit table plus the ramps, and nothing else",
            tableSeconds + rampSeconds,
            reading.cost.slowWaterSec,
            1e-9
        )
        assertEquals(
            "and the table the answer publishes is the one the cost was read from",
            tableSeconds,
            reading.byLimit.sumOf { it.seconds },
            1e-9
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
     * floor is outside it: the two figures the ranking reads are the limit's water and the ramps' alone.
     */
    @Test
    fun theRingTheBandAndTheRampStandInsideTheSumAndTheBendFloorOutside() {
        val timed = timedLine()
        val d = SpatialOperations.haversine(timed.points[0], timed.points[1])
        val sum = slowWaterSeconds(timed)

        assertEquals(
            "the ring's leg, the band's leg and the ramp's own seconds are the whole of the sum",
            d / Units.knotsToMps(5.0) + d / Units.knotsToMps(5.0) + d / Units.knotsToMps(7.0),
            sum,
            1e-6
        )
        assertEquals(
            "and the ramp alone is what the per-limit table leaves out",
            d / Units.knotsToMps(7.0),
            sum - slowTimeByLimit(timed, paceKn, zoneLimit, inZone, inBand, bandLimitKn).sumOf { it.seconds },
            1e-6
        )
        assertTrue(
            "the bend's own floor of 5 kn stands outside the sum, which the limit water alone fills",
            sum < d / Units.knotsToMps(5.0) + d / Units.knotsToMps(5.0) + d / Units.knotsToMps(7.0) +
                d / Units.knotsToMps(5.0)
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
            rampSlowSeconds(floor, paceKn, { false }, { false }, ceilingOnTheLeg),
            1e-9
        )
    }

    /**
     * **The ramp reading never charges the limit's own water twice**: a leg inside a ring or the band is
     * the table's entry, so [rampSlowSeconds] passes it by and answers the open-water legs alone.
     */
    @Test
    fun theRampReadingLeavesTheLimitWaterToTheTable() {
        val timed = timedLine()
        val d = SpatialOperations.haversine(timed.points[0], timed.points[1])
        val ramps = rampSlowSeconds(timed, paceKn, inZone, inBand, bendCeiling)
        val table = slowTimeByLimit(timed, paceKn, zoneLimit, inZone, inBand, bandLimitKn)

        assertEquals(
            "the zones' and the band's legs are no ramp",
            d / Units.knotsToMps(7.0),
            ramps,
            1e-6
        )
        assertEquals("the table keeps the ring's and the band's legs", 2, table.size)
        assertTrue("and the band's entry stands first", table.first().isBand)
    }
}
