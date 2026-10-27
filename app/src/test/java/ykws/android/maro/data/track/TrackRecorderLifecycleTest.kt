package ykws.android.maro.data.track

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
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
 * commands that overlap it while it is parked inside the save, a final save that fails, and the
 * retry that follows. The repository is the real one over a temp directory, with its `save` gated
 * or failed by the test; every wait is on a signal, never on a sleep.
 */
class TrackRecorderLifecycleTest {

    @get:Rule
    val temp = TemporaryFolder()

    /**
     * The real repository with a gate on `save`: closed, a finalize parks inside its IO hop, and
     * [saveEntered] says when it has — the moment a competing command is guaranteed to overlap it.
     */
    private class GatedRepository(dir: File) : TrackRepository(dir) {
        val gate = CompletableDeferred<Unit>()
        val saveEntered = CompletableDeferred<Unit>()
        @Volatile var failSaves = false
        @Volatile var saves = 0

        override suspend fun save(track: Track) {
            if (failSaves) throw IOException("disk full (test)")
            saveEntered.complete(Unit)
            gate.await()
            saves++
            super.save(track)
        }
    }

    private class Harness(dir: File, clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
        val repo = GatedRepository(dir)
        val samples = MutableSharedFlow<TrackSample>(extraBufferCapacity = 64)
        val recorder = TrackRecorder(
            repository = repo,
            config = TrackRecorderConfig(gpsMode = false, geofenceEnabled = false, simplifyEnabled = false),
            dispatcher = Dispatchers.Default,
            // The platform's monotonic clock answers 0 in a plain JVM test; a real one drives the
            // intervals unless a test hands in its own.
            monotonicNowMs = clock
        )
        val events = CopyOnWriteArrayList<TrackEvent>()
        private val eventsSubscribed = CompletableDeferred<Unit>()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        init {
            scope.launch {
                recorder.events
                    .onSubscription { eventsSubscribed.complete(Unit) }
                    .collect { events += it }
            }
            recorder.start(samples)
        }

        /** Both collectors stand before anything is emitted: a SharedFlow keeps nothing for a late one. */
        suspend fun awaitSubscribed() {
            withTimeout(5_000) {
                eventsSubscribed.await()
                samples.subscriptionCount.first { it > 0 }
            }
        }

        suspend fun startAndRecord(points: Int): String {
            awaitSubscribed()
            recorder.startManual()
            awaitState(TrackRecorderState.ON)
            emitPoints(from = 0, count = points)
            return checkNotNull(recorder.uiState.value.currentTrackId)
        }

        suspend fun emitPoints(from: Int, count: Int) {
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
            withTimeout(5_000) { recorder.uiState.first { it.pointCount >= from + count } }
        }

        suspend fun awaitState(state: TrackRecorderState) {
            withTimeout(5_000) { recorder.uiState.first { it.state == state } }
        }

        /** The save is parked inside the gate: from here a competing command overlaps the finalize. */
        suspend fun awaitSaveParked() {
            withTimeout(5_000) { repo.saveEntered.await() }
        }

        /** Wait for the events collector to have appended what [predicate] expects. */
        suspend fun awaitEvents(predicate: (List<TrackEvent>) -> Boolean) {
            withTimeout(5_000) { while (!predicate(events)) delay(10) }
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
            h.awaitSaveParked()
            assertEquals("ON while the save is parked", TrackRecorderState.ON, h.recorder.uiState.value.state)
            assertFalse(finalFile(id).exists())

            h.repo.gate.complete(Unit)
            h.awaitState(TrackRecorderState.OFF)
            h.awaitEvents { list -> list.count { it is TrackEvent.Finalized } == 1 && list.any { it === TrackEvent.Stopped } }
            assertTrue(finalFile(id).exists())
            assertFalse(checkpointFile(id).exists())
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
            h.awaitSaveParked()
            h.recorder.discard()
            // The discard is queued behind the parked finalize: nothing moves while the gate holds.
            delay(300)
            assertEquals(TrackRecorderState.ON, h.recorder.uiState.value.state)
            assertEquals(0, h.events.count { it === TrackEvent.Stopped })

            h.repo.gate.complete(Unit)
            h.awaitState(TrackRecorderState.OFF)
            h.awaitEvents { list -> list.count { it is TrackEvent.Finalized } == 1 && list.count { it === TrackEvent.Stopped } == 1 }
            // Give a stray second Stopped (a discard that did not find OFF) every chance to appear.
            delay(200)
            assertTrue("the finalize's file survives the discard that waited on it", finalFile(id).exists())
            assertEquals(1, h.events.count { it === TrackEvent.Stopped })
            assertEquals(1, h.repo.saves)
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
            h.awaitSaveParked()
            h.recorder.stop()
            delay(300)
            assertEquals(TrackRecorderState.ON, h.recorder.uiState.value.state)

            h.repo.gate.complete(Unit)
            h.awaitState(TrackRecorderState.OFF)
            h.awaitEvents { list -> list.count { it is TrackEvent.Finalized } == 1 }
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

            // A failed finalize retains a snapshot, and later appends must extend that same recording.
            val retained = h.recorder.snapshotPoints()
            h.emitPoints(from = 4, count = 2)
            assertEquals(4, retained.size)
            assertEquals(retained, h.recorder.snapshotPoints().take(4))

            h.repo.failSaves = false
            h.recorder.stop()
            h.awaitState(TrackRecorderState.OFF)
            h.awaitEvents { list -> list.count { it is TrackEvent.Finalized } == 1 }
            assertTrue(finalFile(id).exists())
            assertFalse(checkpointFile(id).exists())
            assertNotNull(h.repo.load(id))
            assertEquals(6, h.repo.load(id)!!.trackPoints.size)
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
            h.emitPoints(from = 2, count = 3)

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

    @Test
    fun pointOffsetsAndSavedTotalsFollowTheMonotonicClock() = runBlocking {
        var mono = 10_000L
        val h = Harness(temp.root) { mono }
        try {
            h.awaitSubscribed()
            h.recorder.startManual()
            h.awaitState(TrackRecorderState.ON)
            // Three points a second apart on the recorder's own clock; the wall clock is not consulted.
            repeat(3) { i ->
                mono = 10_000L + i * 1_000L
                h.samples.emit(
                    TrackSample(
                        position = LatLng(43.50 + i * 0.002, 7.00),
                        speedMps = 3f, bearingDeg = 0f, hasLock = true,
                        timestampEpochMs = 1_700_000_000_000L + i * 1_000L
                    )
                )
                withTimeout(5_000) { h.recorder.uiState.first { it.pointCount >= i + 1 } }
            }
            mono = 12_000L
            h.repo.gate.complete(Unit)
            h.recorder.stop()
            h.awaitState(TrackRecorderState.OFF)
            h.awaitEvents { list -> list.any { it is TrackEvent.Finalized } }
            val id = h.events.filterIsInstance<TrackEvent.Finalized>().single().trackId
            val saved = checkNotNull(h.repo.load(id))
            assertEquals(listOf(0L, 1_000L, 2_000L), saved.trackPoints.map { it.timeOffsetMs })
            assertEquals("two seconds on the session clock, none idle", 2L, saved.navigatingDurationSec)
            assertEquals(0L, saved.idleDurationSec)
        } finally {
            h.close()
        }
    }
}
