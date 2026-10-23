package ykws.android.maro.data.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ykws.android.maro.data.route.SpeedHistory.Trip

class SpeedHistoryTest {

    private val cap = 30.0

    private fun trip(kn: Double, min: Double = 30.0, start: Long = 0L, demo: Boolean = false) =
        Trip(kn, min, start, demo)

    @Test
    fun emptyStoreHasNoMedian() {
        assertNull(SpeedHistory.weightedMedian(emptyList(), cap))
        assertNull(SpeedHistory.profile(emptyList(), 5, includeDemo = true, capMin = cap))
    }

    @Test
    fun singleTripIsItsOwnMedian() {
        assertEquals(21.0, SpeedHistory.weightedMedian(listOf(trip(21.0)), cap)!!, 1e-9)
    }

    @Test
    fun aShortTripVotesInProportionToItsOpenWater() {
        // 15 open-water minutes against 30: the long trip carries two thirds of the vote.
        val trips = listOf(trip(20.0, min = 15.0), trip(30.0, min = 30.0))
        assertEquals(30.0, SpeedHistory.weightedMedian(trips, cap)!!, 1e-9)
    }

    @Test
    fun aLongTripVotesNoMoreThanTheCap() {
        val trips = listOf(trip(20.0, min = 100.0), trip(30.0, min = 30.0), trip(30.0, min = 30.0))
        assertEquals(30.0, SpeedHistory.weightedMedian(trips, cap)!!, 1e-9)
    }

    @Test
    fun tripsWithNoMinutesVoteEqually() {
        val trips = listOf(trip(20.0, min = 0.0), trip(25.0, min = 0.0), trip(40.0, min = 0.0))
        assertEquals(25.0, SpeedHistory.weightedMedian(trips, cap)!!, 1e-9)
    }

    @Test
    fun theProfileReadsTheLastNByStartTimeAndNothingIsThrownAway() {
        val store = (1..6).map { trip(10.0 * it, start = it.toLong()) }
        assertEquals(50.0, SpeedHistory.profile(store, 3, includeDemo = false, capMin = cap)!!, 1e-9)
        // Raising N brings the older trips back.
        assertEquals(30.0, SpeedHistory.profile(store, 6, includeDemo = false, capMin = cap)!!, 1e-9)
    }

    @Test
    fun aThinHistoryIsReadAsItStands() {
        val store = listOf(trip(18.0, start = 1), trip(22.0, start = 2))
        assertEquals(18.0, SpeedHistory.profile(store, 5, includeDemo = false, capMin = cap)!!, 1e-9)
    }

    @Test
    fun theDemoSwitchMovesTheProfileFromTheSameStore() {
        val store = listOf(
            trip(20.0, start = 1), trip(20.0, start = 2),
            trip(40.0, start = 3, demo = true), trip(40.0, start = 4, demo = true)
        )
        assertEquals(20.0, SpeedHistory.profile(store, 4, includeDemo = false, capMin = cap)!!, 1e-9)
        assertEquals(20.0, SpeedHistory.profile(store, 4, includeDemo = true, capMin = cap)!!, 1e-9)
        // Of the last two trips by start time, both demo.
        assertEquals(40.0, SpeedHistory.profile(store, 2, includeDemo = true, capMin = cap)!!, 1e-9)
        // With demo off the last two are GPS trips whatever came after them.
        assertEquals(20.0, SpeedHistory.profile(store, 2, includeDemo = false, capMin = cap)!!, 1e-9)
    }

    @Test
    fun foldKeepsFifteenOfEachKindDroppingTheOldest() {
        var store = emptyList<Trip>()
        for (i in 1..17) store = SpeedHistory.fold(store, trip(i.toDouble(), start = i.toLong()))
        for (i in 1..3) store = SpeedHistory.fold(store, trip(100.0 + i, start = 100L + i, demo = true))
        assertEquals(15, store.count { !it.demo })
        assertEquals(3, store.count { it.demo })
        // The two oldest GPS trips fell off, not the newest.
        assertEquals(3.0, store.first { !it.demo }.speedKn, 0.0)
        assertEquals(17.0, store.last { !it.demo }.speedKn, 0.0)
    }

    @Test
    fun foldReplacesATripOfTheSameStartAndKind() {
        val once = SpeedHistory.fold(listOf(trip(20.0, start = 5)), trip(22.0, start = 5))
        assertEquals(listOf(trip(22.0, start = 5)), once)
        val otherKind = SpeedHistory.fold(listOf(trip(20.0, start = 5)), trip(22.0, start = 5, demo = true))
        assertEquals(2, otherKind.size)
    }

    @Test
    fun aSeedFedNewestFirstIsStoredOldestFirst() {
        val newestFirst = (6 downTo 1).map { trip(10.0 * it, start = it.toLong()) }
        val store = SpeedHistory.seed(newestFirst)
        assertEquals((1..6).map { 10.0 * it }, store.map { it.speedKn })
        // The later fold drops the oldest, not the newest.
        val grown = (7..16).fold(store) { s, i -> SpeedHistory.fold(s, trip(10.0 * i, start = i.toLong())) }
        assertEquals(15, grown.size)
        assertEquals(20.0, grown.first().speedKn, 0.0)
        assertEquals(160.0, grown.last().speedKn, 0.0)
    }

    @Test
    fun aSeedKeepsFifteenOfEachKind() {
        val trips = (1..20).map { trip(it.toDouble(), start = it.toLong()) } +
            (1..20).map { trip(it.toDouble(), start = 100L + it, demo = true) }
        val store = SpeedHistory.seed(trips)
        assertEquals(15, store.count { !it.demo })
        assertEquals(15, store.count { it.demo })
    }

    @Test
    fun theStoreSurvivesItsPreferenceString() {
        val store = listOf(trip(24.5, min = 12.25, start = 1_700_000_000_000L), trip(31.0, start = 5, demo = true))
        assertEquals(store, SpeedHistory.decode(SpeedHistory.encode(store)))
        assertEquals(emptyList<Trip>(), SpeedHistory.decode(""))
        assertEquals(listOf(trip(20.0, start = 2)), SpeedHistory.decode("garbage|20.0:30.0:2:0|1:2"))
    }
}
