package ykws.android.maro.data.track

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The repository's half of the trace work (R29): the projection in the pass that already opens every
 * track to write the summary index, and the rebuild the index's version stamp forces on an index
 * written before the field existed.
 *
 * The second case is the load-bearing one: without the stamp the rebuilt index would be the only thing
 * that ever carried the flag, and every route already stored would decode `false` — a missing bool
 * reading as off — leaving the filter answering nothing.
 */
class TrackRepositoryTraceIndexTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val repo get() = TrackRepository(temp.root)

    private fun track(id: String, trace: Boolean = false, pinned: Boolean = false) = Track(
        id = id,
        name = id,
        startTimeMs = 1_000L,
        endTimeMs = 2_000L,
        pinned = pinned,
        trace = trace
    )

    @Test
    fun theSummaryCarriesTheFlagFromThePassThatWritesTheIndex() = runBlocking {
        repo.save(track("route-1", trace = true))
        repo.save(track("recording-1"))

        val summaries = repo.listTracks().associateBy { it.id }

        assertTrue(summaries.getValue("route-1").trace)
        assertFalse(summaries.getValue("recording-1").trace)
    }

    @Test
    fun anIndexStampedWithAnOlderSchemaIsRebuiltOnce() = runBlocking {
        repo.save(track("route-1", trace = true))
        // The index as the build before this one wrote it: no trace field, and the absent stamp.
        val stale = TrackSummary(
            id = "route-1",
            name = "route-1",
            startTimeMs = 1_000L,
            endTimeMs = 2_000L
        )
        java.io.File(temp.root, "index.bin").writeBytes(
            ProtoBuf.Default.encodeToByteArray(
                TrackSummaryList.serializer(),
                TrackSummaryList(tracks = listOf(stale), version = 0)
            )
        )

        // The pre-stamp default — the fabricated index's own version — is the value the field's
        // introduction had to move the stamp off: a build that forgot the bump would read that index as
        // current and leave every stored route reading `false`.
        assertTrue("introducing the field must bump the index stamp", SUMMARY_INDEX_VERSION > 0)

        val summaries = repo.listTracks()

        assertEquals(1, summaries.size)
        assertTrue("a stale stamp rebuilds the index from the track files", summaries.first().trace)
        // And the rebuild is **once**: the index it wrote carries the current stamp, so the next read
        // serves it rather than rebuilding the library again. Without this, a write leaving the stamp
        // stale would stay green while every list call re-read every track.
        val rewritten = ProtoBuf.Default.decodeFromByteArray(
            TrackSummaryList.serializer(),
            java.io.File(temp.root, "index.bin").readBytes()
        )
        assertEquals(SUMMARY_INDEX_VERSION, rewritten.version)
    }
}
