package ykws.android.maro.ui.map

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.track.PointType
import ykws.android.maro.data.track.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the inspect ranking (plan §8): the metric's points-and-lines rule and its GAP
 * awareness, the ordering and tie-break, the hysteresis boundary, clear-on-empty, the movement gate
 * that keeps arming or a nudge from picking, the radius derivation with both clamps, and the
 * dp → metres conversion.
 */
class InspectRankingTest {

    private fun pt(lat: Double, lon: Double, type: PointType = PointType.NORMAL) =
        TrackPoint(lat = lat, lon = lon, type = type)

    private val anchor = LatLng(0.0, 0.0)

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
        val nearest = InspectRanking.nearest(anchor, candidates, radiusM = 5_000.0)
        val ladder = InspectRanking.rank(anchor, candidates)
        assertEquals(ladder.first(), nearest)
    }

    @Test
    fun `nothing in range clears the candidate`() {
        assertNull(InspectRanking.nearest(anchor, listOf(farMarker), radiusM = 100.0))
        assertNull(InspectRanking.nearest(anchor, emptyList(), radiusM = 10_000.0))
    }

    @Test
    fun `the ladder is reproducible against a frozen anchor`() {
        val candidates = listOf(nearTrack, farMarker)
        val first = InspectRanking.rank(anchor, candidates)
        val second = InspectRanking.rank(anchor, candidates)
        assertEquals(first, second)
        assertEquals("T1", first.first().id)
    }

    // ── Hysteresis ───────────────────────────────────────────────────────────

    @Test
    fun `the candidate moves only when the challenger is closer by the margin`() {
        val incumbent = InspectRank("T1", InspectKind.TRACK, distanceM = 100.0)
        val tenPctCloser = InspectRank("T2", InspectKind.TRACK, distanceM = 90.0)
        val twentyPctCloser = InspectRank("T3", InspectKind.TRACK, distanceM = 80.0)

        assertEquals(incumbent, InspectRanking.winner(incumbent, tenPctCloser, radiusM = 200.0, hysteresisPct = 15f))
        assertEquals(twentyPctCloser, InspectRanking.winner(incumbent, twentyPctCloser, radiusM = 200.0, hysteresisPct = 15f))
    }

    @Test
    fun `an incumbent that has left the radius loses the gold`() {
        val incumbent = InspectRank("T1", InspectKind.TRACK, distanceM = 150.0)
        val challenger = InspectRank("T2", InspectKind.TRACK, distanceM = 140.0)
        assertEquals(challenger, InspectRanking.winner(incumbent, challenger, radiusM = 100.0, hysteresisPct = 15f))
    }

    @Test
    fun `a null challenger clears the gold and a null incumbent takes one`() {
        val incumbent = InspectRank("T1", InspectKind.TRACK, distanceM = 50.0)
        assertNull(InspectRanking.winner(incumbent, null, radiusM = 100.0, hysteresisPct = 15f))

        val challenger = InspectRank("T2", InspectKind.TRACK, distanceM = 60.0)
        assertEquals(challenger, InspectRanking.winner(null, challenger, radiusM = 100.0, hysteresisPct = 15f))
    }

    @Test
    fun `the same item keeps its slot whatever the margin`() {
        val incumbent = InspectRank("T1", InspectKind.TRACK, distanceM = 100.0)
        assertEquals(incumbent, InspectRanking.winner(incumbent, incumbent, radiusM = 50.0, hysteresisPct = 15f))
    }

    // ── Radius derivation: factor × zoom × shrink, both clamps ───────────────

    @Test
    fun `the radius is factor times the boat icon at the reference zoom`() {
        val r = inspectRadiusDp(
            zoomLevel = 12.0, distMultiplier = 1f, viewportMinDp = 800f,
            factor = 1.5f, minDp = 44f, maxViewportPct = 0.35f
        )
        // 32 dp boat × 1.5 = 48 dp, above the 44 dp floor and under the 280 dp ceiling.
        assertEquals(48f, r, 0.01f)
    }

    @Test
    fun `the floor keeps the ring finger-sized where the icon is tiny`() {
        val r = inspectRadiusDp(
            zoomLevel = 8.0, distMultiplier = 1f, viewportMinDp = 800f,
            factor = 1.5f, minDp = 44f, maxViewportPct = 0.35f
        )
        assertEquals(44f, r, 0.01f)
    }

    @Test
    fun `the ceiling stops the ring exceeding the viewport where the icon is large`() {
        val r = inspectRadiusDp(
            zoomLevel = 18.0, distMultiplier = 1f, viewportMinDp = 800f,
            factor = 1.5f, minDp = 44f, maxViewportPct = 0.35f
        )
        // 32 × 2^2.7 × 1.5 ≈ 311.9 dp, capped at 0.35 × 800 = 280 dp.
        assertEquals(280f, r, 0.01f)
    }

    @Test
    fun `the shore multiplier shrinks the ring without reaching either clamp`() {
        val ring = inspectRadiusDp(
            zoomLevel = 18.0, distMultiplier = 0.3f, viewportMinDp = 800f,
            factor = 1.5f, minDp = 44f, maxViewportPct = 0.35f
        )
        // 207.9 dp icon × 1.5 × 0.3 ≈ 93.6 dp — under the ceiling, above the floor.
        assertEquals(93.6f, ring, 0.2f)
    }

    @Test
    fun `the shore multiplier ramps from the coast to full size`() {
        assertEquals(1.0f, inspectDistMultiplier(null), 0.0001f)
        assertEquals(0.3f, inspectDistMultiplier(0.0), 0.0001f)
        assertEquals(1.0f, inspectDistMultiplier(2_000.0), 0.0001f)
        assertEquals(1.0f, inspectDistMultiplier(50_000.0), 0.0001f)
        assertEquals(0.65f, inspectDistMultiplier(1_000.0), 0.001f)
    }

    // ── dp → metres ──────────────────────────────────────────────────────────

    @Test
    fun `the dp radius converts to metres through the ground resolution`() {
        // 38.22 m/px at z12 on the equator × 3 px/dp × 48 dp ≈ 5 503 m.
        val metres = inspectRadiusMeters(radiusDp = 48f, pxPerDp = 3f, anchorLat = 0.0, zoomLevel = 12.0)
        assertEquals(5_503.0, metres, 5.0)
    }

    @Test
    fun `a coarser zoom covers more ground per dp`() {
        val fine = inspectRadiusMeters(48f, 3f, 0.0, 14.0)
        val coarse = inspectRadiusMeters(48f, 3f, 0.0, 11.0)
        assertTrue("coarser zoom must cover more ground", coarse > fine)
        assertEquals(fine * 2.0, inspectRadiusMeters(48f, 3f, 0.0, 13.0), 1.0)
    }

    // ── Movement gate: the clock waits for a gesture that actually moved the map ─

    @Test
    fun `a nudge under the threshold keeps the gate shut`() {
        // 24 dp at density 2 is 48 px; this nudge covers 47.
        assertFalse(inspectArmGateOpen(100f, 100f, 147f, 100f, density = 2f, minMoveDp = 24f))
    }

    @Test
    fun `a drag past the threshold opens the gate`() {
        assertTrue(inspectArmGateOpen(100f, 100f, 100f, 149f, density = 2f, minMoveDp = 24f))
        // Exactly at the threshold counts: the key is a minimum, not a strict inequality.
        assertTrue(inspectArmGateOpen(0f, 0f, 48f, 0f, density = 2f, minMoveDp = 24f))
    }

    @Test
    fun `a touch that never moves the map leaves the gate shut`() {
        // A resting finger — or the arming seed's own tick — projects the anchor exactly where it was.
        assertFalse(inspectArmGateOpen(100f, 220f, 100f, 220f, density = 3f, minMoveDp = 24f))
    }

    @Test
    fun `a pure pinch counts through the anchor's own travel`() {
        // The zoom carries the anchor's point 60 px across the screen with no pan at all.
        assertTrue(inspectArmGateOpen(200f, 300f, 200f, 360f, density = 2f, minMoveDp = 24f))
    }

    @Test
    fun `the threshold is read in dp, not pixels`() {
        // The same 40 px is 40 dp on a 1x screen and only 20 dp on a 2x one.
        assertTrue(inspectArmGateOpen(0f, 0f, 40f, 0f, density = 1f, minMoveDp = 24f))
        assertFalse(inspectArmGateOpen(0f, 0f, 40f, 0f, density = 2f, minMoveDp = 24f))
    }

    @Test
    fun `a diagonal drag measures its true length`() {
        // 20 px across and 20 px up spans 28.3 px — past 24 dp at density 1, while either axis alone
        // falls short.
        assertTrue(inspectArmGateOpen(0f, 0f, 20f, 20f, density = 1f, minMoveDp = 24f))
        assertFalse(inspectArmGateOpen(0f, 0f, 20f, 0f, density = 1f, minMoveDp = 24f))
    }
}
