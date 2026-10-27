package ykws.android.maro.data.track

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ykws.android.maro.data.model.LatLng
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TrackRecorderCheckpointTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun stationaryTicksSkipButMetadataAndFailedWritesStillCheckpoint() = runTest {
        val saved = mutableListOf<Track>()
        var fail = false
        var attempts = 0
        val repository = object : TrackRepository(temp.root) {
            override suspend fun saveCheckpoint(track: Track) {
                attempts++
                if (fail) throw IOException("full")
                saved += track
            }
        }
        val recorder = TrackRecorder(repository,
            config = TrackRecorderConfig(gpsMode = false, geofenceEnabled = false),
            dispatcher = StandardTestDispatcher(testScheduler),
            monotonicNowMs = { testScheduler.currentTime })
        try {
            recorder.startManual()
            runCurrent()
            advanceTimeBy(30_001)
            assertEquals(1, saved.size)
            advanceTimeBy(90_000)
            assertEquals(1, attempts)
            recorder.updateCurrentTrackMeta(name = "renamed", comment = "comment")
            runCurrent()
            assertEquals(2, saved.size)
            assertEquals("renamed", saved.last().name)
            recorder.updateCurrentTrackMeta(name = "renamed", comment = "comment")
            runCurrent()
            assertEquals(2, attempts)
            fail = true
            recorder.updateCurrentTrackMeta(comment = "retry")
            runCurrent()
            assertEquals(3, attempts)
            fail = false
            advanceTimeBy(30_000)
            assertEquals(4, attempts)
            assertEquals("retry", saved.last().comment)
        } finally { recorder.dispose() }
    }

    @Test fun resumePublishesIndependentSnapshotsAndKeepsTheGap() = runTest {
        val repository = object : TrackRepository(temp.root) {
            override suspend fun deleteCheckpoint(id: String) = Unit
        }
        val samples = MutableSharedFlow<TrackSample>(extraBufferCapacity = 8)
        val seed = List(300) { TrackPoint(43.5, 7.0, timeOffsetMs = it * 1_000L) }
        val track = Track("resume", "resume", startTimeMs = 1, trackPoints = seed)
        val recorder = TrackRecorder(repository,
            config = TrackRecorderConfig(gpsMode = false, geofenceEnabled = false),
            dispatcher = StandardTestDispatcher(testScheduler),
            monotonicNowMs = { testScheduler.currentTime })
        try {
            recorder.resume(track, samples)
            runCurrent()
            val previous = recorder.snapshotPoints()
            samples.emit(TrackSample(LatLng(43.6, 7.1), speedMps = 3f, bearingDeg = null,
                hasLock = true, timestampEpochMs = System.currentTimeMillis()))
            runCurrent()
            assertEquals(seed, previous)
            assertEquals(seed, recorder.snapshotPoints().take(seed.size))
            assertEquals(PointType.GAP, recorder.snapshotPoints()[seed.size].type)
            assertEquals(seed.size + 2, recorder.snapshotPoints().size)
        } finally { recorder.dispose() }
    }
}
