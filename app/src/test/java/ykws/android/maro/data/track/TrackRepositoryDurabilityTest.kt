package ykws.android.maro.data.track

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The repository's durability rules: a checkpoint that outlives its finalised file is the newer
 * recording generation and is offered back, a checkpoint the finalised file already covers is the
 * leftover of a completed finalize and is dropped, an undecodable file is moved aside rather than
 * deleted, and every write lands through a temp file that does not linger.
 */
class TrackRepositoryDurabilityTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val repo get() = TrackRepository(temp.root)

    private fun point(offsetMs: Long) = TrackPoint(lat = 43.5, lon = 7.0, speedMps = 2f, timeOffsetMs = offsetMs)

    private fun track(id: String, offsetsMs: List<Long>, endTimeMs: Long? = null) = Track(
        id = id,
        name = id,
        startTimeMs = 1_000L,
        endTimeMs = endTimeMs,
        trackPoints = offsetsMs.map(::point)
    )

    @Test
    fun aCheckpointNewerThanTheFinalisedFileIsOfferedForRecovery() = runBlocking {
        // A trip finalised at 11 s …
        repo.save(track("t", listOf(0L, 10_000L), endTimeMs = 11_000L).copy(lastPointTimeMs = 11_000L))
        // … then resumed under the same id and checkpointed with a point a minute later.
        repo.saveCheckpoint(track("t", listOf(0L, 10_000L, 60_000L)))

        val recovered = repo.recoverOrphanedCheckpoints()

        assertEquals(listOf("t"), recovered.map { it.id })
        assertEquals(3, recovered.single().trackPoints.size)
        assertTrue(File(temp.root, "t_checkpoint.bin").exists())
    }

    @Test
    fun aCheckpointTheFinalisedFileAlreadyCoversIsDropped() = runBlocking {
        val live = track("t", listOf(0L, 10_000L))
        repo.saveCheckpoint(live)
        // The finalize landed, then the process died before the checkpoint's delete.
        repo.save(live.copy(endTimeMs = 11_000L, lastPointTimeMs = 11_000L))

        assertTrue(repo.recoverOrphanedCheckpoints().isEmpty())
        assertFalse(File(temp.root, "t_checkpoint.bin").exists())
    }

    @Test
    fun anUndecodableTrackIsQuarantinedNotDeleted() = runBlocking {
        repo.save(track("good", listOf(0L)))
        // Field 1 as a truncated length-delimited value: no protobuf reader accepts it.
        File(temp.root, "bad.bin").writeBytes(byteArrayOf(0x0a, 0xff.toByte(), 0xff.toByte()))

        assertNull(repo.load("bad"))

        val quarantined = File(temp.root, "quarantine").listFiles().orEmpty()
        assertEquals(1, quarantined.size)
        assertTrue(quarantined.single().name.startsWith("bad.bin."))
        assertFalse(File(temp.root, "bad.bin").exists())
        assertEquals(listOf("good"), repo.listTracks().map { it.id })
    }

    @Test
    fun everyWriteLandsWithoutATempFileLeftBehind() = runBlocking {
        repo.saveCheckpoint(track("t", listOf(0L)))
        repo.save(track("u", listOf(0L), endTimeMs = 2_000L))

        val names = temp.root.list().orEmpty().toList()
        assertTrue(names.contains("t_checkpoint.bin"))
        assertTrue(names.contains("u.bin"))
        assertTrue(names.contains("index.bin"))
        assertTrue(names.none { it.endsWith(".tmp") })
    }
}
