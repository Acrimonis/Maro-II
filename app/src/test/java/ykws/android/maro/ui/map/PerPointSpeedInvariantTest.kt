package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.track.Track
import ykws.android.maro.data.track.TrackFromCourse
import ykws.android.maro.data.track.TrackPoint
import ykws.android.maro.spatial.SpatialOperations

/**
 * **The per-point speed invariant** (2026-10-05) — one fixture over the writers and readers of per-point
 * speed: a planned vertex carries the speed of the leg **leaving** it, `legDistance ÷ legDuration`; the
 * destination, the one vertex no leg leaves, keeps the last leg's; a recorded track keeps its own GPS
 * speeds and is exempt; and a plan-less draft carries zero, read as *no speed* rather than a wrong one.
 *
 * The readers are checked together — the built track, [`routePlanOf`]'s read-back and [`mirroredPlanOf`]'s
 * reversed line — the same invariant on each, with no expectation elsewhere retargeted. Unequal legs
 * (0.01° then 0.02° of latitude) make each leg's own speed distinguishable, so a direction swap cannot hide.
 */
class PerPointSpeedInvariantTest {

    private val a = RoutePoint(43.5000, 7.0000)
    private val b = RoutePoint(43.5100, 7.0000)
    private val c = RoutePoint(43.5300, 7.0000)

    private val leg0Sec = 120.0
    private val leg1Sec = 300.0

    private fun builtTrack(): Track = TrackFromCourse.build(
        start = a,
        legs = listOf(
            TrackFromCourse.legBetween(a, b, durationSec = leg0Sec),
            TrackFromCourse.legBetween(b, c, durationSec = leg1Sec)
        ),
        id = "course-1",
        createdAtMs = 1_000_000L
    )

    private fun metres(from: RoutePoint, to: RoutePoint): Double =
        SpatialOperations.haversine(
            LatLng(from.latitude, from.longitude),
            LatLng(to.latitude, to.longitude)
        )

    private fun speed(legMetres: Double, legSec: Double): Double = legMetres / legSec

    /** The writer: a planned vertex wears the speed of its outgoing leg, the destination the last leg's. */
    @Test
    fun aPlannedVertexCarriesItsOutgoingLegsSpeed() {
        val track = builtTrack()
        val tp = track.trackPoints

        assertEquals(3, tp.size)
        assertEquals(
            "the start wears leg 0's own speed",
            speed(metres(a, b), leg0Sec),
            tp[0].speedMps!!.toDouble(),
            1e-4
        )
        assertEquals(
            "the middle vertex wears leg 1's speed — the leg leaving it, not the one arriving",
            speed(metres(b, c), leg1Sec),
            tp[1].speedMps!!.toDouble(),
            1e-4
        )
        assertEquals(
            "the destination, which no leg leaves, keeps the last leg's speed",
            tp[1].speedMps!!,
            tp[2].speedMps!!
        )
        assertNotEquals(
            "the two legs differ, so labelling a vertex with the arriving leg would show",
            tp[0].speedMps!!,
            tp[1].speedMps!!
        )
    }

    /** The read-back: [`routePlanOf`]'s own leg times reproduce the vertex speeds the save wrote. */
    @Test
    fun theReadBackReproducesTheVertexSpeeds() {
        val track = builtTrack()
        val plan = routePlanOf(track) ?: error("a saved route reads back into a plan")

        assertEquals("one leg per stored leg", 2, plan.legTimesSec.size)
        assertEquals("leg 0's time is the stored one", leg0Sec, plan.legTimesSec[0], 1e-9)
        assertEquals("leg 1's time is the stored one", leg1Sec, plan.legTimesSec[1], 1e-9)
        assertEquals(
            "leg 0's distance over its time is the speed the start vertex carries",
            track.trackPoints[0].speedMps!!.toDouble(),
            speed(metres(plan.points[0], plan.points[1]), plan.legTimesSec[0]),
            1e-4
        )
        assertEquals(
            "leg 1's distance over its time is the speed the middle vertex carries",
            track.trackPoints[1].speedMps!!.toDouble(),
            speed(metres(plan.points[1], plan.points[2]), plan.legTimesSec[1]),
            1e-4
        )
    }

    /** The mirrored plan: the invariant holds over the reversed line, mirrored leg k being stored leg n−1−k. */
    @Test
    fun theMirroredPlanHoldsTheInvariantOverTheReversedLine() {
        val track = builtTrack()
        val mirrored = mirroredPlanOf(track, nowMs = 42L) ?: error("a saved route mirrors")

        assertEquals("the line runs backwards", listOf(c, b, a), mirrored.points)
        val n = track.trackPoints.size - 1
        for (k in 0 until mirrored.legTimesSec.size) {
            val stored = n - 1 - k
            assertEquals(
                "mirrored leg $k reproduces stored leg $stored's vertex speed",
                track.trackPoints[stored].speedMps!!.toDouble(),
                speed(metres(mirrored.points[k], mirrored.points[k + 1]), mirrored.legTimesSec[k]),
                1e-4
            )
        }
    }

    /** A plan-less draft carries zero — read as *no speed* rather than a paced wrong one. */
    @Test
    fun aPlanLessDraftCarriesZeroSpeed() {
        val draft = partialPlanOf(listOf(a, b, c), null, 1_234L) ?: error("a draft stands")
        val draftTrack = TrackFromCourse.build(
            start = draft.points.first(),
            legs = draft.points.drop(1).mapIndexed { i, pt ->
                TrackFromCourse.legBetween(draft.points[i], pt, draft.legTimesSec[i])
            },
            id = "draft-1",
            createdAtMs = draft.computedAtMs
        )

        assertEquals("every leg carries no time", listOf(0.0, 0.0), draft.legTimesSec)
        assertTrue(
            "so every vertex carries zero — no speed, not a guess",
            draftTrack.trackPoints.all { it.speedMps == 0f }
        )
    }

    /** A recorded track keeps its own GPS speeds and is exempt: the readers only read its times. */
    @Test
    fun aRecordedTrackKeepsItsOwnGpsSpeeds() {
        val recorded = Track(
            id = "recorded-1",
            name = "Journey 2026-10-01 09:00",
            startTimeMs = 1_000_000L,
            trackPoints = listOf(
                TrackPoint(lat = a.latitude, lon = a.longitude, speedMps = 3.4f, timeOffsetMs = 0L),
                TrackPoint(lat = b.latitude, lon = b.longitude, speedMps = 1.1f, timeOffsetMs = 500_000L),
                TrackPoint(lat = c.latitude, lon = c.longitude, speedMps = 5.7f, timeOffsetMs = 900_000L)
            ),
            distanceNm = 1.5f,
            navigatingDurationSec = 900L
        )

        routePlanOf(recorded)
        mirroredPlanOf(recorded, 42L)

        assertEquals(
            "the GPS speeds survive verbatim — the leg arithmetic is not theirs",
            listOf(3.4f, 1.1f, 5.7f),
            recorded.trackPoints.map { it.speedMps }
        )
    }
}
