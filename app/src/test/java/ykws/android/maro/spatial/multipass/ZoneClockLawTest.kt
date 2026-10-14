package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import kotlin.math.abs

/**
 * **The split between a zone's price and its clock, pinned at the one seam it lives on.**
 *
 * A zone may carry a routing-only `speedRoute` price above its displayed limit, buying the search an
 * advantage over water that is still signed at the display. The two laws must therefore answer
 * differently at the same point: [`strictestLimitKnAt`] (the A\*'s stored `ZoneRing` limits and the
 * guard's point read) and [`strictestDisplayLimitKnAt`] (the clock's own read), which
 * [`limitAtFor`] hands to every reported figure — the leg speed, the trip's clock, the line's colour
 * and the slow-limit attribution.
 *
 * These pins fail the moment the clock is fed the price again: the timing legs would read `10` kn
 * where the water is signed `5`, which is the one defect this split exists to prevent.
 */
class ZoneClockLawTest {

    /** Display 5 kn, routed at 10 — the shape a `regulatedZone.speedRoute` override produces. */
    private val overridden = SpeedZone(
        id = "z",
        name = "Cap",
        speedLimitKn = 5.0,
        outerRing = rectRing(43.49, 43.51, 6.98, 7.02),
        routeLimitKn = 10.0
    )

    private val at = LatLng(43.50, 7.00)

    /** The line the clock times: wholly inside the zone, so every leg is one regime. */
    private val line = listOf(LatLng(43.50, 6.99), LatLng(43.50, 7.01))

    private val pace = 20.0

    @Test
    fun theTwoLawsAnswerDifferentlyAtTheSamePoint() {
        val world = ZoneWorld(listOf(overridden))

        assertEquals("the price law reads the routing-only override", 10.0, world.zoneLimitKnAt(43.50, 7.00)!!, 0.0)
        assertEquals("the clock law reads the displayed limit", 5.0, world.zoneDisplayLimitKnAt(43.50, 7.00)!!, 0.0)
        assertEquals("and the clock's own lambda takes the displayed one", 5.0, limitAtFor(world)(at)!!, 0.0)
    }

    @Test
    fun theClockTimesTheDisplayedLimitWhileTheSearchPricesTheOverride() {
        val world = ZoneWorld(listOf(overridden))
        val clockLimit = limitAtFor(world)
        val priceLimit = { p: LatLng -> world.zoneLimitKnAt(p.latitude, p.longitude) }

        val timed = timeLineWithLimits(line, pace, clockLimit, sampleM = 25.0)
        assertEquals("one leg, the line's own", 1, timed.legTimesSec.size)
        assertTrue(
            "every leg is made good at the displayed limit, never the negotiated price",
            timed.legSpeedsMps.all { abs(it - Units.knotsToMps(5.0)) < 1e-9 }
        )
        val dist = SpatialOperations.haversine(line[0], line[1])
        assertEquals(
            "the trip's clock is the displayed limit's own time",
            dist / Units.knotsToMps(5.0),
            timed.durationSec,
            1e-6
        )

        // The same line under the price law reads the override — the leak the split prevents.
        val priced = timeLineWithLimits(line, pace, priceLimit, sampleM = 25.0)
        assertTrue(
            "the price law over the same line would report the override",
            priced.legSpeedsMps.all { abs(it - Units.knotsToMps(10.0)) < 1e-9 }
        )
        assertTrue("so the two clocks genuinely differ", priced.durationSec < timed.durationSec)
    }

    @Test
    fun aZoneWithoutAnOverrideReadsTheSameOnBothLaws() {
        val plain = SpeedZone("p", "Plain", 6.0, rectRing(43.49, 43.51, 6.98, 7.02))
        val world = ZoneWorld(listOf(plain))

        assertEquals(6.0, world.zoneLimitKnAt(43.50, 7.00)!!, 0.0)
        assertEquals(6.0, world.zoneDisplayLimitKnAt(43.50, 7.00)!!, 0.0)
        assertEquals("an override-free zone is a no-op for the split", 6.0, limitAtFor(world)(at)!!, 0.0)
    }

