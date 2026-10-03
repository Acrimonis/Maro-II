package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units

/**
 * **The early save's partial-plan builder** — the provisional points become a plan whose legs are
 * priced at the planned pace, dated by the caller's instant and anchored by the acquisition's start.
 */
class PartialPlanOfTest {

    private val a = RoutePoint(43.5000, 7.0000)
    private val b = RoutePoint(43.5100, 7.0000)
    private val c = RoutePoint(43.5200, 7.0000)

    @Test
    fun fewerThanTwoPointsReturnsNull() {
        assertNull(partialPlanOf(emptyList(), a, 9.0, 1_000L))
        assertNull(partialPlanOf(listOf(a), a, 9.0, 1_000L))
    }

    @Test
    fun buildsThePlanFromTheDrawnPointsAtThePlannedPace() {
        val plan = partialPlanOf(listOf(a, b, c), null, 9.0, 1_234L)!!

        val paceMps = Units.knotsToMps(9.0)
        val ab = SpatialOperations.haversine(LatLng(a.latitude, a.longitude), LatLng(b.latitude, b.longitude))
        val bc = SpatialOperations.haversine(LatLng(b.latitude, b.longitude), LatLng(c.latitude, c.longitude))

        assertEquals("the anchor falls back to the line's first point", a, plan.start)
        assertEquals("the destination is the last drawn point", c, plan.destination)
        assertEquals("one leg per segment", 2, plan.legTimesSec.size)
        assertEquals("the distance is the segments' sum", ab + bc, plan.distanceM, 0.001)
        assertEquals("each leg is the segment at the planned pace", ab / paceMps, plan.legTimesSec[0], 0.001)
        assertEquals("the duration is the legs' sum", (ab + bc) / paceMps, plan.durationSec, 0.001)
        assertEquals("the plan is dated by the caller's instant", 1_234L, plan.computedAtMs)
    }

    @Test
    fun usesTheAnchorWhenOneIsGiven() {
        val plan = partialPlanOf(listOf(a, b), c, 9.0, 0L)!!
        assertEquals(c, plan.start)
        assertEquals(b, plan.destination)
    }
}
