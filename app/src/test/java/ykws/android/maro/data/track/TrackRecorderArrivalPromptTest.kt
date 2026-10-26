package ykws.android.maro.data.track

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ykws.android.maro.data.model.LatLng

/**
 * The recorder's half of the home-port arrival prompt (settled 2026-10-24): idling inside the
 * circle for the threshold fires the three-door prompt once, only a re-exit re-arms it, and an
 * idle outside the circle never fires it.
 */
class TrackRecorderArrivalPromptTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val homeLat = 43.5696
    private val homeLon = 7.1312

    private fun recorder(stopped: MutableStateFlow<Boolean>) = TrackRecorder(
        repository = TrackRepository(temp.root),
        config = TrackRecorderConfig(
            geofenceOriginLat = homeLat,
            geofenceOriginLon = homeLon,
            geofenceRadiusM = 50.0,
            geofenceEnabled = true,
            gpsMode = false,
            idleThresholdSec = 1
        ),
        isStopped = stopped,
        dispatcher = Dispatchers.Unconfined
    )

    private fun sample(lat: Double, lon: Double) = TrackSample(
        position = LatLng(lat, lon),
        speedMps = 3f,
        bearingDeg = null,
        hasLock = true,
        timestampEpochMs = System.currentTimeMillis()
    )

    private fun promptCount(events: List<TrackEvent>) =
        events.filterIsInstance<TrackEvent.HomePortArrivalPrompt>().size

    @Test
    fun `idle inside the circle fires the prompt once`() = runBlocking {
        val stopped = MutableStateFlow(false)
        val samples = MutableSharedFlow<TrackSample>(extraBufferCapacity = 16)
        val events = CopyOnWriteArrayList<TrackEvent>()
        val rec = recorder(stopped)
        val collectJob = launch { rec.events.collect { events.add(it) } }
        try {
            rec.start(samples)
            rec.startManual()
            stopped.value = true
            samples.tryEmit(sample(homeLat, homeLon))
            delay(2_000)
            assertEquals(1, promptCount(events))
        } finally {
            collectJob.cancel()
            rec.dispose()
        }
    }

    @Test
    fun `the prompt re-fires only after a re-exit`() = runBlocking {
        val stopped = MutableStateFlow(false)
        val samples = MutableSharedFlow<TrackSample>(extraBufferCapacity = 16)
        val events = CopyOnWriteArrayList<TrackEvent>()
        val rec = recorder(stopped)
        val collectJob = launch { rec.events.collect { events.add(it) } }
        try {
            rec.start(samples)
            rec.startManual()

            // First entry: idle inside asks once.
            stopped.value = true
            samples.tryEmit(sample(homeLat, homeLon))
            delay(2_000)
            assertEquals(1, promptCount(events))

            // Still idle inside: no second fire without a re-exit.
            samples.tryEmit(sample(homeLat + 0.0001, homeLon + 0.0001))
            delay(2_000)
            assertEquals(1, promptCount(events))

            // Re-exit re-arms, then a fresh entry asks again.
            stopped.value = false
            samples.tryEmit(sample(homeLat + 0.02, homeLon + 0.02))
            delay(300)
            stopped.value = true
            samples.tryEmit(sample(homeLat, homeLon))
            delay(2_000)
            assertEquals(2, promptCount(events))
        } finally {
            collectJob.cancel()
            rec.dispose()
        }
    }

    @Test
    fun `idle outside the circle never fires the prompt`() = runBlocking {
        val stopped = MutableStateFlow(false)
        val samples = MutableSharedFlow<TrackSample>(extraBufferCapacity = 16)
        val events = CopyOnWriteArrayList<TrackEvent>()
        val rec = recorder(stopped)
        val collectJob = launch { rec.events.collect { events.add(it) } }
        try {
            rec.start(samples)
            rec.startManual()
            stopped.value = true
            samples.tryEmit(sample(homeLat + 0.05, homeLon + 0.05))
            delay(2_000)
            assertTrue(events.none { it is TrackEvent.HomePortArrivalPrompt })
        } finally {
            collectJob.cancel()
            rec.dispose()
        }
    }
}
