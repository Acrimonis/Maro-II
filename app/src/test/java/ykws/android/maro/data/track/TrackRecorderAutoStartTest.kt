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

    private fun config(
        geofenceEnabled: Boolean = true,
        leave: Boolean = true,
        autoSave: Boolean = false,
        onWater: Boolean = false,
        holdMs: Long = 300L
    ) = TrackRecorderConfig(
        geofenceOriginLat = homeLat,
        geofenceOriginLon = homeLon,
        geofenceRadiusM = 150.0,
        geofenceEnabled = geofenceEnabled,
        gpsMode = false,
        idleThresholdSec = 1,
        autoStartLeave = leave,
        autoSaveReturn = autoSave,
        autoStartWater = onWater,
        arrivalRadiusM = 50.0,
        arrivalHoldMs = holdMs,
        waterStartDistanceM = 50.0
    )

    private fun recorder(
        geofenceEnabled: Boolean = true,
        water: Boolean? = true,
        stopped: MutableStateFlow<Boolean> = MutableStateFlow(false),
        leave: Boolean = true,
        autoSave: Boolean = false,
        onWater: Boolean = false,
        holdMs: Long = 300L
    ) = TrackRecorder(
        repository = repository,
        config = config(geofenceEnabled, leave, autoSave, onWater, holdMs),
        isStopped = stopped,
        dispatcher = Dispatchers.Unconfined,
        monotonicNowMs = { nowMs },
        waterAt = { water }
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
    fun `a pending recovery is saved as a track and the start goes on`() = runBlocking {
        repository.saveCheckpoint(checkpointedTrack())
        val samples = flow()
        val rec = recorder()
        val events = CopyOnWriteArrayList<TrackEvent>()
        val collectJob = launch { rec.events.collect { events.add(it) } }
        try {
            rec.start(samples)
            samples.leavePort()
            delay(400) // the checkpoint is decoded and saved on the IO pool before the start
            assertEquals(true, rec.isOn())
            assertNotNull("the interrupted trip is a finished track now", repository.load("t")?.endTimeMs)
            assertEquals(listOf("t"), events.filterIsInstance<TrackEvent.RecoveriesSaved>().single().trackIds)
        } finally {
            collectJob.cancel()
            rec.dispose()
        }
    }

    @Test
    fun `a stale checkpoint is not saved again and the start goes on`() = runBlocking {
        val live = checkpointedTrack()
        repository.saveCheckpoint(live)
        // The finalize landed and the process died before the checkpoint's delete: the file covers it.
        repository.save(live.copy(endTimeMs = 11_000L, lastPointTimeMs = 11_000L))
        val samples = flow()
        val rec = recorder()
        val events = CopyOnWriteArrayList<TrackEvent>()
        val collectJob = launch { rec.events.collect { events.add(it) } }
        try {
            rec.start(samples)
            samples.leavePort()
            delay(400)
            assertEquals(true, rec.isOn())
            assertEquals(0, events.filterIsInstance<TrackEvent.RecoveriesSaved>().size)
            assertEquals(11_000L, repository.load("t")?.endTimeMs)
        } finally {
            collectJob.cancel()
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

    // ── The auto-save on return ─────────────────────────────────────────────

    /** Out to 400 m and back into the 50 m arrival circle, 200 m at most per 20 s fix. */
    private suspend fun MutableSharedFlow<TrackSample>.sailOutAndBack() {
        feed(sample(400.0))
        feed(sample(200.0))
        feed(sample(100.0))
        feed(sample(30.0))
    }

    @Test
    fun `back from sea the trip saves itself after the hold and the prompt stands aside`() = runBlocking {
        val stopped = MutableStateFlow(false)
        val samples = flow()
        val events = CopyOnWriteArrayList<TrackEvent>()
        val rec = recorder(stopped = stopped, autoSave = true, holdMs = 1_500L)
        val collectJob = launch { rec.events.collect { events.add(it) } }
        try {
            rec.start(samples)
            samples.leavePort()
            assertEquals(true, rec.isOn())
            samples.feed(sample(400.0))
            samples.feed(sample(200.0))
            samples.feed(sample(100.0))
            // Idle at the berth: the prompt's own timer (1 s) runs out before the hold (1.5 s).
            stopped.value = true
            samples.feed(sample(30.0))
            delay(2_500)
            assertEquals(false, rec.isOn())
            assertEquals(0, promptCount(events))
            assertEquals(true, events.filterIsInstance<TrackEvent.Finalized>().single().auto)
        } finally {
            collectJob.cancel()
            rec.dispose()
        }
    }

    @Test
    fun `a fix out of the arrival circle cancels the hold`() = runBlocking {
        val samples = flow()
        val rec = recorder(autoSave = true, holdMs = 600L)
        try {
            rec.start(samples)
            samples.leavePort()
            samples.sailOutAndBack()
            samples.feed(sample(90.0))
            delay(1_000)
            assertEquals(true, rec.isOn())
        } finally {
            rec.dispose()
        }
    }

    @Test
    fun `with the auto-save off a return keeps recording`() = runBlocking {
        val samples = flow()
        val rec = recorder(autoSave = false)
        try {
            rec.start(samples)
            samples.leavePort()
            samples.sailOutAndBack()
            delay(800)
            assertEquals(true, rec.isOn())
        } finally {
            rec.dispose()
        }
    }

    @Test
    fun `a trip that never left the home circle is not auto-saved`() = runBlocking {
        val samples = flow()
        val rec = recorder(autoSave = true)
        try {
            rec.start(samples)
            rec.startManual()
            delay(200)
            samples.feed(sample(100.0))
            samples.feed(sample(30.0))
            delay(800)
            assertEquals(true, rec.isOn())
        } finally {
            rec.dispose()
        }
    }

    @Test
    fun `Continue at the berth is not saved again until the trip leaves and returns`() = runBlocking {
        val samples = flow()
        val rec = recorder(autoSave = true)
        try {
            rec.start(samples)
            samples.leavePort()
            samples.sailOutAndBack()
            delay(800)
            assertEquals(false, rec.isOn())

            // Continue: the saved trip reopens as the live recording.
            val saved = repository.listTracks().single { !it.route }
            rec.resume(repository.load(saved.id)!!, samples, fromCheckpoint = false)
            delay(300)
            assertEquals(true, rec.isOn())
            samples.feed(sample(20.0))
            delay(800)
            assertEquals("still at the berth: no second save", true, rec.isOn())

            samples.sailOutAndBack()
            delay(800)
            assertEquals(false, rec.isOn())
        } finally {
            rec.dispose()
        }
    }

    @Test
    fun `the auto-save switched off during the hold lets the arrival prompt ask`() = runBlocking {
        val stopped = MutableStateFlow(false)
        val samples = flow()
        val events = CopyOnWriteArrayList<TrackEvent>()
        val rec = recorder(stopped = stopped, autoSave = true, holdMs = 400L)
        val collectJob = launch { rec.events.collect { events.add(it) } }
        try {
            rec.start(samples)
            samples.leavePort()
            samples.feed(sample(400.0))
            samples.feed(sample(200.0))
            samples.feed(sample(100.0))
            samples.feed(sample(30.0))
            rec.applyConfig(config(autoSave = false, holdMs = 400L))
            delay(600)
            assertEquals("no save: the toggle is off", true, rec.isOn())
            // Idle at the berth: the prompt asks now that the hold has ended.
            stopped.value = true
            samples.feed(sample(31.0))
            delay(1_500)
            assertEquals(1, promptCount(events))
        } finally {
            collectJob.cancel()
            rec.dispose()
        }
    }

    @Test
    fun `Continue at the berth raises no arrival prompt until the trip leaves`() = runBlocking {
        val stopped = MutableStateFlow(false)
        val samples = flow()
        val events = CopyOnWriteArrayList<TrackEvent>()
        val rec = recorder(stopped = stopped, autoSave = true)
        val collectJob = launch { rec.events.collect { events.add(it) } }
        try {
            rec.start(samples)
            samples.leavePort()
            samples.sailOutAndBack()
            delay(800)
            val saved = repository.listTracks().single { !it.route }
            rec.resume(repository.load(saved.id)!!, samples, fromCheckpoint = false)
            delay(300)
            stopped.value = true
            samples.feed(sample(21.0))
            delay(1_500)
            assertEquals(0, promptCount(events))
        } finally {
            collectJob.cancel()
            rec.dispose()
        }
    }

    // ── The start on water ──────────────────────────────────────────────────

    @Test
    fun `away from the port a move on water starts a recording not from the home port`() = runBlocking {
        val samples = flow()
        val rec = recorder(leave = false, onWater = true)
        try {
            rec.start(samples)
            samples.feed(sample(2_000.0))
            samples.feed(sample(2_030.0))
            assertEquals(false, rec.isOn())
            samples.feed(sample(2_100.0))
            assertEquals(true, rec.isOn())

            val id = rec.uiState.value.currentTrackId!!
            rec.stop()
            delay(500)
            assertEquals(false, repository.load(id)!!.startedAtHomePort)
        } finally {
            rec.dispose()
        }
    }

    @Test
    fun `with the water start on a trip stopped at sea still does not restart while moving`() = runBlocking {
        val samples = flow()
        val rec = recorder(onWater = true)
        try {
            rec.start(samples)
            samples.leavePort()
            samples.feed(sample(400.0))
            rec.stop()
            delay(500)

            samples.feed(sample(500.0))
            samples.feed(sample(600.0))
            samples.feed(sample(700.0))
            assertEquals(false, rec.isOn())
        } finally {
            rec.dispose()
        }
    }

    @Test
    fun `leaving the port starts once when both start rules are on`() = runBlocking {
        val samples = flow()
        val rec = recorder(onWater = true)
        val events = CopyOnWriteArrayList<TrackEvent>()
        val collectJob = launch { rec.events.collect { events.add(it) } }
        try {
            rec.start(samples)
            samples.leavePort()
            samples.feed(sample(300.0))
            assertEquals(1, events.filterIsInstance<TrackEvent.Started>().size)
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
