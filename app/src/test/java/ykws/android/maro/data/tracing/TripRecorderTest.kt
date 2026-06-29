package ykws.android.maro.data.tracing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ykws.android.maro.data.location.GpsFix
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.settings.SettingsManager
import java.io.File

/**
 * Unit tests for [TripRecorder] — coroutine-based state machine.
 *
 * Uses [StandardTestDispatcher] to control time. Real [SettingsManager] and
 * [TripRepository] are wired with a [StubContext] so the full save chain is
 * exercised. Only [GeofenceChecker] is faked to control geofence outcomes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripRecorderTest {

    private lateinit var testScope: TestScope
    private lateinit var tempDir: File
    private lateinit var geofenceChecker: FakeGeofenceChecker
    private lateinit var tripRepository: TripRepository
    private lateinit var settingsManager: SettingsManager
    private lateinit var recorder: TripRecorder

    @Before
    fun setUp() {
        testScope = TestScope()
        tempDir = createTempDir("tripRecorderTest")
        geofenceChecker = FakeGeofenceChecker()
        // TripRepository override that saves on the caller's dispatcher (not hardcoded IO)
        tripRepository = object : TripRepository(StubContext(tempDir)) {
            override suspend fun save(trip: Trip): Result<Unit> = runCatching {
                val bytes = kotlinx.serialization.protobuf.ProtoBuf.encodeToByteArray(
                    Trip.serializer(), trip
                )
                java.io.File(tempDir, "trips/${trip.id}.trip").also {
                    it.parentFile.mkdirs()
                }.writeBytes(bytes)
            }
        }
        settingsManager = SettingsManager(StubContext())
        recorder = TripRecorder(
            geofenceChecker = geofenceChecker,
            tripRepository = tripRepository,
            settingsManager = settingsManager,
            defaultDispatcher = StandardTestDispatcher(testScope.testScheduler),
            ioDispatcher = UnconfinedTestDispatcher(testScope.testScheduler),
            tooLongPausedMs = Long.MAX_VALUE
        )
    }

    @After
    fun tearDown() {
        recorder.dispose()
        tempDir.deleteRecursively()
        testScope.cancel()
    }

    @Test
    fun `initial state is IDLE`() {
        assertEquals(RecorderState.IDLE, recorder.state.value)
        assertNull(recorder.activeTrip.value)
    }

    @Test
    fun `IDLE to RECORDING when fix outside geofence and tracing enabled`() = testScope.runTest {
        settingsManager.update { it.copy(tracingEnabled = true) }
        geofenceChecker.insideResult = false  // outside geofence

        recorder.connect(flowOf(fix(lat = 43.5, lon = 7.1)))
        advanceUntilIdle()

        assertEquals(RecorderState.RECORDING, recorder.state.value)
        assertNotNull(recorder.activeTrip.value)
    }

    @Test
    fun `IDLE stays IDLE when fix inside geofence`() = testScope.runTest {
        settingsManager.update { it.copy(tracingEnabled = true) }
        geofenceChecker.insideResult = true  // inside geofence

        recorder.connect(flowOf(fix()))
        advanceUntilIdle()

        assertEquals(RecorderState.IDLE, recorder.state.value)
    }

    @Test
    fun `IDLE stays IDLE when fix outside but tracing disabled`() = testScope.runTest {
        settingsManager.update { it.copy(tracingEnabled = false) }
        geofenceChecker.insideResult = false

        recorder.connect(flowOf(fix(lat = 43.5, lon = 7.1)))
        advanceUntilIdle()

        assertEquals(RecorderState.IDLE, recorder.state.value)
    }

    @Test
    fun `startManually transitions IDLE to RECORDING without GPS fix`() {
        recorder.startManually("Manual Trip")

        assertEquals(RecorderState.RECORDING, recorder.state.value)
        assertNotNull(recorder.activeTrip.value)
        assertEquals("Manual Trip", recorder.activeTrip.value?.name)
    }

    @Test
    fun `stop transitions RECORDING through FINALIZING to IDLE`() = testScope.runTest {
        settingsManager.update { it.copy(tracingEnabled = true) }
        geofenceChecker.insideResult = false
        recorder.connect(flowOf(fix(lat = 43.5, lon = 7.1)))
        advanceUntilIdle()
        assertEquals(RecorderState.RECORDING, recorder.state.value)

        recorder.stop()
        advanceUntilIdle()

        assertEquals(RecorderState.IDLE, recorder.state.value)
        assertNull(recorder.activeTrip.value)
    }

    @Test
    fun `RECORDING to PAUSED on geofence re-entry`() = testScope.runTest {
        settingsManager.update { it.copy(tracingEnabled = true) }

        // First fix outside → RECORDING
        geofenceChecker.insideResult = false
        recorder.connect(flowOf(fix(lat = 43.5, lon = 7.1)))
        advanceUntilIdle()
        assertEquals(RecorderState.RECORDING, recorder.state.value)

        // Replace flow with inside-geofence fix → PAUSED
        geofenceChecker.insideResult = true
        recorder.connect(flowOf(fix()))
        advanceUntilIdle()

        assertEquals(RecorderState.PAUSED, recorder.state.value)
    }

    @Test
    fun `PAUSED to RECORDING when geofence exited again`() = testScope.runTest {
        settingsManager.update { it.copy(tracingEnabled = true) }

        // Start recording: outside geofence
        geofenceChecker.insideResult = false
        recorder.connect(flowOf(fix(lat = 43.5, lon = 7.1)))
        advanceUntilIdle()
        assertEquals(RecorderState.RECORDING, recorder.state.value)

        // Pause: inside geofence
        geofenceChecker.insideResult = true
        recorder.connect(flowOf(fix()))
        advanceUntilIdle()
        assertEquals(RecorderState.PAUSED, recorder.state.value)

        // Resume: outside geofence again
        geofenceChecker.insideResult = false
        recorder.connect(flowOf(fix(lat = 43.5, lon = 7.1)))
        advanceUntilIdle()

        assertEquals(RecorderState.RECORDING, recorder.state.value)
    }

    @Test
    fun `RECORDING captures TracePoint when speed exceeds threshold`() = testScope.runTest {
        settingsManager.update { it.copy(tracingEnabled = true) }
        geofenceChecker.insideResult = false

        // Start recording first (first fix transitions IDLE→RECORDING, doesn't capture)
        recorder.startManually("Test")

        val startMs = 1_700_000_000_000L
        val fixWithSpeed = fix(
            lat = 43.5, lon = 7.1,
            speedMps = 3.0f,  // > 2.5 kn ≈ 1.286 m/s
            timestampEpochMs = startMs + 60_000
        )

        recorder.connect(flowOf(fixWithSpeed))
        advanceUntilIdle()

        val trip = recorder.activeTrip.value
        assertNotNull(trip)
        assertEquals(1, trip?.tracePoints?.size)
        assertEquals(3.0f, trip?.tracePoints?.get(0)?.speedMps)
    }

    @Test
    fun `RECORDING skips TracePoint when speed below threshold`() = testScope.runTest {
        settingsManager.update { it.copy(tracingEnabled = true) }
        geofenceChecker.insideResult = false

        val fixSlow = fix(
            lat = 43.5, lon = 7.1,
            speedMps = 1.0f,  // < 2.5 kn
            timestampEpochMs = 1_700_000_060_000L
        )

        recorder.connect(flowOf(fixSlow))
        advanceUntilIdle()

        val trip = recorder.activeTrip.value
        assertNotNull(trip)
        assertEquals(0, trip?.tracePoints?.size)
    }

    @Test
    fun `events flow emits Started during transition to RECORDING`() = testScope.runTest {
        settingsManager.update { it.copy(tracingEnabled = true) }
        geofenceChecker.insideResult = false

        val events = mutableListOf<TripEvent>()
        val collectorJob = backgroundScope.launch(UnconfinedTestDispatcher(testScope.testScheduler)) {
            recorder.events.collect { events.add(it) }
        }

        recorder.connect(flowOf(fix(lat = 43.5, lon = 7.1)))
        advanceUntilIdle()

        assertTrue(events.any { it is TripEvent.Started })
        collectorJob.cancel()
    }

    @Test
    fun `events flow emits Paused on geofence re-entry`() = testScope.runTest {
        settingsManager.update { it.copy(tracingEnabled = true) }

        val events = mutableListOf<TripEvent>()
        backgroundScope.launch {
            recorder.events.collect { events.add(it) }
        }

        geofenceChecker.insideResult = false
        recorder.connect(flowOf(fix(lat = 43.5, lon = 7.1)))
        advanceUntilIdle()

        geofenceChecker.insideResult = true
        recorder.connect(flowOf(fix()))
        advanceUntilIdle()

        assertTrue(events.any { it is TripEvent.Paused })
    }

    // ── Fakes ───────────────────────────────────────────────────────────────

    private class FakeGeofenceChecker : GeofenceChecker() {
        var insideResult: Boolean = true

        override fun isInsideGeofence(
            posLat: Double, posLon: Double,
            originLat: Double, originLon: Double,
            radiusM: Float
        ): Boolean = insideResult
    }

    // ── Test helpers ────────────────────────────────────────────────────────

    private fun fix(
        lat: Double = 43.5283,
        lon: Double = 7.0450,
        speedMps: Float? = null,
        bearingDeg: Float? = null,
        timestampEpochMs: Long = 1_700_000_000_000L
    ): GpsFix = GpsFix(
        position = LatLng(lat, lon),
        bearingDeg = bearingDeg,
        hasCourse = bearingDeg != null,
        speedMps = speedMps,
        timestampEpochMs = timestampEpochMs,
        hasLock = true
    )
}