    @Test
    fun theDisplayReadAnswersTheStrictestOfTheZonesOverThePoint() {
        val slower = SpeedZone("a", "Slower", 4.0, rectRing(43.49, 43.51, 6.98, 7.02), routeLimitKn = 12.0)
        val faster = SpeedZone("b", "Faster", 8.0, rectRing(43.49, 43.51, 6.98, 7.02), routeLimitKn = 14.0)

        assertEquals(
            "the strictest displayed limit wins, the override never reaching it",
            4.0,
            strictestDisplayLimitKnAt(listOf(slower, faster), emptySet(), 43.50, 7.00)!!,
            0.0
        )
        assertEquals(
            "and the price law still reads each zone's own override",
            12.0,
            strictestLimitKnAt(listOf(slower, faster), emptySet(), 43.50, 7.00)!!,
            0.0
        )
    }

    @Test
    fun aWorldThatAnswersNoDisplayReadTimesNoZoneLimit() {
        // The deliberate default: a fake that implements only the price read reports no clock limit at
        // all, so an override-zone test fails loudly rather than quietly timing the negotiated price.
        val world = PriceOnlyWorld(listOf(overridden))

        assertNull("no display law answered means no zone limit in force", world.zoneDisplayLimitKnAt(43.50, 7.00))
        assertNull(limitAtFor(world)(at))
    }

    @Test
    fun theBandKeepsOneLawWhateverTheZoneOverride() {
        // The 300 m band has no override key, so its arms are the same on both laws.
        val world = ZoneWorld(listOf(overridden), bandWidthM = 100.0, coastM = 50.0)
        val bandKn = AppConfig.routeAvoidZone300LimitKn

        assertEquals(
            "the band's own limit stands beside the zone's displayed limit, the lesser winning",
            minOf(5.0, bandKn),
            limitAtFor(world)(at)!!,
            0.0
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun rectRing(latSouth: Double, latNorth: Double, lonWest: Double, lonEast: Double): List<LatLng> =
        listOf(
            LatLng(latSouth, lonWest),
            LatLng(latSouth, lonEast),
            LatLng(latNorth, lonEast),
            LatLng(latNorth, lonWest),
            LatLng(latSouth, lonWest)
        )

    /** A water-everywhere world whose only source is its zones, answering **both** limit laws. */
    private open class ZoneWorld(
        private val zones: List<SpeedZone>,
        override val bandWidthM: Double = 0.0,
        private val coastM: Double = Double.MAX_VALUE
    ) : MultipassWorld {
        override val coastlineReady: Boolean get() = true
        override val depthReady: Boolean get() = true
        override val regionBounds: BBox? get() = null

        override fun segmentsIn(box: BBox): List<MultipassEdge> = emptyList()
        override fun openCoastIn(box: BBox): List<List<LatLng>> = emptyList()
        override fun isWater(latitude: Double, longitude: Double): Boolean = true
        override fun distanceToCoastM(latitude: Double, longitude: Double): Double = coastM
        override fun depthAt(latitude: Double, longitude: Double): DepthSample = DepthSample.NONE
        override fun speedZonesIn(box: BBox): List<SpeedZone> = speedZonesInBox(zones, box, emptySet())
        override fun zoneLimitKnAt(latitude: Double, longitude: Double): Double? =
            strictestLimitKnAt(zones, emptySet(), latitude, longitude)
        override fun zoneDisplayLimitKnAt(latitude: Double, longitude: Double): Double? =
            strictestDisplayLimitKnAt(zones, emptySet(), latitude, longitude)
    }

    /** The price law alone — the shape an unwired fake has, which must fail loudly. */
    private class PriceOnlyWorld(private val zones: List<SpeedZone>) : ZoneWorld(zones) {
        override fun zoneDisplayLimitKnAt(latitude: Double, longitude: Double): Double? = null
    }
}
