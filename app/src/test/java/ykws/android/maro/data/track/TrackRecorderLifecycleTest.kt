package ykws.android.maro.data.track

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ykws.android.maro.data.model.LatLng
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The recorder's lifecycle under the conditions the review named: a Stop whose persistence is slow,
 * commands that overlap it, a final save that fails, and the retry that follows. The repository is
 * the real one over a temp directory, with its `save` gated or failed by the test.
 */
class TrackRecorderLifecycleTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** The real repository with a gate on `save`: closed, a finalize parks inside its IO hop. */
    private class GatedRepository(dir: File) : TrackRepository(dir) {
        val gate = CompletableDeferred<Unit>()
        @Volatile var failSaves = false
        @Volatile var saves = 0

        override suspend fun save(track: Track) {
            if (failSaves) throw IOException("disk full (test)")
            gate.await()
            saves++
            super.save(track)
        }
    }

    private class Harness(dir: File) {
        val repo = GatedRepository(dir)
        val samples = MutableSharedFlow<TrackSample>(extraBufferCapacity = 64)
        val recorder = TrackRecorder(
            repository = repo,
            config = TrackRecorderConfig(gpsMode = false, geofenceEnabled = false, simplifyEnabled = false),
            dispatcher = Dispatchers.Default
        )
        val events = CopyOnWriteArrayList<TrackEvent>()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        init {
            scope.launch { recorder.events.collect { events += it } }
            recorder.start(samples)
        }

        suspend fun startAndRecord(points: Int): String {
            recorder.startManual()
            awaitState(TrackRecorderState.ON)
            repeat(points) { i ->
                samples.emit(
                    TrackSample(
                        position = LatLng(43.50 + i * 0.001, 7.00 + i * 0.001),
                        speedMps = 3f,
                        bearingDeg = 90f,
                        hasLock = true,
                        timestampEpochMs = 1_700_000_000_000L + i * 1_000L
                    )
                )
            }
            withTimeout(5_000) { recorder.uiState.first { it.pointCount >= points } }
            return checkNotNull(recorder.uiState.value.currentTrackId)
        }

        suspend fun awaitState(state: TrackRecorderState) {
            withTimeout(5_000) { recorder.uiState.first { it.state == state } }
        }

        fun close() {
            recorder.dispose()
            scope.cancel()
        }
    }

    private fun finalFile(id: String) = File(temp.root, "$id.bin")
    private fun checkpointFile(id: String) = File(temp.root, "${id}_checkpoint.bin")

    @Test
    fun stopReturnsAtOnceAndReadsOnUntilTheSaveLands() = runBlocking {
        val h = Harness(temp.root)
        try {
            val id = h.startAndRecord(3)
            h.recorder.stop()
            delay(300)
            assertEquals("still ON while the save is parked", TrackRecorderState.ON, h.recorder.uiState.value.state)
            assertFalse(finalFile(id).exists())

            h.repo.gate.complete(Unit)
            h.awaitState(TrackRecorderState.OFF)
            assertTrue(finalFile(id).exists())
            assertFalse(checkpointFile(id).exists())
            assertEquals(1, h.events.count { it is TrackEvent.Finalized })
        } finally {
            h.close()
        }
    }

    @Test
    fun aDiscardDuringAFinalizeWaitsAndThenFindsNothingToDiscard() = runBlocking {
        val h = Harness(temp.root)
        try {
            val id = h.startAndRecord(3)
            h.recorder.stop()
            h.recorder.discard()
            h.repo.gate.complete(Unit)
            h.awaitState(TrackRecorderState.OFF)
            delay(200)
            assertTrue("the finalize's file survives the discard that waited on it", finalFile(id).exists())
            assertEquals(1, h.events.count { it is TrackEvent.Finalized })
            assertEquals(1, h.events.count { it === TrackEvent.Stopped })
        } finally {
            h.close()
        }
    }

    @Test
    fun aSecondStopDuringAFinalizeDoesNotFinalizeTwice() = runBlocking {
        val h = Harness(temp.root)
        try {
            val id = h.startAndRecord(2)
            h.recorder.stop()
            h.recorder.stop()
            h.repo.gate.complete(Unit)
            h.awaitState(TrackRecorderState.OFF)
            delay(200)
            assertEquals(1, h.repo.saves)
            assertEquals(1, h.events.count { it is TrackEvent.Finalized })
            assertTrue(finalFile(id).exists())
        } finally {
            h.close()
        }
    }

    @Test
    fun aFailedSaveRetainsTheRecordingAsACheckpointAndTheNextStopRetries() = runBlocking {
        val h = Harness(temp.root)
        try {
            val id = h.startAndRecord(4)
            assertFalse("no checkpoint yet: the periodic writer has not fired", checkpointFile(id).exists())

            h.repo.failSaves = true
            h.repo.gate.complete(Unit)
            h.recorder.stop()
            withTimeout(5_000) { h.recorder.uiState.first { it.infoError != null } }
            assertEquals("retained: the recorder stays ON", TrackRecorderState.ON, h.recorder.uiState.value.state)
            assertFalse(finalFile(id).exists())
            assertTrue("the complete snapshot went to disk as a checkpoint", checkpointFile(id).exists())
            val recoverable = h.repo.recoverOrphanedCheckpoints().single()
            assertEquals(id, recoverable.id)
            assertEquals(4, recoverable.trackPoints.size)
            assertEquals(0, h.events.count { it is TrackEvent.Finalized })

            h.repo.failSaves = false
            h.recorder.stop()
            h.awaitState(TrackRecorderState.OFF)
            assertTrue(finalFile(id).exists())
            assertFalse(checkpointFile(id).exists())
            assertEquals(1, h.events.count { it is TrackEvent.Finalized })
            assertNotNull(h.repo.load(id))
        } finally {
            h.close()
        }
    }

    @Test
    fun aFailedSaveOverAStaleCheckpointLeavesTheNewerSnapshot() = runBlocking {
        val h = Harness(temp.root)
        try {
            val id = h.startAndRecord(2)
            // A meta update writes a checkpoint: the stale one, two points old.
            h.recorder.updateCurrentTrackMeta(name = "renamed")
            withTimeout(5_000) { while (!checkpointFile(id).exists()) delay(20) }
            h.startMore(id, from = 2, count = 3)

            h.repo.failSaves = true
            h.repo.gate.complete(Unit)
            h.recorder.stop()
            withTimeout(5_000) { h.recorder.uiState.first { it.infoError != null } }
            val recoverable = h.repo.recoverOrphanedCheckpoints().single()
            assertEquals("the checkpoint carries every point, not the stale two", 5, recoverable.trackPoints.size)
            assertEquals("renamed", recoverable.name)
        } finally {
            h.close()
        }
    }

    private suspend fun Harness.startMore(id: String, from: Int, count: Int) {
        repeat(count) { i ->
            val n = from + i
            samples.emit(
                TrackSample(
                    position = LatLng(43.50 + n * 0.001, 7.00 + n * 0.001),
                    speedMps = 3f,
                    bearingDeg = 90f,
                    hasLock = true,
                    timestampEpochMs = 1_700_000_000_000L + n * 1_000L
                )
            )
        }
        withTimeout(5_000) { recorder.uiState.first { it.currentTrackId == id && it.pointCount >= from + count } }
    }
}
