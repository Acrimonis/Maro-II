package ykws.android.maro.spatial.avoid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units

/**
 * **The Speed limits attribution** — [slowTimeByLimit] sums each slow leg's seconds under the limit
 * that governs it, folds the ramps into the limit they serve, and keeps the 300 m band as its own entry.
 */
class SlowTimeByLimitTest {

    private val paceKn = 10.0
    private val bandLimitKn = 5.0

    /** Five points 0.01° apart, four legs: ramp, 5 kn zone, fast, 5 kn band. */
    private fun timedLine(lat0: Double = 43.0): TimedLine {
        val points = (0..4).map { LatLng(lat0 + 0.01 * it, 7.0) }
        val d = SpatialOperations.haversine(points[0], points[1])
        val paceMps = Units.knotsToMps(paceKn)
        val legTimes = listOf(
            d / Units.knotsToMps(7.0),
            d / Units.knotsToMps(5.0),
            d / paceMps,
            d / Units.knotsToMps(5.0)
        )
        return TimedLine(points, legTimes, legTimes.map { 0.0 })
    }

    private val inZone: (LatLng) -> Boolean = { p -> p.latitude > 43.01 && p.latitude < 43.02 }
    private val inBand: (LatLng) -> Boolean = { p -> p.latitude > 43.03 && p.latitude < 43.04 }
    private val zoneLimit: (LatLng) -> Double? = { p -> if (p.latitude > 43.01 && p.latitude < 43.02) 5.0 else null }

    @Test
    fun theRampFoldsIntoTheLimitItServes() {
        val timed = timedLine()
        val d = SpatialOperations.haversine(timed.points[0], timed.points[1])
        val entries = slowTimeByLimit(timed, paceKn, zoneLimit, inZone, inBand, bandLimitKn)

        assertTrue("the band stands first", entries.first().isBand)
        assertEquals("the fast leg reports nothing, so two entries stand", 2, entries.size)
        assertEquals(
            "the band entry is the band leg alone",
            d / Units.knotsToMps(5.0),
            entries[0].seconds,
            1e-6
        )
        assertEquals(
            "the zone entry counts its ramp",
            d / Units.knotsToMps(5.0) + d / Units.knotsToMps(7.0),
            entries[1].seconds,
            1e-6
        )
        assertEquals(5.0, entries[1].limitKn, 1e-9)
    }

    @Test
    fun theZoneBeatsTheBandWhereBothHold() {
        val timed = timedLine()
        // The band's leg 43.03..43.04 is also inside a zone: it must be credited to the zone alone.
        val zoneOverBand: (LatLng) -> Boolean = { p -> p.latitude > 43.03 && p.latitude < 43.04 }
        val entries = slowTimeByLimit(timed, paceKn, { 5.0 }, zoneOverBand, inBand, bandLimitKn)

        assertTrue("nothing stands as the band when a zone covers it", entries.none { it.isBand })
    }

    @Test
    fun anOrdinaryRouteReportsNothing() {
        val points = (0..1).map { LatLng(43.0 + 0.01 * it, 7.0) }
        val d = SpatialOperations.haversine(points[0], points[1])
        val timed = TimedLine(points, listOf(d / Units.knotsToMps(paceKn)), listOf(0.0))

        assertTrue(
            slowTimeByLimit(timed, paceKn, { null }, { false }, { false }, bandLimitKn).isEmpty()
        )
    }
}
