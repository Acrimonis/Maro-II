package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units

/**
 * **The Speed limits attribution** — [slowTimeByLimit] charges each slow leg to the water standing at
 * its own midpoint and to nothing else: a leg inside a ring to that ring's limit, a leg inside the
 * 300 m band to the band's own entry, and a slow leg on open water — a bend's floor, an acceleration —
 * to **no** entry at all. There is no fold: a limit's figure is the full seconds its own legs take,
 * never a neighbour's, so the band's entry can never grow into the route's own duration.
 */
class SlowTimeByLimitTest {

    private val paceKn = 10.0
    private val bandLimitKn = 5.0

    /** Five points 0.01° apart, four legs: open-water ramp, 5 kn zone, fast, 5 kn band. */
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

    /**
     * **The rule, item by item: a leg in a zone counts in the zone's entry, and a slow leg on open
     * water counts in no entry at all.** This is the corrected successor of
     * `theRampFoldsIntoTheLimitItServes`: the 7 kn leg (43.00..43.01, its own water) is slow but stands
     * outside both the zone and the band, and the deleted fold used to charge it to the zone. The
     * zone's figure is now its own leg's seconds alone.
     */
    @Test
    fun aRampIsCountedInNoEntry() {
        val timed = timedLine()
        val d = SpatialOperations.haversine(timed.points[0], timed.points[1])
        val entries = slowTimeByLimit(timed, paceKn, zoneLimit, inZone, inBand, bandLimitKn)

        assertTrue("the band stands first", entries.first().isBand)
        assertEquals("the fast leg reports nothing and the open-water ramp counts nowhere", 2, entries.size)
        assertEquals(
            "the band entry is the band leg alone",
            d / Units.knotsToMps(5.0),
            entries[0].seconds,
            1e-6
        )
        assertEquals(
            "the zone entry is its own water alone, never the open-water ramp",
            d / Units.knotsToMps(5.0),
            entries[1].seconds,
            1e-6
        )
        assertEquals(
            "the open-water leg's seconds stand in no entry",
            d / Units.knotsToMps(5.0) + d / Units.knotsToMps(5.0),
            entries.sumOf { it.seconds },
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

    /**
     * **A mid-line bend's seconds are no limit's business.** One short in-band leg at the line's end
     * (43.03..43.04) stands after a fast leg, and a bend mid-line (43.01..43.02) stands between fast
     * legs. The band's entry must be that in-band leg's own seconds **alone**; the old outward search,
     * `for (d in 1..legs)`, charged the bend to the band — the nearest keyed water — so the panel's
     * 300 m row read grossly inflated on a route that avoids the band.
     */
    @Test
    fun aBendMidLineIsCountedInNoEntry() {
        // Legs: fast (43.005), bend slow (43.015), fast (43.025), in-band (43.035).
        val points = (0..4).map { LatLng(43.0 + 0.01 * it, 7.0) }
        val d = SpatialOperations.haversine(points[0], points[1])
        val timed = TimedLine(
            points,
            listOf(
                d / Units.knotsToMps(paceKn),
                d / Units.knotsToMps(5.0),
                d / Units.knotsToMps(paceKn),
                d / Units.knotsToMps(5.0)
            ),
            listOf(0.0, 0.0, 0.0, 0.0)
        )

        val entries = slowTimeByLimit(timed, paceKn, { null }, { false }, inBand, bandLimitKn)

        assertEquals("the band is the only entry", 1, entries.size)
        assertTrue("and it is the band", entries[0].isBand)
        assertEquals(
            "the band's figure is the in-band leg alone, never the mid-line bend's seconds",
            d / Units.knotsToMps(5.0),
            entries[0].seconds,
            1e-6
        )
    }

    /**
     * **The recorded shape at test scale: every leg slow, two of them on band water.** The device line
     * read `slowMetres == distance`, so its whole length was one contiguous slow run and the bounded
     * fold charged all of it to the band — the band's figure became the route's own `durationSec`
     * (≈ 12 min) where the time actually inside 300 m was ≈ 3 min. Thirty legs stand on water the band
     * never governs and legs 4 and 5 stand on the band's own water: the band's entry must be its own
     * two legs' seconds alone, and **decisively not the route's duration**.
     */
    @Test
    fun everyLegSlowStillLeavesTheBandItsOwnWaterAlone() {
        val points = (0..30).map { LatLng(43.0 + 0.001 * it, 7.0) }
        val slowMps = Units.knotsToMps(5.0)
        val legTimes = (0 until 30).map { i ->
            SpatialOperations.haversine(points[i], points[i + 1]) / slowMps
        }
        val timed = TimedLine(points, legTimes, legTimes.map { 0.0 })
        // Legs 4 and 5 (midpoints 43.0045, 43.0055) stand on the band's own water.
        val inBand: (LatLng) -> Boolean = { p -> p.latitude > 43.004 && p.latitude < 43.006 }

        val entries = slowTimeByLimit(timed, paceKn, { null }, { false }, inBand, bandLimitKn)

        assertEquals("the band is the only entry", 1, entries.size)
        assertTrue("and it is the band", entries[0].isBand)
        assertEquals(
            "the band's figure is its own two legs' seconds",
            legTimes[4] + legTimes[5],
            entries[0].seconds,
            1e-6
        )
        assertTrue(
            "and decisively not the route's own duration",
            entries[0].seconds < timed.durationSec - 1e-6
        )
    }

    /**
     * **A ramp between two limits belongs to neither** — the positional tie-break went with the fold,
     * and this is the corrected successor of `aRampBetweenTwoLimitsFoldsForward`: the middle leg is
     * slow but stands on open water, so A and B each keep their own water alone and the ramp's seconds
     * are counted nowhere. Placed between **two different** limits on purpose, which is where the old
     * positional rule bit.
     */
    @Test
    fun aRampBetweenTwoLimitsIsCountedInNoEntry() {
        // Legs: zone A (43.005), ramp slow (43.015), zone B (43.025).
        val points = (0..3).map { LatLng(43.0 + 0.01 * it, 7.0) }
        val d = SpatialOperations.haversine(points[0], points[1])
        val timed = TimedLine(
            points,
            listOf(
                d / Units.knotsToMps(5.0),
                d / Units.knotsToMps(5.0),
                d / Units.knotsToMps(7.0)
            ),
            listOf(0.0, 0.0, 0.0)
        )
        fun zoneA(p: LatLng) = p.latitude > 43.00 && p.latitude < 43.01
        fun zoneB(p: LatLng) = p.latitude > 43.02 && p.latitude < 43.03
        val inZone: (LatLng) -> Boolean = { zoneA(it) || zoneB(it) }
        val limitAt: (LatLng) -> Double? =
            { if (zoneA(it)) 5.0 else if (zoneB(it)) 7.0 else null }

        val entries = slowTimeByLimit(timed, paceKn, limitAt, inZone, { false }, bandLimitKn)

        assertTrue("no band water stands, so no band entry", entries.none { it.isBand })
        assertEquals("both limits stand", 2, entries.size)
        assertEquals("A keeps its own water", d / Units.knotsToMps(5.0), entries[0].seconds, 1e-6)
        assertEquals(
            "B keeps its own water, and the open-water ramp is counted in neither",
            d / Units.knotsToMps(7.0),
            entries[1].seconds,
            1e-6
        )
    }

    /**
     * **No double count and no orphan** — the entries' sum never exceeds the route's own slow seconds,
     * and a limit the line never stands on never appears. On a line standing only in the band, the sole
     * entry is the band's and nothing it never stood on is invented.
     */
    @Test
    fun theEntriesNeverExceedTheSlowSecondsAndInventNoLimit() {
        val points = (0..4).map { LatLng(43.0 + 0.01 * it, 7.0) }
        val d = SpatialOperations.haversine(points[0], points[1])
        val legTimes = listOf(
            d / Units.knotsToMps(paceKn),
            d / Units.knotsToMps(5.0),
            d / Units.knotsToMps(paceKn),
            d / Units.knotsToMps(5.0)
        )
        val timed = TimedLine(points, legTimes, legTimes.map { 0.0 })
        val slowSeconds = legTimes[1] + legTimes[3]

        val entries = slowTimeByLimit(timed, paceKn, { null }, { false }, inBand, bandLimitKn)

        assertEquals("only the band stands", 1, entries.size)
        assertTrue("no zone entry is invented for water the line never stood on", entries.none { !it.isBand })
        assertTrue(
            "the entries never claim more than the route's slow seconds",
            entries.sumOf { it.seconds } <= slowSeconds + 1e-9
        )
    }

    /**
     * **A route whose whole length stands outside the band reports no band entry** — a line with a zone
     * leg and a mid-line bend, nothing in the band, has a zone entry and no band one.
     */
    @Test
    fun aRouteOutsideTheBandReportsNoBandEntry() {
        // Legs: zone (43.005, keyed 5 kn), bend slow (43.015), fast (43.025). Nothing in the band.
        val points = (0..3).map { LatLng(43.0 + 0.01 * it, 7.0) }
        val d = SpatialOperations.haversine(points[0], points[1])
        val timed = TimedLine(
            points,
            listOf(
                d / Units.knotsToMps(5.0),
                d / Units.knotsToMps(5.0),
                d / Units.knotsToMps(paceKn)
            ),
            listOf(0.0, 0.0, 0.0)
        )
        val inZone: (LatLng) -> Boolean = { it.latitude > 43.00 && it.latitude < 43.01 }
        val limitAt: (LatLng) -> Double? = { p -> if (inZone(p)) 5.0 else null }

        val entries = slowTimeByLimit(timed, paceKn, limitAt, inZone, { false }, bandLimitKn)

        assertEquals("the zone stands", 1, entries.size)
        assertTrue("and no band entry stands on a line that never entered the band", entries.none { it.isBand })
    }
}
