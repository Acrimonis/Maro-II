package ykws.android.maro.data.track

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ykws.android.maro.data.model.LatLng

/**
 * The recorder's side of the forgotten-start rule ([AutoStartDetector] owns the rule itself): what it
 * feeds the detector, what a start does to the trip and the arrival prompt, how the last stop is kept,
 * and the safety net for a false start. The monotonic clock is driven by hand.
 */
class TrackRecorderAutoStartTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val homeLat = 43.5696
    private val homeLon = 7.1313
    private val metresPerDegree = 6_371_000.0 * Math.PI / 180.0

    private var nowMs = 1_000_000L

    private val repository by lazy { TrackRepository(temp.root) }

    private fun recorder(
        geofenceEnabled: Boolean = true,
        water: Boolean? = true,
        orphansPending: suspend () -> Boolean = { false },
        lastStopWallMs: Long? = null,
        offMs: Long = 300_000L,
        stopped: MutableStateFlow<Boolean> = MutableStateFlow(false),
        onStopped: (Long) -> Unit = {}
    ) = TrackRecorder(
        repository = repository,
        config = TrackRecorderConfig(
            geofenceOriginLat = homeLat,
            geofenceOriginLon = homeLon,
            geofenceRadiusM = 150.0,
            geofenceEnabled = geofenceEnabled,
            gpsMode = false,
            idleThresholdSec = 1,
            autoStartOffMs = offMs,
            autoStartRestMs = 300_000L
        ),
        isStopped = stopped,
        dispatcher = Dispatchers.Unconfined,
        monotonicNowMs = { nowMs },
        waterAt = { water },
        orphansPending = orphansPending,
        lastStopWallMs = lastStopWallMs,
        onStopped = onStopped
    )

    private fun sample(
        northM: Double,
        speed: Float = 3f,
        accuracy: Float? = 5f,
        demo: Boolean = false,
        wallMs: Long = nowMs
    ) = TrackSample(
        position = LatLng(homeLat + northM / metresPerDegree, homeLon),
        speedMps = speed,
        bearingDeg = null,
        hasLock = !demo,
        timestampEpochMs = wallMs,
        accuracyM = accuracy,
        isDemo = demo
    )

    private fun TrackRecorder.isOn() = uiState.value.state == TrackRecorderState.ON

    private fun flow() = MutableSharedFlow<TrackSample>(extraBufferCapacity = 16)

    /** Emit [s] at [atMs] on the hand-driven clock and let the collector take it. */
    private suspend fun MutableSharedFlow<TrackSample>.feed(atMs: Long, s: TrackSample) {
        nowMs = atMs
        tryEmit(s)
        delay(100)
    }

    /** A still fix at the berth, then two 10 m moves 5 s apart: the third completes the rule. */
    private suspend fun MutableSharedFlow<TrackSample>.departFromBerth(
        atMs: Long = nowMs,
        wallMs: (Long) -> Long = { it }
    ) {
        feed(atMs, sample(0.0, speed = 0f, wallMs = wallMs(atMs)))
        feed(atMs + 5_000, sample(10.0, wallMs = wallMs(atMs + 5_000)))
        feed(atMs + 10_000, sample(20.0, wallMs = wallMs(atMs + 10_000)))
    }

    private fun promptCount(events: List<TrackEvent>) = events.filterIsInstance<TrackEvent.HomePortArrivalPrompt>().size

    // ── The start ───────────────────────────────────────────────────────────

    @Test
    fun `a rested boat moving off the berth on water starts a recording that began at the home port`() = runBlocking {
        val samples = flow()
        val rec = recorder()
        val events = CopyOnWriteArrayList<TrackEvent>()
        val collectJob = launch { rec.events.collect { events.add(it) } }
        try {
            rec.start(samples)
            samples.feed(nowMs, sample(0.0, speed = 0f))
            samples.feed(nowMs + 5_000, sample(10.0))
            assertEquals("two fixes are not yet a move", false, rec.isOn())
            samples.feed(nowMs + 5_000, sample(20.0))
            assertEquals(true, rec.isOn())
            assertEquals(1, events.filterIsInstance<TrackEvent.Started>().size)

            val id = rec.uiState.value.currentTrackId!!
            rec.stop()
            delay(500)
            assertEquals(true, repository.load(id)!!.startedAtHomePort)
        } finally {
            collectJob.cancel()
            rec.dispose()
        }
    }

    @Test
    fun `a boat first seen moving starts nothing`() = runBlocking {
        val samples = flow()
        val rec = recorder()
        try {
            rec.start(samples)
            samples.feed(nowMs, sample(10.0))
            samples.feed(nowMs + 5_000, sample(20.0))
            samples.feed(nowMs + 5_000, sample(30.0))
            samples.feed(nowMs + 5_000, sample(40.0))
            assertEquals(false, rec.isOn())
        } finally {
            rec.dispose()
        }
    }

    @Test
    fun `with the geofence off nothing starts`() = runBlocking {
        val samples = flow()
        val rec = recorder(geofenceEnabled = false)
        try {
            rec.start(samples)
            samples.departFromBerth()
            assertEquals(false, rec.isOn())
        } finally {
            rec.dispose()
        }
    }

    @Test
    fun `the defaults start nothing - water is unknown and a recovery is not pending`() = runBlocking {
        val samples = flow()
        val rec = TrackRecorder(
            repository = repository,
            config = TrackRecorderConfig(
                geofenceOriginLat = homeLat, geofenceOriginLon = homeLon, geofenceRadiusM = 150.0, gpsMode = false
            ),
            dispatcher = Dispatchers.Unconfined,
            monotonicNowMs = { nowMs }
        )
        try {
            rec.start(samples)
            samples.departFromBerth()
            assertEquals(false, rec.isOn())
        } finally {
            rec.dispose()
        }
    }

    @Test
    fun `land or an unknown water answer starts nothing`() = runBlocking {
        for (water in listOf(false, null)) {
            val samples = flow()
            val rec = recorder(water = water)
            try {
                rec.start(samples)
                samples.departFromBerth()
                assertEquals("water=$water", false, rec.isOn())
            } finally {
                rec.dispose()
            }
        }
    }

    @Test
    fun `simulated demo samples start it the same way`() = runBlocking {
        val samples = flow()
        val rec = recorder()
        try {
            rec.start(samples)
            samples.feed(nowMs, sample(0.0, speed = 0f, demo = true))
            samples.feed(nowMs + 5_000, sample(10.0, demo = true))
            samples.feed(nowMs + 5_000, sample(20.0, demo = true))
            assertEquals(true, rec.isOn())
        } finally {
            rec.dispose()
        }
    }

    @Test
    fun `a sample's own wall timestamp is never read`() = runBlocking {
        val samples = flow()
        val rec = recorder()
        try {
            rec.start(samples)
            samples.departFromBerth(wallMs = { 42L })
            assertEquals(true, rec.isOn())
        } finally {
            rec.dispose()
        }
    }

    // ── The last stop ───────────────────────────────────────────────────────

    @Test
    fun `a stop is announced by a discard and by a save and not by a start`() = runBlocking {
        val samples = flow()
        val rec = recorder()
        val events = CopyOnWriteArrayList<TrackEvent>()
        val collectJob = launch { rec.events.collect { events.add(it) } }
        try {
            rec.start(samples)
            rec.startManual()
            delay(200)
            assertEquals(0, events.filterIsInstance<TrackEvent.Stopped>().size)
            rec.stop()
            delay(500)
            assertEquals(1, events.filterIsInstance<TrackEvent.Stopped>().size)
            rec.startManual()
            delay(200)
            rec.discard()
            delay(500)
            assertEquals(2, events.filterIsInstance<TrackEvent.Stopped>().size)
        } finally {
            collectJob.cancel()
            rec.dispose()
        }
    }

    @Test
    fun `every stop reports its wall time before the recorder reads OFF`() = runBlocking {
        val stops = CopyOnWriteArrayList<Long>()
        val samples = flow()
        val rec = recorder(onStopped = { stops.add(it) })
        try {
            rec.start(samples)
            rec.startManual()
            delay(200)
            assertEquals(0, stops.size)
            rec.stop()
            delay(500)
            assertEquals(1, stops.size)
            rec.startManual()
            delay(200)
            rec.discard()
            delay(500)
            assertEquals(2, stops.size)
        } finally {
            rec.dispose()
        }
    }

    @Test
    fun `a stop persisted by the service is read back by a re-created recorder`() = runBlocking {
        val oneMinuteAgo = System.currentTimeMillis() - 60_000L
        val tooSoon = recorder(lastStopWallMs = oneMinuteAgo)
        val samples = flow()
        try {
            tooSoon.start(samples)
            samples.departFromBerth()
            assertEquals(false, tooSoon.isOn())
        } finally {
            tooSoon.dispose()
        }

        val tenMinutesAgo = System.currentTimeMillis() - 600_000L
        val longEnough = recorder(lastStopWallMs = tenMinutesAgo)
        val later = flow()
        try {
            longEnough.start(later)
            later.departFromBerth()
            assertEquals(true, longEnough.isOn())
        } finally {
            longEnough.dispose()
        }
    }

    @Test
    fun `a stop in this process restarts the off clock`() = runBlocking {
        val samples = flow()
        val rec = recorder()
        try {
            rec.start(samples)
            rec.startManual()
            delay(200)
            rec.stop()
            delay(500)
            assertEquals(false, rec.isOn())
            // A minute after the stop: rested at the berth, then away — too soon.
            samples.departFromBerth(atMs = nowMs + 60_000)
            assertEquals(false, rec.isOn())
            // Six minutes after it: the same departure now starts.
            samples.feed(nowMs + 300_000, sample(30.0))
            samples.feed(nowMs + 5_000, sample(40.0))
            samples.feed(nowMs + 5_000, sample(50.0))
            assertEquals(true, rec.isOn())
        } finally {
            rec.dispose()
        }
    }

    // ── A recovery pending ──────────────────────────────────────────────────

    private fun checkpointedTrack() = Track(
        id = "t",
        name = "t",
        startTimeMs = 1_000L,
        trackPoints = listOf(0L, 10_000L).map { TrackPoint(lat = 43.5, lon = 7.0, speedMps = 2f, timeOffsetMs = it) }
    )

    @Test
    fun `a valid checkpoint suppresses the start`() = runBlocking {
        repository.saveCheckpoint(checkpointedTrack())
        val samples = flow()
        val rec = recorder(orphansPending = { repository.hasRecoverableCheckpoint() })
        try {
            rec.start(samples)
            samples.departFromBerth()
            assertEquals(false, rec.isOn())
        } finally {
            rec.dispose()
        }
    }

    @Test
    fun `a stale checkpoint does not suppress the start`() = runBlocking {
        val live = checkpointedTrack()
        repository.saveCheckpoint(live)
        // The finalize landed and the process died before the checkpoint's delete: the file covers it.
        repository.save(live.copy(endTimeMs = 11_000L, lastPointTimeMs = 11_000L))
        val samples = flow()
        val rec = recorder(orphansPending = { repository.hasRecoverableCheckpoint() })
        try {
            rec.start(samples)
            samples.departFromBerth()
            delay(400) // the checkpoint is decoded on the IO pool before the rule can answer
            assertEquals(true, rec.isOn())
        } finally {
            rec.dispose()
        }
    }

    // ── The arrival prompt ──────────────────────────────────────────────────

    @Test
    fun `a start inside the circle raises no prompt on a stop inside until the boat has left and returned`() = runBlocking {
        val stopped = MutableStateFlow(false)
        val samples = flow()
        val events = CopyOnWriteArrayList<TrackEvent>()
        val rec = recorder(offMs = 60_000L, stopped = stopped)
        val collectJob = launch { rec.events.collect { events.add(it) } }
        try {
            rec.start(samples)
            samples.departFromBerth()
            assertEquals(true, rec.isOn())

            // A fuel-dock stop seconds after leaving: no Save / Discard.
            stopped.value = true
            samples.feed(nowMs + 5_000, sample(30.0))
            delay(1_500)
            assertEquals(0, promptCount(events))

            // Out of the circle and back in: now the arrival asks.
            stopped.value = false
            samples.feed(nowMs + 5_000, sample(300.0))
            stopped.value = true
            samples.feed(nowMs + 5_000, sample(60.0))
            delay(1_500)
            assertEquals(1, promptCount(events))
        } finally {
            collectJob.cancel()
            rec.dispose()
        }
    }

    @Test
    fun `a start whose completing fix is already outside prompts on the return`() = runBlocking {
        val stopped = MutableStateFlow(false)
        val samples = flow()
        val events = CopyOnWriteArrayList<TrackEvent>()
        val rec = recorder(offMs = 60_000L, stopped = stopped)
        val collectJob = launch { rec.events.collect { events.add(it) } }
        try {
            rec.start(samples)
            samples.feed(nowMs, sample(125.0, speed = 0f))
            samples.feed(nowMs + 5_000, sample(142.0))
            // 10 m past the edge, inside its own 20 m accuracy: the latch holds and the window has inside fixes.
            samples.feed(nowMs + 5_000, sample(160.0, accuracy = 20f))
            assertEquals(true, rec.isOn())

            stopped.value = true
            samples.feed(nowMs + 5_000, sample(60.0))
            delay(1_500)
            assertEquals(1, promptCount(events))
        } finally {
            collectJob.cancel()
            rec.dispose()
        }
    }

    @Test
    fun `the safety net prompts once after idling inside a trip that never left`() = runBlocking {
        val stopped = MutableStateFlow(false)
        val samples = flow()
        val events = CopyOnWriteArrayList<TrackEvent>()
        val rec = recorder(offMs = 1_000L, stopped = stopped)
        val collectJob = launch { rec.events.collect { events.add(it) } }
        try {
            rec.start(samples)
            samples.departFromBerth()
            assertEquals(true, rec.isOn())

            stopped.value = true
            samples.feed(nowMs + 5_000, sample(30.0))
            delay(2_500)
            assertEquals(1, promptCount(events))
            delay(1_500)
            assertEquals("once", 1, promptCount(events))
        } finally {
            collectJob.cancel()
            rec.dispose()
        }
    }

    @Test
    fun `moving again withdraws the safety net`() = runBlocking {
        val stopped = MutableStateFlow(false)
        val samples = flow()
        val events = CopyOnWriteArrayList<TrackEvent>()
        val rec = recorder(offMs = 1_500L, stopped = stopped)
        val collectJob = launch { rec.events.collect { events.add(it) } }
        try {
            rec.start(samples)
            samples.departFromBerth()

            stopped.value = true
            samples.feed(nowMs + 5_000, sample(30.0))
            stopped.value = false
            samples.feed(nowMs + 5_000, sample(40.0))
            delay(2_500)
            assertEquals(0, promptCount(events))
        } finally {
            collectJob.cancel()
            rec.dispose()
        }
    }

    @Test
    fun `a manual start keeps the arrival prompt armed`() = runBlocking {
        val stopped = MutableStateFlow(false)
        val samples = flow()
        val events = CopyOnWriteArrayList<TrackEvent>()
        val rec = recorder(offMs = 60_000L, stopped = stopped)
        val collectJob = launch { rec.events.collect { events.add(it) } }
        try {
            rec.start(samples)
            rec.startManual()
            delay(200)
            assertNotNull(rec.uiState.value.currentTrackId)
            stopped.value = true
            samples.feed(nowMs + 5_000, sample(30.0))
            delay(1_500)
            assertEquals(1, promptCount(events))
        } finally {
            collectJob.cancel()
            rec.dispose()
        }
    }
}
