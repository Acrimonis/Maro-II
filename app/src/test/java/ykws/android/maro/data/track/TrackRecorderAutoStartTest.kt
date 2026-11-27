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
 * The recorder's side of the home-port exit rule ([AutoStartDetector] owns the rule itself): what it feeds
 * the detector, what a start does to the trip and the arrival prompt, and that a stop sets no wait.
 */
class TrackRecorderAutoStartTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val homeLat = 43.5696
    private val homeLon = 7.1313
    private val metresPerDegree = 6_371_000.0 * Math.PI / 180.0

    /** The recorder's monotonic clock, driven by hand: each fed fix lands 20 s after the previous one. */
    private var nowMs = 1_000_000L

    private val repository by lazy { TrackRepository(temp.root) }

    private fun recorder(
        geofenceEnabled: Boolean = true,
        water: Boolean? = true,
        orphansPending: suspend () -> Boolean = { false },
        stopped: MutableStateFlow<Boolean> = MutableStateFlow(false)
    ) = TrackRecorder(
        repository = repository,
        config = TrackRecorderConfig(
            geofenceOriginLat = homeLat,
            geofenceOriginLon = homeLon,
            geofenceRadiusM = 150.0,
            geofenceEnabled = geofenceEnabled,
            gpsMode = false,
            idleThresholdSec = 1
        ),
        isStopped = stopped,
        dispatcher = Dispatchers.Unconfined,
        monotonicNowMs = { nowMs },
        waterAt = { water },
        orphansPending = orphansPending
    )

    private fun sample(northM: Double, speed: Float = 3f, demo: Boolean = false) = TrackSample(
        position = LatLng(homeLat + northM / metresPerDegree, homeLon),
        speedMps = speed,
        bearingDeg = null,
        hasLock = !demo,
        timestampEpochMs = System.currentTimeMillis(),
        accuracyM = if (demo) null else 5f,
        isDemo = demo
    )

    private fun TrackRecorder.isOn() = uiState.value.state == TrackRecorderState.ON

    private fun flow() = MutableSharedFlow<TrackSample>(extraBufferCapacity = 16)

    /** Emit [s] 20 s after the previous fix and let the collector take it. */
    private suspend fun MutableSharedFlow<TrackSample>.feed(s: TrackSample) {
        nowMs += 20_000L
        tryEmit(s)
        delay(100)
    }

    /** A fix at the berth, one in the harbour, one 50 m past the edge: the third completes the rule. */
    private suspend fun MutableSharedFlow<TrackSample>.leavePort(demo: Boolean = false) {
        feed(sample(0.0, demo = demo))
        feed(sample(100.0, demo = demo))
        feed(sample(200.0, demo = demo))
    }

    private fun promptCount(events: List<TrackEvent>) = events.filterIsInstance<TrackEvent.HomePortArrivalPrompt>().size

    // ── The start ───────────────────────────────────────────────────────────

    @Test
    fun `leaving the circle on water starts a recording that began at the home port`() = runBlocking {
        val samples = flow()
        val rec = recorder()
        val events = CopyOnWriteArrayList<TrackEvent>()
        val collectJob = launch { rec.events.collect { events.add(it) } }
        try {
            rec.start(samples)
            samples.feed(sample(0.0))
            samples.feed(sample(100.0))
            assertEquals("moving inside the harbour starts nothing", false, rec.isOn())
            samples.feed(sample(200.0))
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
    fun `a GPS spike out of the berth starts nothing - the real departure does`() = runBlocking {
        val samples = flow()
        val rec = recorder()
        try {
            rec.start(samples)
            samples.feed(sample(0.0, speed = 0f))
            samples.feed(sample(250.0, speed = 0f))
            assertEquals("no Doppler speed: a spike", false, rec.isOn())
            samples.feed(sample(0.0, speed = 0f))
            samples.leavePort()
            assertEquals(true, rec.isOn())
        } finally {
            rec.dispose()
        }
    }

    @Test
    fun `a boat first seen outside starts nothing`() = runBlocking {
        val samples = flow()
        val rec = recorder()
        try {
            rec.start(samples)
            samples.feed(sample(300.0))
            samples.feed(sample(400.0))
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
            samples.leavePort()
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
            samples.leavePort()
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
                samples.leavePort()
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
            samples.leavePort(demo = true)
            assertEquals(true, rec.isOn())
        } finally {
            rec.dispose()
        }
    }

    // ── After a stop ────────────────────────────────────────────────────────

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
    fun `a stop inside the circle sets no wait - leaving right after starts again`() = runBlocking {
        val samples = flow()
        val rec = recorder()
        try {
            rec.start(samples)
            samples.leavePort()
            assertEquals(true, rec.isOn())
            samples.feed(sample(50.0))
            rec.stop()
            delay(500)
            assertEquals(false, rec.isOn())

            samples.feed(sample(200.0))
            assertEquals(true, rec.isOn())
        } finally {
            rec.dispose()
        }
    }

    @Test
    fun `a trip stopped at sea does not restart on its own`() = runBlocking {
        val samples = flow()
        val rec = recorder()
        try {
            rec.start(samples)
            samples.leavePort()
            samples.feed(sample(400.0))
            rec.stop()
            delay(500)

            samples.feed(sample(500.0))
            samples.feed(sample(600.0))
            assertEquals(false, rec.isOn())
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
            samples.leavePort()
            delay(400)
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
            samples.leavePort()
            delay(400) // the checkpoint is decoded on the IO pool before the rule can answer
            assertEquals(true, rec.isOn())
        } finally {
            rec.dispose()
        }
    }

    // ── The arrival prompt ──────────────────────────────────────────────────

    @Test
    fun `an auto start prompts on the return`() = runBlocking {
        val stopped = MutableStateFlow(false)
        val samples = flow()
        val events = CopyOnWriteArrayList<TrackEvent>()
        val rec = recorder(stopped = stopped)
        val collectJob = launch { rec.events.collect { events.add(it) } }
        try {
            rec.start(samples)
            samples.leavePort()
            assertEquals(true, rec.isOn())

            stopped.value = true
            samples.feed(sample(60.0))
            delay(1_500)
            assertEquals(1, promptCount(events))
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
        val rec = recorder(stopped = stopped)
        val collectJob = launch { rec.events.collect { events.add(it) } }
        try {
            rec.start(samples)
            rec.startManual()
            delay(200)
            assertNotNull(rec.uiState.value.currentTrackId)
            stopped.value = true
            samples.feed(sample(30.0))
            delay(1_500)
            assertEquals(1, promptCount(events))
        } finally {
            collectJob.cancel()
            rec.dispose()
        }
    }
}
