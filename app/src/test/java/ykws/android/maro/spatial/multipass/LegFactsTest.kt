package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.regulation.SpeedZone

/**
 * **The clock's per-leg facts change no reader's sum.** The profile keeps each leg's length, midpoint and raw
 * limit ([LegFacts]), and a caller reads the legs' water once ([LegWater]); the per-limit table, the ramps,
 * the shares and the slow share then read those arrays — the same doubles the bare walk re-reads — so a
 * line with its facts and the same line without them answer one number everywhere.
 */
class LegFactsTest {

    private val paceKn = 20.0
    private val zone = SpeedZone(
        "z", "Cap", 5.0,
        listOf(LatLng(43.499, 7.010), LatLng(43.499, 7.020), LatLng(43.501, 7.020), LatLng(43.501, 7.010), LatLng(43.499, 7.010))
    )
    private val set = ZoneSet(listOf(zone))
    private val limitAt: (LatLng) -> Double? = { p -> set.strictestDisplayLimitKnAt(p.latitude, p.longitude) }
    private val inZone: (LatLng) -> Boolean = { p -> set.containsAny(p.latitude, p.longitude) }
    private val inBand: (LatLng) -> Boolean = { p -> p.longitude > 7.028 }
    private val noCeiling: (LatLng) -> Double? = { null }
    private val line = listOf(LatLng(43.5, 7.0), LatLng(43.5, 7.03))

    @Test
    fun theProfileKeepsItsLegsFactsAndEveryReaderReadsTheSameSums() {
        val timed = timeLineWithProfile(line, paceKn, limitAt, noCeiling, sampleM = 10.0)
        assertNotNull("the profile keeps its legs' facts", timed.facts)
        val bare = timed.copy(facts = null)
        val water = LegWater.of(timed, inZone, inBand)

        assertEquals("one clock", bare.durationSec, timed.durationSec, 0.0)
        val table = slowTimeByLimit(timed, paceKn, limitAt, inZone, inBand, 5.0, water)
        assertTrue("the fixture crosses the zone", table.isNotEmpty())
        assertEquals("one per-limit table", slowTimeByLimit(bare, paceKn, limitAt, inZone, inBand, 5.0), table)
        assertEquals(
            "one ramp figure",
            rampSlowSeconds(bare, paceKn, limitAt, inZone, inBand, noCeiling),
            rampSlowSeconds(timed, paceKn, limitAt, inZone, inBand, noCeiling, water), 0.0
        )
        assertEquals("one share split", slowShares(bare, paceKn, inZone, inBand), slowShares(timed, paceKn, inZone, inBand))
        assertEquals("one slow share", zoneSlowShare(bare, paceKn), zoneSlowShare(timed, paceKn), 0.0)
    }
}
