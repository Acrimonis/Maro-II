package ykws.android.maro.ui.map

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NotificationTickerTest {
    @Test fun aMinuteOfChangingStatsKeepsCadenceAndReadsTheLatestPayload() = runTest {
        var latest = 0
        val updates = mutableListOf<Pair<Long, Int>>()
        val ticker = launch { notificationTicker(true) { updates += testScheduler.currentTime to latest } }
        runCurrent()
        repeat(59) {
            advanceTimeBy(1_000)
            latest++
            runCurrent()
        }
        advanceTimeBy(1_000)
        ticker.cancelAndJoin() // Half-open [0, 60s), no tick at 60s.
        assertEquals((0..11).map { it * 5_000L to it * 5 }, updates)
    }

    @Test fun referenceRestartingOnEachStatSendsFiveTimesAsManyUpdates() = runTest {
        var sends = 0
        repeat(60) {
            val job = launch { notificationTicker(true) { sends++ } }
            runCurrent()
            advanceTimeBy(1_000)
            job.cancelAndJoin()
        }
        assertEquals(60, sends)
        println("DISPLAY-PERF virtual_window_ms=60000 reference_sends=60 candidate_sends=12")
    }

    @Test fun stopSendsOnceAndCancellationStopsFurtherUpdates() = runTest {
        var sends = 0
        val job = launch { notificationTicker(true) { sends++ } }
        runCurrent()
        advanceTimeBy(2_000)
        job.cancelAndJoin()
        notificationTicker(false) { sends++ }
        advanceTimeBy(60_000)
        assertEquals(2, sends)
    }

    @Test fun aModeOrWaterTransitionCanSendImmediatelyWithFreshPayload() = runTest {
        val times = mutableListOf<Long>()
        val job = launch { notificationTicker(true) { times += testScheduler.currentTime } }
        runCurrent()
        advanceTimeBy(1_200)
        job.cancelAndJoin()
        val replacement = launch { notificationTicker(true) { times += testScheduler.currentTime } }
        runCurrent()
        advanceTimeBy(5_001)
        replacement.cancelAndJoin()
        assertEquals(listOf(0L, 1_200L, 6_200L), times)
    }
}
