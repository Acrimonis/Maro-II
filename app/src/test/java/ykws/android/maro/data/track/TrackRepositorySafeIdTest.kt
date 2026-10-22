package ykws.android.maro.data.track

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

/**
 * The id gate on the tracks directory (N06): a track id becomes a file name, so one that is not a
 * plain path segment — a traversal, a hidden file, one of the directory's own reserved names — is
 * refused before it reaches a path, and every file accessor refuses it rather than writing it.
 */
class TrackRepositorySafeIdTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val repo get() = TrackRepository(temp.root)

    @Test
    fun aUuidIsASafeId() {
        assertTrue(TrackRepository.isSafeId(UUID.randomUUID().toString()))
        assertTrue(TrackRepository.isSafeId("route-1"))
        assertTrue(TrackRepository.isSafeId("t.2"))
    }

    @Test
    fun aTraversalAHiddenNameAReservedNameAndAnEmptyIdAreRefused() {
        assertFalse("a parent segment", TrackRepository.isSafeId("../x"))
        assertFalse("a nested path", TrackRepository.isSafeId("a/b"))
        assertFalse("the index file's own name", TrackRepository.isSafeId("index"))
        assertFalse("the quarantine directory's own name", TrackRepository.isSafeId("quarantine"))
        assertFalse("a hidden file", TrackRepository.isSafeId(".hidden"))
        assertFalse("nothing at all", TrackRepository.isSafeId(""))
        assertFalse("a space", TrackRepository.isSafeId("my trip"))
        assertFalse("past the length cap", TrackRepository.isSafeId("a".repeat(129)))
    }

    @Test
    fun theRepositoryRefusesToWriteUnderAnUnsafeId() = runBlocking {
        val escaping = Track(id = "../escaped", name = "Escape", startTimeMs = 1_000L, endTimeMs = 2_000L)

        try {
            repo.save(escaping)
            fail("an unsafe id must never reach a path")
        } catch (_: IllegalArgumentException) {
            // The gate the accessor keeps.
        }

        assertFalse("nothing landed outside the directory", java.io.File(temp.root.parentFile, "escaped.bin").exists())
        assertEquals(0, repo.listTracks().size)
    }
}
