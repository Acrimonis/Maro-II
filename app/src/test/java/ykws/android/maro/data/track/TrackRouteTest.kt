package ykws.android.maro.data.track

import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.ListFilter
import ykws.android.maro.data.model.matchesFilter

/**
 * The route flag's own behaviours, read where they live on the summary: the filter axis both ways with
 * the live recording exempt (R31), the Resume refusal the summary owns (R41), the merge candidacy that
 * drops a route (R41), and the blob the exporter writes carrying the flag and a foreign track not
 * (R29, R30).
 */
class TrackRouteTest {

    private fun summary(
        id: String = "t1",
        route: Boolean = false,
        pinned: Boolean = false,
        live: Boolean = false,
        endTimeMs: Long? = 1_000L
    ) = TrackSummary(
        id = id,
        name = id,
        startTimeMs = 0L,
        endTimeMs = endTimeMs,
        pinned = pinned,
        route = route
    ).also { it.isLive = live }

    private fun axis(value: String) = ListFilter(mapOf("route" to value))

    @Test
    fun theAxisReadsBothSidesOfTheFlag() {
        assertTrue(summary(route = true).matchesFilter(axis("ROUTES"), 0L))
        assertFalse(summary(route = true).matchesFilter(axis("TRACKS"), 0L))
        assertTrue(summary(route = false).matchesFilter(axis("TRACKS"), 0L))
        assertFalse(summary(route = false).matchesFilter(axis("ROUTES"), 0L))
    }

    @Test
    fun allLeavesEverySummaryInWhateverItIs() {
        assertTrue(summary(route = true).matchesFilter(axis("ALL"), 0L))
        assertTrue(summary(route = false).matchesFilter(axis("ALL"), 0L))
    }

    @Test
    fun aLiveRecordingPassesWhateverTheAxisSays() {
        val live = summary(route = false, live = true)

        assertTrue(live.matchesFilter(axis("ROUTES"), 0L))
        assertTrue(live.matchesFilter(axis("TRACKS"), 0L))
    }

    @Test
    fun resumeIsRefusedForARouteAndAllowedForAFinishedRecording() {
        assertFalse(summary(route = true).resumeAllowed)
        assertTrue(summary(route = false).resumeAllowed)
    }

    @Test
    fun resumeStaysRefusedForAnUnfinishedRecording() {
        assertFalse(summary(route = false, endTimeMs = null).resumeAllowed)
    }

    @Test
    fun theMergeCandidacyDropsARoute() {
        val summaries = listOf(
            summary(id = "a"),
            summary(id = "b", route = true),
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
            route = true
        )

        val decoded = ProtoBuf.Default.decodeFromByteArray(
            Track.serializer(),
            ProtoBuf.Default.encodeToByteArray(Track.serializer(), track)
        )

        assertTrue(decoded.route)
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

        assertFalse(decoded.route)
    }
}
