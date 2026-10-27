package ykws.android.maro.data.track

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.spatial.WhereAmIMatch
import ykws.android.maro.spatial.WhereAmIResult

/**
 * The port-zone exclusion (settled 2026-10-24): a marker whose point stands inside the home-port
 * circle never names the trip — not through the comment's traversed fallback and not through the
 * title's idle-stop body.
 */
class TrackPortZoneNamingTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val homeLat = 43.5696
    private val homeLon = 7.1312

    private val homeMarker = UserMarker(
        id = "home",
        name = "Home",
        geometry = MarkerGeometry.Pin(LatLng(homeLat, homeLon))
    )

    private fun portZoneWhereAmI(pos: LatLng): WhereAmIResult =
        if (TrackGeofenceChecker.isInsideGeofence(pos.latitude, pos.longitude, homeLat, homeLon, 50.0)) {
            WhereAmIResult(listOf(WhereAmIMatch.ZoneMatch(homeMarker, 10.0, 0.0, 0.0)))
        } else {
            WhereAmIResult(emptyList())
        }

    private fun recorder(repo: TrackRepository, stopped: MutableStateFlow<Boolean>, idle: Boolean = false) =
        TrackRecorder(
            repository = repo,
            config = TrackRecorderConfig(
                geofenceOriginLat = homeLat,
                geofenceOriginLon = homeLon,
                geofenceRadiusM = 50.0,
                geofenceEnabled = false,
                gpsMode = false,
                idleThresholdSec = if (idle) 1 else 60
            ),
            isStopped = stopped,
            dispatcher = Dispatchers.Unconfined,
            idleThresholdCallback = if (idle) object : IdleThresholdCallback {
                override suspend fun onIdleThresholdReached(position: LatLng): IdleCaptureResult =
                    IdleCaptureResult(
                        listOf(
                            MarkerSnapshot(
                                markerId = homeMarker.id,
                                name = homeMarker.name,
                                geometryType = "PIN",
                                lat = position.latitude,
                                lon = position.longitude,
                                distanceNm = 0.0,
                                bearingDeg = 0.0
                            )
                        ),
                        shouldOpenDrawer = false
                    )
            } else null,
            whereAmI = { pos -> portZoneWhereAmI(pos) }
        )

    private fun sample(lat: Double, lon: Double) = TrackSample(
        position = LatLng(lat, lon),
        speedMps = 3f,
        bearingDeg = null,
        hasLock = true,
        timestampEpochMs = System.currentTimeMillis()
    )

    private suspend fun stopAndAwaitSave(recorder: TrackRecorder) {
        recorder.stop()
        // Stop is asynchronous: OFF is published only after the finalized file is durable.
        withTimeout(5_000) { recorder.uiState.first { it.state == TrackRecorderState.OFF } }
    }

    @Test
    fun `a port-zone marker stays out of the traversed fallback`() = runBlocking {
        val repo = TrackRepository(temp.root)
        val stopped = MutableStateFlow(false)
        val samples = MutableSharedFlow<TrackSample>(extraBufferCapacity = 16)
        val rec = recorder(repo, stopped)
        try {
            rec.start(samples)
            rec.startManual()
            val id = rec.uiState.value.currentTrackId!!
            // Two moving fixes inside the circle: the trip records points but stays at the port.
            samples.tryEmit(sample(homeLat, homeLon))
            samples.tryEmit(sample(homeLat + 0.0001, homeLon + 0.0001))
            stopAndAwaitSave(rec)

            val saved = repo.load(id)
            assertTrue(saved != null)
            assertFalse(saved!!.comment.orEmpty().contains(homeMarker.name))
        } finally {
            rec.dispose()
        }
    }

    @Test
    fun `a port-zone idle stop never names the trip`() = runBlocking {
        val repo = TrackRepository(temp.root)
        val stopped = MutableStateFlow(false)
        val samples = MutableSharedFlow<TrackSample>(extraBufferCapacity = 16)
        val rec = recorder(repo, stopped, idle = true)
        try {
            rec.start(samples)
            rec.startManual()
            val id = rec.uiState.value.currentTrackId!!
            // Two moving sea fixes: the trip records points without meeting any marker.
            samples.tryEmit(sample(homeLat + 0.05, homeLon + 0.05))
            samples.tryEmit(sample(homeLat + 0.0501, homeLon + 0.0501))
            // Idle inside the circle: the idle timer creates a Home BoatMarker at the port.
            stopped.value = true
            samples.tryEmit(sample(homeLat, homeLon))
            delay(2_000)
            stopAndAwaitSave(rec)

            val saved = repo.load(id)
            assertTrue(saved != null)
            assertFalse(saved!!.name.contains(homeMarker.name))
        } finally {
            rec.dispose()
        }
    }
}
