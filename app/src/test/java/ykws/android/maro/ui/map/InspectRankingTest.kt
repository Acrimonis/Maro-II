package ykws.android.maro.ui.map

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.track.PointType
import ykws.android.maro.data.track.TrackPoint

/**
 * Unit tests for the inspect ranking (plan §2, §3, §8): the metric's points-and-lines rule and its
 * GAP awareness, the ordering and tie-break, viewport eligibility through a candidate's own cached
 * bbox, the true closest winning with no stickiness, clear-on-empty and the resume when a candidate
 * comes back, and the sweep pipeline's conflate-but-never-cancel behaviour.
 */
class InspectRankingTest {

    private fun pt(lat: Double, lon: Double, type: PointType = PointType.NORMAL) =
        TrackPoint(lat = lat, lon = lon, type = type)

    private val anchor = LatLng(0.0, 0.0)

    /** A viewport, i.e. the projection's own visible bounds. */
    private fun viewport(north: Double, east: Double, south: Double, west: Double) =
        InspectBounds(north, east, south, west)

    /** The whole world, for the tests whose subject is not eligibility. */
    private val everywhere = viewport(90.0, 180.0, -90.0, -180.0)

    // 0.001° of latitude is ~111.2 m with the app's haversine radius.
    private val nearTrack = InspectCandidate.line(
        id = "T1",
        kind = InspectKind.TRACK,
        points = listOf(pt(-0.01, 0.001), pt(0.01, 0.001))
    )
    private val farMarker = InspectCandidate.point("M1", InspectKind.MARKER, LatLng(0.02, 0.0))

    // ── Metric: points and lines only, never inside-ness ─────────────────────

    @Test
    fun `a marker measures to its own point`() {
        val d = InspectRanking.distance(anchor, InspectCandidate.point("M", InspectKind.MARKER, LatLng(0.001, 0.0)))
        assertTrue("expected ~111 m, got $d", d > 100.0 && d < 120.0)
    }

    @Test
    fun `a corridor measures to its centre line and never reports inside-ness`() {
        // The anchor sits between the corridor's two ends, i.e. inside its band: the band width is
        // ignored, so the answer is the centre line's own distance and never 0.
        val corridor = InspectCandidate.line(
            "C1",
            InspectKind.MARKER,
            listOf(pt(-0.001, -0.01), pt(-0.001, 0.01))
        )
        val d = InspectRanking.distance(anchor, corridor)
        assertTrue("expected the centre line's distance, got $d", d > 50.0 && d < 200.0)
    }

    @Test
    fun `a track measures to its nearest segment, not to its vertices`() {
        val d = InspectRanking.distance(anchor, nearTrack)
        // Both vertices are ~1.1 km away; the line itself passes ~111 m from the anchor.
        assertTrue("expected the segment's own distance, got $d", d > 100.0 && d < 120.0)
    }

    @Test
    fun `a gap seam is not measured across`() {
        val acrossGap = InspectCandidate.line(
            id = "T2",
            kind = InspectKind.TRACK,
            points = listOf(pt(-0.01, 0.001), pt(0.01, 0.001, PointType.GAP))
        )
        val d = InspectRanking.distance(anchor, acrossGap)
        // The seam would read ~111 m; the nearest real vertex is ~1.1 km away.
        assertTrue("the seam must not be measured, got $d", d > 1000.0)
    }

    @Test
    fun `a single point track measures to that point`() {
        val single = InspectCandidate.line("T3", InspectKind.TRACK, listOf(pt(0.001, 0.0)))
        val d = InspectRanking.distance(anchor, single)
        assertTrue("expected ~111 m, got $d", d > 100.0 && d < 120.0)
    }

    // ── Ordering, tie-break, ladder ──────────────────────────────────────────

    @Test
    fun `the ladder orders by distance then markers before tracks then id`() {
        val candidates = listOf(
            InspectCandidate.line("T9", InspectKind.TRACK, listOf(pt(0.003, 0.0), pt(0.003, 0.001))),
            InspectCandidate.point("M9", InspectKind.MARKER, LatLng(0.001, 0.0)),
            InspectCandidate.point("M1", InspectKind.MARKER, LatLng(0.001, 0.0)),
            InspectCandidate.line("T1", InspectKind.TRACK, listOf(pt(0.002, 0.0), pt(0.002, 0.001)))
        )
        val ladder = InspectRanking.rank(anchor, candidates)
        assertEquals(listOf("M1", "M9", "T1", "T9"), ladder.map { it.id })
        assertEquals(InspectKind.MARKER, ladder[0].kind)
    }

    @Test
    fun `the gold is always the ladder's first entry`() {
        val candidates = listOf(farMarker, nearTrack, InspectCandidate.point("M2", InspectKind.MARKER, LatLng(0.0005, 0.0)))
        val nearest = InspectRanking.nearest(anchor, candidates, everywhere)
        val ladder = InspectRanking.rank(anchor, candidates)
        assertEquals(ladder.first(), nearest)
    }

    @Test
    fun `the ladder is reproducible against a frozen anchor`() {
        val candidates = listOf(nearTrack, farMarker)
        val first = InspectRanking.rank(anchor, candidates)
        val second = InspectRanking.rank(anchor, candidates)
        assertEquals(first, second)
        assertEquals("T1", first.first().id)
    }

    // ── The true closest: no incumbent, no margin ─────────────────────────────

