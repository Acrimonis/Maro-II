package ykws.android.maro.data.track

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A revision is acknowledged only after persistence succeeds; queued older writes cannot replace it. */
internal class RecordingCheckpoint(private val save: suspend (Track) -> Unit) {
    data class Snapshot(val revision: Long, val track: Track)

    private val mutex = Mutex()
    private var persistedRevision = -1L

    suspend fun write(snapshot: Snapshot) = mutex.withLock {
        if (snapshot.revision > persistedRevision) {
            save(snapshot.track)
            persistedRevision = snapshot.revision
        }
    }

    /** Deletion and acknowledgement share the lock. A failed/cancelled delete also permits a retry. */
    suspend fun invalidateWhile(delete: suspend () -> Unit) = mutex.withLock {
        persistedRevision = -1L
        delete()
    }
}
