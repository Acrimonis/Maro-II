package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.RoutePoint

/**
 * **The arrival face** (R93, amended 2026-10-09) — the paint's own reading of a followed split, pinned
 * as the **face** rather than the split's arithmetic: with fewer than two points remaining, fewer than
 * two is **zero** points — an empty run — so slot 0 carries no points, no casing is built, and the
 * travelled run carries the whole covered line in the shared dimming, so the route reads done while it
 * is still followed.
 *
 * [`routeFollowFace`] is the pure value the host paints from, so the arrival shape is read without a
 * Compose or osmdroid harness — the same JVM-honest limit [`OverlayPaintRankTest`] records. A plan and
 * a split are arranged here, but every assertion stands on the face's own three answers.
 */
class RouteArrivalFaceTest {

    private val computedAt = 1_700_000_000_000L
    private val p0 = RoutePoint(43.5000, 7.0000)
    private val p1 = RoutePoint(43.5100, 7.0000)
    private val p2 = RoutePoint(43.5200, 7.0000)

    private fun plan() = RoutePlan(
        start = p0,
        destination = p2,
        destinationMoved = false,
        points = listOf(p0, p1, p2),
        legTimesSec = listOf(120.0, 240.0),
        distanceM = 3000.0,
        durationSec = 360.0,
        computedAtMs = computedAt
    )

    @Test
    fun fewerThanTwoRemainingStandsSlotZeroAndCasingDown() {
        val followed = plan()
        val split = followed.splitAt(RoutePoint(43.5300, 7.0000))
        assertTrue("the fixture is the arrival case", split.remainingPoints.size < 2)

        val face = routeFollowFace(followed, split)

        assertTrue("slot 0 carries no points at arrival", face.slotZeroPoints.isEmpty())
        assertNull("and no casing is built", face.casingPoints)
    }

    @Test
    fun theTravelledRunCarriesTheWholeCoveredLineAtArrival() {
        val followed = plan()
        val split = followed.splitAt(RoutePoint(43.5300, 7.0000))

        val face = routeFollowFace(followed, split)

        val travelled = face.travelledPoints
        assertTrue("the travelled run draws at arrival", travelled != null)
        assertEquals("carrying every covered vertex", followed.points.size, travelled!!.size)
        assertEquals("opening on the plan's start", p0, travelled.first())
        val end = travelled.last()
        assertEquals("and closing on the destination", p2.latitude, end.latitude, 1e-9)
        assertEquals(p2.longitude, end.longitude, 1e-9)
    }

    @Test
    fun aStandingRemainderKeepsSlotZeroOnTheRunAheadAndTheCasingWithIt() {
        val followed = plan()
        val split = followed.splitAt(RoutePoint(43.5050, 7.0000))
        assertTrue("the fixture stands the remaining run", split.remainingPoints.size >= 2)

        val face = routeFollowFace(followed, split)

        assertTrue("slot 0 carries the run ahead", face.slotZeroPoints.size >= 2)
        assertEquals("and the casing under-strokes that run", split.remainingPoints, face.casingPoints)
        assertTrue("while the travelled run draws behind it", face.travelledPoints != null)
    }
}
