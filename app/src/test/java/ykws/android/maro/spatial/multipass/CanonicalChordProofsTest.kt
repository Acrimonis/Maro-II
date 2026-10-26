package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.abs

/**
 * **A chord is one reading from either end.** The pull's lattices are anchored at the chord's first point,
 * so `a → b` and `b → a` once stood their marks on different points and could answer a hair apart; every
 * proof now turns the pair to its canonical direction first, so the sum is the same double bit for bit,
 * the verdict is the same, and the reversed read is answered from the memo the first one filled.
 */
class CanonicalChordProofsTest {

    private val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    private val wallLat = 43.4990
    private val priceLon = 7.0050

    private val field = RouteCostField(
        listOf(
            RouteCostSource.Hard(distanceAt = { p -> abs(p.latitude - wallLat) * mPerDegLat }),
            RouteCostSource.Soft(
                priceSec = { p -> if (p.longitude > priceLon) 30.0 else 0.0 },
                tag = MultipassCellState.ZONE,
                clearanceAt = { p -> abs(p.longitude - priceLon) * mPerDegLat * 0.7 }
            )
        ),
        cellM = 100.0
    )

    private fun setup() = PullSetup(
        marginM = 25.0, coarseStepM = 100.0, priceStepM = 100.0, field = field,
        start = LatLng(43.6, 7.3), aim = LatLng(43.7, 7.4), paceMps = 6.0
    )

    @Test
    fun thePriceAndTheTotalAreTheSameDoubleFromEitherEnd() {
        val a = LatLng(43.5000, 7.0000)
        val b = LatLng(43.5007, 7.0113)
        val ctx = setup().context()
        assertEquals(
            MultipassPull.softPriceSec(a, b, ctx).toBits(), MultipassPull.softPriceSec(b, a, ctx).toBits()
        )
        assertEquals(MultipassPull.totalSec(a, b, ctx).toBits(), MultipassPull.totalSec(b, a, ctx).toBits())
        assertTrue("the fixture prices the chord's eastern half", MultipassPull.softPriceSec(a, b, ctx) > 0.0)
    }

    @Test
    fun theClearanceVerdictIsTheSameFromEitherEnd() {
        val ctx = setup().context()
        val clearA = LatLng(43.5000, 7.0000)
        val clearB = LatLng(43.5002, 7.0120)
        assertNull(MultipassPull.legClearCause(clearA, clearB, ctx))
        assertNull(MultipassPull.legClearCause(clearB, clearA, ctx))
        // A chord dipping within the margin of the wall is refused, read from either end.
        val nearA = LatLng(43.49915, 7.0000)
        val nearB = LatLng(43.4998, 7.0120)
        assertEquals(ChordRefusal.LAND, MultipassPull.legClearCause(nearA, nearB, ctx))
        assertEquals(ChordRefusal.LAND, MultipassPull.legClearCause(nearB, nearA, ctx))
    }

    @Test
    fun theReversedReadIsAnsweredFromTheMemoTheFirstOneFilled() {
        val a = LatLng(43.5000, 7.0000)
        val b = LatLng(43.5007, 7.0113)
        val timing = PullTiming()
        val memo = MarkMemo()
        val ctx = setup().context(timing = timing, memo = memo)
        val forward = MultipassPull.softPriceSec(a, b, ctx)
        val paidForward = timing.priceReads
        assertTrue("the first read pays the field", paidForward > 0)
        val backward = MultipassPull.softPriceSec(b, a, ctx)
        assertEquals("the reversed read pays nothing new", paidForward, timing.priceReads)
        assertEquals(forward.toBits(), backward.toBits())
    }
}
