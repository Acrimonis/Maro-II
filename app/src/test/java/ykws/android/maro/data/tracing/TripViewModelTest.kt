package ykws.android.maro.data.tracing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ykws.android.maro.data.location.GpsFix
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.settings.SettingsManager
import java.io.File

/**
 * Unit tests for [TripViewModel] — verifies [StateFlow] emits correct states
 * during a mock recording sequence.
 *
 * Uses real [TripRepository] and [SettingsManager] wired with [StubContext].
 * Only [GeofenceChecker] is faked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripViewModelTest {

    private lateinit var testScope: TestScope
    private lateinit var tempDir: File
    private lateinit var geofenceChecker: FakeGeofenceChecker
    private lateinit var tripRepository: TripRepository
    private lateinit var settingsManager: SettingsManager
    private lateinit var tripRecorder: TripRecorder
    private lateinit var viewModel: TripViewModel

    @Before
    fun setUp() {
        testScope = TestScope()
        Dispatchers.setMain(StandardTestDispatcher(testScope.testScheduler))
        tempDir = createTempDir("tripViewModelTest")
        geofenceChecker = FakeGeofenceChecker()
        // TripRepository override — all ops run on caller's dispatcher (no hardcoded IO)
        tripRepository = object : TripRepository(StubContext(tempDir)) {
            private fun tripFile(): java.io.File {
                val dir = java.io.File(tempDir, "trips")
                dir.mkdirs()
                return dir
            }

            override suspend fun save(trip: Trip): Result<Unit> = runCatching {
                val bytes = kotlinx.serialization.protobuf.ProtoBuf.encodeToByteArray(
                    Trip.serializer(), trip
                )
                java.io.File(tripFile(), "${trip.id}.trip").writeBytes(bytes)
            }

            override suspend fun load(id: String): Result<Trip> = runCatching {
                kotlinx.serialization.protobuf.ProtoBuf.decodeFromByteArray(
                    Trip.serializer(),
                    java.io.File(tripFile(), "$id.trip").readBytes()
                )
            }

            override suspend fun list(): Result<List<TripSummary>> = runCatching {
                (tripFile().listFiles { f -> f.extension == "trip" } ?: emptyArray())
                    .map { file ->
                        val trip = kotlinx.serialization.protobuf.ProtoBuf.decodeFromByteArray(
                            Trip.serializer(), file.readBytes()
                        )
                        TripSummary(
                            id = trip.id,
                            name = trip.name,
                            description = trip.description,
                            startTimeMs = trip.startTimeMs,
                            endTimeMs = trip.endTimeMs,
                            pointCount = trip.tracePoints.size,
                            trackColorArgb = trip.trackColorArgb,
                            pausedDurationSec = trip.pausedDurationSec,
                            fastestSpeedMps = trip.fastestSpeedMps
                        )
                    }
                    .sortedByDescending { it.startTimeMs }
            }

            override suspend fun delete(id: String): Result<Unit> = runCatching {
                java.io.File(tripFile(), "$id.trip").delete()
                Unit
            }
        }
        settingsManager = SettingsManager(StubContext())
        tripRecorder = TripRecorder(
            geofenceChecker = geofenceChecker,
            tripRepository = tripRepository,
            settingsManager = settingsManager,
            defaultDispatcher = StandardTestDispatcher(testScope.testScheduler),
            ioDispatcher = UnconfinedTestDispatcher(testScope.testScheduler),
            tooLongPausedMs = Long.MAX_VALUE
        )
        viewModel = TripViewModel(tripRecorder, tripRepository)
    }

    @After
    fun tearDown() {
        tripRecorder.dispose()
        tempDir.deleteRecursively()
        Dispatchers.resetMain()
        testScope.cancel()
    }

    @Test
    fun `initial UI state is IDLE with empty trips`() {
        val state = viewModel.uiState.value
        assertEquals(RecorderState.IDLE, state.recorderState)
        assertNull(state.activeTrip)
        assertTrue(state.trips.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `startTrip transitions state to RECORDING`() = testScope.runTest {
        viewModel.startTrip("My Voyage")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(RecorderState.RECORDING, state.recorderState)
        assertNotNull(state.activeTrip)
        assertEquals("My Voyage", state.activeTrip?.name)
    }

    @Test
    fun `stopTrip triggers FINALIZING then IDLE and saves trip`() = testScope.runTest {
        viewModel.startTrip("Test")
        advanceUntilIdle()
        assertEquals(RecorderState.RECORDING, viewModel.uiState.value.recorderState)

        viewModel.stopTrip()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(RecorderState.IDLE, state.recorderState)
        assertNull(state.activeTrip)

        // Trip should be persisted
        val trips = tripRepository.list().getOrThrow()
        assertEquals(1, trips.size)
    }

    @Test
    fun `loadTripHistory populates trips list`() = testScope.runTest {
        // Pre-save a trip directly
        val trip = Trip(
            id = java.util.UUID.randomUUID().toString(),
            name = "Pre-saved Trip",
            startTimeMs = 1_700_000_000_000L,
            endTimeMs = 1_700_000_360_000L,
            tracePoints = listOf(
                TracePoint(lat = 43.5, lon = 7.0, timeOffsetSec = 0)
            )
        )
        tripRepository.save(trip)

        viewModel.loadTripHistory()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.trips.size)
        assertEquals("Pre-saved Trip", state.trips[0].name)
    }

    @Test
    fun `deleteTrip removes trip and refreshes list`() = testScope.runTest {
        val trip = Trip(
            id = java.util.UUID.randomUUID().toString(),
            name = "To Delete",
            startTimeMs = 1_700_000_000_000L,
            tracePoints = emptyList()
        )
        tripRepository.save(trip)

        viewModel.loadTripHistory()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.trips.size)

        viewModel.deleteTrip(trip.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.trips.isEmpty())
    }

    @Test
    fun `GPS flow triggers auto-recording when outside geofence`() = testScope.runTest {
        settingsManager.update { it.copy(tracingEnabled = true) }
        geofenceChecker.insideResult = false

        val gpsFlow = flowOf(
            GpsFix(
                position = LatLng(43.5, 7.1),
                bearingDeg = null,
                hasCourse = false,
                speedMps = null,
                timestampEpochMs = 1_700_000_000_000L,
                hasLock = true
            )
        )
        viewModel.connect(gpsFlow)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(RecorderState.RECORDING, state.recorderState)
        assertNotNull(state.activeTrip)
    }

    @Test
    fun `GPS flow inside geofence does not start recording`() = testScope.runTest {
        settingsManager.update { it.copy(tracingEnabled = true) }
        geofenceChecker.insideResult = true

        val gpsFlow = flowOf(
            GpsFix(
                position = LatLng(43.5283, 7.0450),
                bearingDeg = null,
                hasCourse = false,
                speedMps = null,
                timestampEpochMs = 1_700_000_000_000L,
                hasLock = true
            )
        )
        viewModel.connect(gpsFlow)
        advanceUntilIdle()

        assertEquals(RecorderState.IDLE, viewModel.uiState.value.recorderState)
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
}
