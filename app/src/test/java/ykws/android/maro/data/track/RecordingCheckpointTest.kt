package ykws.android.maro.data.track

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RecordingCheckpointTest {
    private val track = Track("trip", "trip", startTimeMs = 0)

    @Test fun unchangedRevisionsSkipAndMetadataChangesPersist() = runTest {
        val writes = mutableListOf<Track>()
        val checkpoints = RecordingCheckpoint { writes += it }
        val original = RecordingCheckpoint.Snapshot(1, track)
        repeat(120) { checkpoints.write(original) }
        val renamed = RecordingCheckpoint.Snapshot(2, track.copy(name = "renamed"))
        checkpoints.write(renamed)
        checkpoints.write(original) // An older queued request must not roll back the disk.
        assertEquals(listOf(track, renamed.track), writes)
    }

    @Test fun failureDoesNotAcknowledgeTheRevision() = runTest {
        var attempts = 0
        val checkpoints = RecordingCheckpoint {
            if (++attempts == 1) throw IOException("disk full")
        }
        val snapshot = RecordingCheckpoint.Snapshot(1, track)
        try { checkpoints.write(snapshot); fail("failure expected") } catch (_: IOException) { }
        checkpoints.write(snapshot)
        checkpoints.write(snapshot)
        assertEquals(2, attempts)
    }

    @Test fun changesDuringPersistenceRemainDirtyAndWritesStayOrdered() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writes = mutableListOf<Track>()
        val checkpoints = RecordingCheckpoint {
            if (writes.isEmpty()) { entered.complete(Unit); release.await() }
            writes += it
        }
        val first = async { checkpoints.write(RecordingCheckpoint.Snapshot(1, track)) }
        entered.await()
        val changed = track.copy(comment = "changed while writing")
        val second = async { checkpoints.write(RecordingCheckpoint.Snapshot(2, changed)) }
        release.complete(Unit)
        first.await()
        second.await()
        assertEquals(listOf(track, changed), writes)
    }

    @Test fun deletionIncludingFailureMakesTheSameRevisionEligibleAgain() = runTest {
        var writes = 0
        val checkpoints = RecordingCheckpoint { writes++ }
        val snapshot = RecordingCheckpoint.Snapshot(1, track)
        checkpoints.write(snapshot)
        checkpoints.invalidateWhile { }
        checkpoints.write(snapshot)
        try {
            checkpoints.invalidateWhile { throw IOException("delete failed") }
            fail("failure expected")
        } catch (_: IOException) { }
        checkpoints.write(snapshot)
        assertEquals(3, writes)
    }

    @Test fun cancellationLeavesTheRevisionEligibleForRetry() = runTest {
        val entered = CompletableDeferred<Unit>()
        var attempts = 0
        val checkpoints = RecordingCheckpoint {
            if (++attempts == 1) { entered.complete(Unit); awaitCancellation() }
        }
        val snapshot = RecordingCheckpoint.Snapshot(1, track)
        val first = async { checkpoints.write(snapshot) }
        entered.await()
        first.cancelAndJoin()
        checkpoints.write(snapshot)
        assertEquals(2, attempts)
    }
}
