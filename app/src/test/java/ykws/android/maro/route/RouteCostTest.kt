package ykws.android.maro.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.data.regulation.SpeedZoneQuery

/**
 * The objective of plan §3 — time-primary with the prices that slide it towards distance-and-legality
 * — and the feasibility gate that no price may breach (§4).
 */
class RouteCostTest {

    private val zone5 = SpeedZone(id = "z5", name = "Test 5 kn", speedLimitKn = 5.0, outerRing = emptyList())

    private fun inZone(lat: Double, lon: Double): SpeedZoneQuery =
        if (lon > 7.010) {
            SpeedZoneQuery(
                allInsideZones = listOf(zone5),
                mostRestrictiveSpeedKn = 5.0,
                insideAnyZone = true,
                distanceToBoundaryM = -20.0,
            )
        } else {
            SpeedZoneQuery()
        }

    private fun config() = RouteConfig()

    @Test
    fun `a leg inside a 5 kn zone costs five times the same leg in free water`() {
        val cost = RouteCostModel(config(), RouteHarness.fakeContext(zoneQueryFn = ::inZone))
        val a = LatLng(43.5100, 7.0050)
        val b = LatLng(43.5100, 7.0060)

        val freeSame = RouteCostModel(config(), RouteHarness.fakeContext()).legTimeS(a, b)
        val zoned = cost.legTimeS(LatLng(43.5100, 7.0150), LatLng(43.5100, 7.0160))

        assertEquals(5.0, zoned / freeSame, 1e-6)
    }

    @Test
    fun `the limits read inside, outside, and from the 300 m band when D4 says it counts`() {
        val cost = RouteCostModel(config(), RouteHarness.fakeContext(zoneQueryFn = ::inZone))
        assertEquals(5.0, cost.limitKnAt(43.5100, 7.0150)!!, 1e-9)
        assertNull("free water, no band", cost.limitKnAt(43.5100, 7.0050))

        // Within 300 m of the coast, on water: the band's implied 5 kn applies.
        val inshore = RouteHarness.fakeContext(coastDistanceM = { _, _ -> 120.0 })
        assertEquals(5.0, RouteCostModel(config(), inshore).limitKnAt(43.5100, 7.0050)!!, 1e-9)
        assertNull(
            "and stops applying when D4 says the band is geometry only",
            RouteCostModel(config().copy(band300IsZone = false), inshore).limitKnAt(43.5100, 7.0050),
        )
    }

    @Test
    fun `the 2 to 3 m band is priced, not gated`() {
        val shallow = RouteCostModel(
            config(),
            RouteHarness.fakeContext(depth = { _, _ -> 2.5f }, coastDistanceM = { _, _ -> 5_000.0 }),
        )
        val deep = RouteCostModel(
            config(),
            RouteHarness.fakeContext(depth = { _, _ -> 20f }, coastDistanceM = { _, _ -> 5_000.0 }),
        )
        val a = LatLng(43.5100, 7.0050)
        val b = LatLng(43.5100, 7.0060)
        assertEquals(2.0, shallow.legTimeS(a, b) / deep.legTimeS(a, b), 1e-6)
    }

    @Test
    fun `the standoff around a zone is priced, so a zone stays crossable`() {
        val nearBoundary = RouteHarness.fakeContext(
            zoneQueryFn = { _, _ -> SpeedZoneQuery(distanceToBoundaryM = 10.0, insideAnyZone = false) },
        )
        val cost = RouteCostModel(config(), nearBoundary)
        val a = LatLng(43.5100, 7.0050)
        val b = LatLng(43.5100, 7.0060)
        val free = RouteCostModel(config(), RouteHarness.fakeContext()).legTimeS(a, b)
        // 1 + 2·(1 − 10/25) = 2.2 in the priced band.
        assertEquals(2.2, cost.legTimeS(a, b) / free, 1e-3)
    }

    @Test
    fun `an impassable sample makes a leg unclear whatever the price would say`() {
        val wall = RouteHarness.fakeContext(forbidden = { _, lon -> lon > 7.010 })
        val cost = RouteCostModel(config(), wall)
        assertFalse(cost.isClear(LatLng(43.5100, 7.0050), LatLng(43.5100, 7.0150)))
        assertTrue(cost.isClear(LatLng(43.5100, 7.0050), LatLng(43.5100, 7.0060)))
    }

    @Test
    fun `zone distance is integrated along the leg`() {
        val cost = RouteCostModel(config(), RouteHarness.fakeContext(zoneQueryFn = ::inZone))
        val from = LatLng(43.5100, 7.0105)
        val to = LatLng(43.5100, 7.0155)
        val legLength = ykws.android.maro.spatial.SpatialOperations.haversine(from, to)
        val total = cost.legZoneDistanceM(from, to)["Test 5 kn"] ?: 0.0
        assertEquals("the whole leg is inside the zone: $total of $legLength", legLength, total, 1.0)
    }

    @Test
    fun `the zone aversion is a knob, not a hidden default`() {
        val plain = RouteCostModel(config(), RouteHarness.fakeContext(zoneQueryFn = ::inZone))
        val averted = RouteCostModel(
            config().copy(zoneAversionK = 1.0),
            RouteHarness.fakeContext(zoneQueryFn = ::inZone),
        )
        val a = LatLng(43.5100, 7.0150)
        val b = LatLng(43.5100, 7.0160)
        assertEquals(
            "k_zone = 1 doubles the time a zone costs, which is B's quantity",
            2.0,
            averted.legTimeS(a, b) / plain.legTimeS(a, b),
            1e-9,
        )
    }
}
