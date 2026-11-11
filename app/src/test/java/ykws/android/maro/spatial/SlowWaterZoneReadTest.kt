package ykws.android.maro.spatial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.multipass.ZoneSet

/**
 * **The containment-only zone read a track's slow-water walk asks of every point** —
 * [ZoneSet.strictestDisplayLimitKnAt] — against the full point query it replaced
 * ([SpeedZoneIndex.query]'s `mostRestrictiveSpeedKn`). The walk wants the strictest limit of the zones
 * holding the point and nothing else, so the two must agree everywhere: nested zones of two limits, a zone
 * with a hole, a degenerate ring, and points inside, in the hole, outside, and in a zone's box but outside
 * its ring.
 */
class SlowWaterZoneReadTest {

    private val outer = SpeedZone(
        id = "outer", name = "Outer", speedLimitKn = 10.0,
        outerRing = listOf(
            LatLng(43.5500, 7.100), LatLng(43.5500, 7.102), LatLng(43.5480, 7.102),
            LatLng(43.5480, 7.100), LatLng(43.5500, 7.100)
        )
    )

    private val inner = SpeedZone(
        id = "inner", name = "Inner", speedLimitKn = 5.0,
        outerRing = listOf(
            LatLng(43.5496, 7.1012), LatLng(43.5496, 7.1018), LatLng(43.5490, 7.1018),
            LatLng(43.5490, 7.1012), LatLng(43.5496, 7.1012)
        )
    )

    private val withHole = SpeedZone(
        id = "hole", name = "With Hole", speedLimitKn = 8.0,
        outerRing = listOf(
            LatLng(43.5600, 7.130), LatLng(43.5600, 7.135), LatLng(43.5550, 7.135),
            LatLng(43.5550, 7.130), LatLng(43.5600, 7.130)
        ),
        holes = listOf(
            listOf(
                LatLng(43.5582, 7.1322), LatLng(43.5582, 7.1328), LatLng(43.5572, 7.1328),
                LatLng(43.5572, 7.1322), LatLng(43.5582, 7.1322)
            )
        )
    )

    /** A triangle: its box also holds points outside its ring. */
    private val triangle = SpeedZone(
        id = "triangle", name = "Triangle", speedLimitKn = 6.0,
        outerRing = listOf(
            LatLng(43.5700, 7.150), LatLng(43.5700, 7.154), LatLng(43.5660, 7.150), LatLng(43.5700, 7.150)
        )
    )

    /** Fewer than three vertices bound nothing, in both reads. */
    private val degenerate = SpeedZone(
        id = "degenerate", name = "Degenerate", speedLimitKn = 2.0,
        outerRing = listOf(LatLng(43.5800, 7.170), LatLng(43.5800, 7.172))
    )

    private val zones = listOf(outer, inner, withHole, triangle, degenerate)

    @Test
    fun theContainmentReadAgreesWithTheFullQueryAtEveryPoint() {
        val index = SpeedZoneIndex(zones)
        val set = ZoneSet(zones)
        var answered = 0

        // A fine grid, offset off every ring vertex so no point stands on a boundary.
        var lat = 43.5455
        while (lat < 43.5825) {
            var lon = 7.0951
            while (lon < 7.1755) {
                val full = index.query(lat, lon).mostRestrictiveSpeedKn
                val contained = set.strictestDisplayLimitKnAt(lat, lon)
                assertEquals("at $lat, $lon", full, contained)
                if (contained != null) answered++
                lon += 0.00037
            }
            lat += 0.00031
        }
        assertEquals("the grid must exercise zones, not only open water", true, answered > 100)
    }

    @Test
    fun theStrictestOfNestedZonesWinsAHoleIsOpenAndADegenerateRingBoundsNothing() {
        val set = ZoneSet(zones)

        assertEquals("inside both nested zones: the strictest", 5.0, set.strictestDisplayLimitKnAt(43.5493, 7.1015)!!, 1e-9)
        assertEquals("inside the outer zone only", 10.0, set.strictestDisplayLimitKnAt(43.5485, 7.1005)!!, 1e-9)
        assertNull("in the hole", set.strictestDisplayLimitKnAt(43.5577, 7.1325))
        assertEquals("beside the hole, in the ring", 8.0, set.strictestDisplayLimitKnAt(43.5595, 7.1345)!!, 1e-9)
        assertNull("inside the triangle's box, outside its ring", set.strictestDisplayLimitKnAt(43.5662, 7.1535))
        assertNotNull("inside the triangle", set.strictestDisplayLimitKnAt(43.5695, 7.1505))
        assertNull("on the degenerate ring", set.strictestDisplayLimitKnAt(43.5800, 7.1710))
        assertNull("an empty set answers open water", ZoneSet(emptyList()).strictestDisplayLimitKnAt(43.5493, 7.1015))
    }
}
