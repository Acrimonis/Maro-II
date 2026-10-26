package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/** Snapping trades travel time against field penalties while keeping clearance absolute. */
class CornerSnapCostTest {
    private val start = point(0.0, 0.0)
    private val aim = point(1000.0, 0.0)
    private val straight = point(500.0, 0.0)
    private val detour = point(500.0, 200.0)

    @Test
    fun aFreeDetourIsRejectedWhenItAddsTravelTime() {
        val setup = setup(RouteCostField.ofHard { Double.MAX_VALUE })
        assertEquals(listOf(start, straight, aim), snap(setup, straight, detour))
    }

    @Test
    fun aShorterSnapCanPayMorePenaltyAndStillWin() {
        val setup = setup(pricedStrip(0.001))
        assertTrue(softCost(setup, straight) > softCost(setup, detour))
        assertTrue(totalCost(setup, straight) < totalCost(setup, detour))
        assertEquals(listOf(start, straight, aim), snap(setup, detour, straight))
    }

    @Test
    fun aSmallPenaltySavingCannotBuyAnExpensiveDetour() {
        val setup = setup(pricedStrip(0.001))
        assertTrue(softCost(setup, detour) < softCost(setup, straight))
        assertTrue(totalCost(setup, detour) > totalCost(setup, straight))
        assertEquals(listOf(start, straight, aim), snap(setup, straight, detour))
    }

    @Test
    fun aLargePenaltySavingCanBuyAWorthwhileDetour() {
        val setup = setup(pricedStrip(0.1))
        assertTrue(totalCost(setup, detour) < totalCost(setup, straight))
        assertEquals(listOf(start, detour, aim), snap(setup, straight, detour))
    }

    @Test
    fun aCheaperSnapCannotCrossTheClearanceWall() {
        val setup = setup(RouteCostField.ofHard { SpatialOperations.haversine(it, straight) })
        assertTrue(MultipassPull.legClear(start, detour, setup.context()))
        assertFalse(MultipassPull.legClear(start, straight, setup.context()))
        assertTrue(totalCost(setup, straight) < totalCost(setup, detour))
        assertEquals(listOf(start, detour, aim), snap(setup, detour, straight))
    }

    private fun setup(field: RouteCostField) = PullSetup(
        marginM = 25.0, coarseStepM = 20.0, priceStepM = 20.0,
        field = field, start = start, aim = aim, paceMps = 10.0
    )

    private fun snap(setup: PullSetup, bend: LatLng, corner: LatLng) = snapToCorners(
        setup, listOf(CornerSet(listOf(corner)) { 250.0 }), listOf(start, bend, aim)
    )

    private fun softCost(setup: PullSetup, bend: LatLng): Double {
        val ctx = setup.context()
        return MultipassPull.softPriceSec(start, bend, ctx) + MultipassPull.softPriceSec(bend, aim, ctx)
    }

    private fun totalCost(setup: PullSetup, bend: LatLng): Double {
        val ctx = setup.context()
        return MultipassPull.totalSec(start, bend, ctx) + MultipassPull.totalSec(bend, aim, ctx)
    }

    private fun pricedStrip(rateSecPerM: Double) = RouteCostField(
        listOf(
            RouteCostSource.Hard(distanceAt = { Double.MAX_VALUE }),
            RouteCostSource.Soft(
                priceSec = { if (abs((it.latitude - latitude) * metresPerDegree) < 10.0) rateSecPerM else 0.0 },
                tag = MultipassCellState.ZONE
            )
        ),
        cellM = 1.0
    )

    private companion object {
        const val latitude = 43.5
        val metresPerDegree = SpatialOperations.EARTH_RADIUS_M * PI / 180.0

        fun point(eastM: Double, northM: Double) = LatLng(
            latitude + northM / metresPerDegree,
            7.0 + eastM / (metresPerDegree * cos(Math.toRadians(latitude)))
        )
    }
}