    @Test
    fun `the true closest wins on every re-rank with no stickiness`() {
        val candidates = listOf(nearTrack, farMarker)
        assertEquals("T1", InspectRanking.nearest(anchor, candidates, everywhere)?.id)
        // Re-ranked from scratch: the answer is the geometry's, never an incumbent's.
        assertEquals("T1", InspectRanking.nearest(anchor, candidates, everywhere)?.id)
        // With the anchor over the marker's own side the marker takes the gold at once, whatever the
        // previous highlight was.
        assertEquals("M1", InspectRanking.nearest(LatLng(0.02, 0.0), candidates, everywhere)?.id)
    }

    @Test
    fun `a hair closer is enough — no margin has to be overcome`() {
        // ~110.1 m against the track's ~111.2 m: less than one per cent better, and still the winner.
        val barelyCloser = InspectCandidate.point("M1", InspectKind.MARKER, LatLng(0.00099, 0.0))
        assertEquals("M1", InspectRanking.nearest(anchor, listOf(nearTrack, barelyCloser), everywhere)?.id)
    }

    // ── Eligibility is the viewport ──────────────────────────────────────────

    @Test
    fun `a point is eligible through its own position`() {
        val marker = InspectCandidate.point("M", InspectKind.MARKER, LatLng(0.001, 0.0))
        // A viewport holding the point ...
        assertEquals("M", InspectRanking.nearest(anchor, listOf(marker), viewport(0.01, 0.01, 0.0, -0.01))?.id)
        // ... and one that stops just short of it, which is an honest empty screen rather than an
        // item the user cannot see.
        assertNull(InspectRanking.nearest(anchor, listOf(marker), viewport(0.0005, 0.01, -0.01, -0.01)))
    }

    @Test
    fun `a line is eligible when its box overlaps although no vertex is on screen`() {
        // A line running west to east through the anchor's own latitude: both of its vertices sit at
        // lon ±0.01, the viewport holds only the middle, and the cached box is what finds it.
        val acrossTheScreen = InspectCandidate.line(
            "T4",
            InspectKind.TRACK,
            listOf(pt(0.0, -0.01), pt(0.0, 0.01))
        )
        val middle = viewport(0.0005, 0.0005, -0.0005, -0.0005)
        assertEquals("T4", InspectRanking.nearest(anchor, listOf(acrossTheScreen), middle)?.id)
        // A viewport that stands clear of the line's own box — north of it, spanning the same
        // longitudes — is still empty however near the anchor sees the line.
        assertNull(
            InspectRanking.nearest(anchor, listOf(acrossTheScreen), viewport(0.002, 0.01, 0.001, -0.01))
        )
    }

    @Test
    fun `a nearer item off screen loses to a farther one on screen`() {
        // The marker is twice as close to the anchor, but it is off the viewport while the track is
        // on it: overlap first, distance second (plan §2).
        val onScreen = viewport(0.01, 0.01, -0.01, -0.01)
        val justOutside = InspectCandidate.point("M2", InspectKind.MARKER, LatLng(0.012, 0.0))
        assertEquals(
            "T1",
            InspectRanking.nearest(anchor, listOf(nearTrack, justOutside), onScreen)?.id
        )
    }

    @Test
    fun `an empty viewport clears the highlight and the ranking resumes when it comes back`() {
        val candidates = listOf(nearTrack)
        val onScreen = viewport(0.05, 0.05, -0.05, -0.05)
        val elsewhere = viewport(40.0, 40.0, 39.0, 39.0)
        assertEquals("T1", InspectRanking.nearest(anchor, candidates, onScreen)?.id)
        // Nothing overlaps: the highlight clears rather than naming an item off screen.
        assertNull(InspectRanking.nearest(anchor, candidates, elsewhere))
        // And the same candidate comes back the moment the viewport holds it again.
        assertEquals("T1", InspectRanking.nearest(anchor, candidates, onScreen)?.id)
    }

    // ── The sweep pipeline: conflate stale ticks, never cancel a scan ─────────

    @Test
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    fun `a scan in flight is never cancelled and its result is never dropped`() = runTest {
        val ticks = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val started = mutableListOf<Int>()
        val finished = mutableListOf<Int>()
        val published = mutableListOf<Int>()
        val job = launch {
            inspectSweep(ticks) { tick ->
                started += tick
                delay(100L)
                finished += tick
                tick
            }.collect { published += it }
        }
        runCurrent()
        ticks.emit(1)
        runCurrent()
        assertEquals("the first scan must be in flight", listOf(1), started)
        // Two newer ticks arrive while it is still running: they must be conflated into one scan, and
        // the running one must be left alone — `collect`, never `collectLatest`.
        ticks.emit(2)
        ticks.emit(3)
        runCurrent()
        advanceUntilIdle()
        assertEquals("the in-flight scan finished, and the newest tick was scanned next", listOf(1, 3), finished)
        assertEquals("every result is published, none dropped for a newer tick", listOf(1, 3), published)
        job.cancel()
    }

    @Test
    fun `an empty scan publishes its own null and the highlight returns on the next tick`() = runTest {
        val results = ArrayDeque<InspectRank?>(listOf(null, InspectRank("T1", InspectKind.TRACK, distanceM = 12.0)))
        val ticks = flow {
            emit(1)
            delay(10L)
            emit(2)
        }
        val published = mutableListOf<InspectRank?>()
        inspectSweep(ticks) {
            results.removeFirst()
        }.collect { published += it }
        // The empty viewport's own result is published — the gold clears — and the map's next motion
        // publishes the candidate that came back.
        assertEquals(listOf(null, InspectRank("T1", InspectKind.TRACK, distanceM = 12.0)), published)
    }
}
