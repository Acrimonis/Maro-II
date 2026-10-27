package ykws.android.maro.ui.map

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
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
    @Test fun meaningfulFlowTransitionsRemainImmediateWithoutRecomposition() = runTest {
        val keys=MutableStateFlow(NotificationTickerKey(true,true,false,true))
        var latestStat=0
        val sent=mutableListOf<Triple<Long,NotificationTickerKey,Int>>()
        val job=launch { notificationUpdates(keys) { sent+=Triple(testScheduler.currentTime,it,latestStat) } }
        runCurrent()
        advanceTimeBy(1_000); latestStat=1
        keys.value=keys.value.copy() // A statistics update does not restart the ticker.
        runCurrent(); assertEquals(1,sent.size)
        advanceTimeBy(4_000); latestStat=5; runCurrent()
        assertEquals(5,sent.last().third)
        keys.value=keys.value.copy(onWater=true); runCurrent()
        assertEquals(true,sent.last().second.onWater)
        keys.value=keys.value.copy(moving=false); runCurrent()
        assertEquals(false,sent.last().second.moving)
        keys.value=keys.value.copy(recording=false); runCurrent()
        assertEquals(false,sent.last().second.recording)
        val stoppedCount=sent.size
        advanceTimeBy(30_000); runCurrent()
        assertEquals(stoppedCount,sent.size)
        job.cancelAndJoin()
    }

}
