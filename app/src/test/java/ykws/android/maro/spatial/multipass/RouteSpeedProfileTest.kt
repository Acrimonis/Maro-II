package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.Units
import org.junit.Assert.assertTrue
import org.junit.Test

/** The smooth speed profile's own contract: the enforced limit is a hard ceiling, never a target. */
class RouteSpeedProfileTest {

    private fun north(lat: Double) = LatLng(lat, 7.0)

    @Test
    fun enteringASlowZoneNeverExceedsTheLimitInsideIt() {
        val points = listOf(north(43.0), north(43.0045), north(43.009))
        val zoneMps = Units.knotsToMps(5.0)
        val paceMps = Units.knotsToMps(28.0)
        val timed = timeLineWithProfile(
            points, paceKn = 28.0,
            limitKnAt = { p -> if (p.latitude > 43.0054) 5.0 else null },
            ceilingKnAt = { null },
            sampleM = 25.0
        )
        for (i in timed.legSpeedsMps.indices) {
            val mid = (timed.points[i].latitude + timed.points[i + 1].latitude) / 2.0
            val cap = if (mid > 43.0054) zoneMps else paceMps
            assertTrue("leg $i speed ${timed.legSpeedsMps[i]} exceeds $cap", timed.legSpeedsMps[i] <= cap + 1e-6)
        }
        // The deceleration is anticipated: some free-water leg already runs below the pace.
        assertTrue(
            "a free-water leg decelerates before the zone",
            timed.legSpeedsMps.any { it < paceMps - 1e-6 }
        )
    }

    @Test
    fun leavingASlowZoneAcceleratesOnlyAfterTheBoundary() {
        val points = listOf(north(43.0), north(43.0045), north(43.009))
        val zoneMps = Units.knotsToMps(5.0)
        val paceMps = Units.knotsToMps(28.0)
        val timed = timeLineWithProfile(
            points, paceKn = 28.0,
            limitKnAt = { p -> if (p.latitude < 43.0045) 5.0 else null },
            ceilingKnAt = { null },
            sampleM = 25.0
        )
        for (i in timed.legSpeedsMps.indices) {
            val mid = (timed.points[i].latitude + timed.points[i + 1].latitude) / 2.0
            val cap = if (mid < 43.0045) zoneMps else paceMps
            assertTrue("leg $i speed ${timed.legSpeedsMps[i]} exceeds $cap", timed.legSpeedsMps[i] <= cap + 1e-6)
        }
    }

    @Test
    fun aCornerCeilingSlowsTheRoute() {
        val points = listOf(north(43.0), north(43.003), north(43.006), north(43.009))
        val capped = north(43.006)
        val free = timeLineWithProfile(points, 28.0, { null }, { null }, sampleM = 25.0)
        val cappedLine =
            timeLineWithProfile(points, 28.0, { null }, { p -> if (p == capped) 5.0 else null }, sampleM = 25.0)
        assertTrue("the curvature ceiling slows the route", cappedLine.durationSec > free.durationSec)
        val paceMps = Units.knotsToMps(28.0)
        for (s in cappedLine.legSpeedsMps) {
            assertTrue("no leg exceeds the pace", s <= paceMps + 1e-6)
        }
    }
}
