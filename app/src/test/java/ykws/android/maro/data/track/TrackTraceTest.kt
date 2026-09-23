package ykws.android.maro.data.track

import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.ListFilter
import ykws.android.maro.data.model.matchesFilter

/**
 * The trace flag's own behaviours, read where they live on the summary: the filter axis both ways with
 * the live recording exempt (R31), the Resume refusal the summary owns (R41), the merge candidacy that
 * drops a trace (R41), and the blob the exporter writes carrying the flag and a foreign track not
 * (R29, R30).
 */
class TrackTraceTest {

    private fun summary(
        id: String = "t1",
        trace: Boolean = false,
        pinned: Boolean = false,
        live: Boolean = false,
        endTimeMs: Long? = 1_000L
    ) = TrackSummary(
        id = id,
        name = id,
        startTimeMs = 0L,
        endTimeMs = endTimeMs,
        pinned = pinned,
        trace = trace
    ).also { it.isLive = live }

    private fun axis(value: String) = ListFilter(mapOf("trace" to value))

    @Test
    fun theAxisReadsBothSidesOfTheFlag() {
        assertTrue(summary(trace = true).matchesFilter(axis("TRACES"), 0L))
        assertFalse(summary(trace = true).matchesFilter(axis("TRACKS"), 0L))
        assertTrue(summary(trace = false).matchesFilter(axis("TRACKS"), 0L))
        assertFalse(summary(trace = false).matchesFilter(axis("TRACES"), 0L))
    }

    @Test
    fun allLeavesEverySummaryInWhateverItIs() {
        assertTrue(summary(trace = true).matchesFilter(axis("ALL"), 0L))
        assertTrue(summary(trace = false).matchesFilter(axis("ALL"), 0L))
    }

    @Test
    fun aLiveRecordingPassesWhateverTheAxisSays() {
        val live = summary(trace = false, live = true)

        assertTrue(live.matchesFilter(axis("TRACES"), 0L))
        assertTrue(live.matchesFilter(axis("TRACKS"), 0L))
    }

    @Test
    fun resumeIsRefusedForATraceAndAllowedForAFinishedRecording() {
        assertFalse(summary(trace = true).resumeAllowed)
        assertTrue(summary(trace = false).resumeAllowed)
    }

    @Test
    fun resumeStaysRefusedForAnUnfinishedRecording() {
        assertFalse(summary(trace = false, endTimeMs = null).resumeAllowed)
    }

    @Test
    fun theMergeCandidacyDropsATrace() {
        val summaries = listOf(
            summary(id = "a"),
            summary(id = "b", trace = true),
            summary(id = "c")
        )

        assertEquals(setOf("a", "c"), mergeCandidates(summaries, setOf("a", "b", "c")))
        assertEquals(setOf("c"), mergeCandidates(summaries, setOf("b", "c")))
        assertEquals(emptySet<String>(), mergeCandidates(summaries, setOf("b")))
    }

    @Test
    fun theBlobCarriesTheFlagThroughTheRoundTrip() {
        val track = Track(
            id = "route-1",
            name = "Route 2026-09-22 14:50",
            startTimeMs = 1_700_000_000_000L,
            endTimeMs = 1_700_000_360_000L,
            trace = true
        )

        val decoded = ProtoBuf.Default.decodeFromByteArray(
            Track.serializer(),
            ProtoBuf.Default.encodeToByteArray(Track.serializer(), track)
        )

        assertTrue(decoded.trace)
        assertEquals(track.id, decoded.id)
        assertEquals(track.name, decoded.name)
    }

    @Test
    fun aTrackThatNeverSetTheFlagReadsOff() {
        val foreign = Track(id = "imported", name = "Elsewhere", startTimeMs = 1L)

        val decoded = ProtoBuf.Default.decodeFromByteArray(
            Track.serializer(),
            ProtoBuf.Default.encodeToByteArray(Track.serializer(), foreign)
        )

        assertFalse(decoded.trace)
    }
}
