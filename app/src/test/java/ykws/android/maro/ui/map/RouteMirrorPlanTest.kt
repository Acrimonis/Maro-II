package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.track.Track
import ykws.android.maro.data.track.TrackFromCourse
import ykws.android.maro.data.track.TrackPoint
import ykws.android.maro.spatial.Units

/**
 * **The mirrored plan** (R86) — a stored route read back as the return trip by [`mirroredPlanOf`]: its
 * points run the other way and its per-leg times are the **stored ones reversed**, exactly the forward
 * inverse with its lists reversed rather than any pace arithmetic redone.
 *
 * The stored figures are what is pinned: the forward plan reads its leg times off the millisecond
 * offsets `TrackFromCourse.build` wrote, and mirroring hands those same seconds back in reverse order —
 * mirrored leg *k* is stored leg *n−1−k* — so no pace read and no haversine can drift.
 */
class RouteMirrorPlanTest {

    private val a = RoutePoint(43.5000, 7.0000)
    private val b = RoutePoint(43.5100, 7.0000)
    private val c = RoutePoint(43.5200, 7.0000)

    private val armingInstantMs = 42_424L

    /** The track a saved A→B→C route becomes — its vertices laid out as the save writes them. */
    private fun savedTrack(distanceNm: Float = 1.25f): Track = Track(
        id = "route-1",
        name = "Route 2026-09-30 12:00",
        startTimeMs = 1_000_000L,
        trackPoints = listOf(
            TrackPoint(lat = a.latitude, lon = a.longitude, speedMps = 2f, timeOffsetMs = 0L),
            TrackPoint(lat = b.latitude, lon = b.longitude, speedMps = 2f, timeOffsetMs = 500_000L),
            TrackPoint(lat = c.latitude, lon = c.longitude, speedMps = 4f, timeOffsetMs = 900_000L)
        ),
        distanceNm = distanceNm,
        navigatingDurationSec = 900L,
        route = true,
        routeStartMarkerId = "m-start",
        routeDestinationMarkerId = "m-dest"
    )

    /** The line runs the other way: the stored destination starts it and the stored start ends it. */
    @Test
    fun theLineIsTheStoredOneReversed() {
        val plan = mirroredPlanOf(savedTrack(), armingInstantMs) ?: error("a mirrored plan stands")

        assertEquals("the vertices run backwards", listOf(c, b, a), plan.points)
        assertEquals("the start is the stored destination", c, plan.start)
        assertEquals("and the destination the stored start", a, plan.destination)
    }

    /** The times are the stored leg times reversed — leg *k* is stored leg *n−1−k*, the exact seconds. */
    @Test
    fun theTimesAreTheStoredLegsReversed() {
        val plan = mirroredPlanOf(savedTrack(), armingInstantMs) ?: error("a mirrored plan stands")

        // The stored offsets are 0 / 500 000 / 900 000 ms: legs of 500 s and 400 s, read back reversed.
        assertEquals(listOf(400.0, 500.0), plan.legTimesSec)
        assertEquals("the duration is the mirrored legs' own sum", plan.legTimesSec.sum(), plan.durationSec, 1e-9)
    }

    /**
     * The end-to-end tie between the two halves: a route track built by the save, mirrored back. The
     * save's own offsets are the only producer of the figures the mirror reverses, so this pins the
     * convention rather than hand-laying either side — the gap that hand-laid paces left.
     */
    @Test
    fun theSavesOwnTrackMirrorsToItsLegsReversed() {
        val p0 = RoutePoint(43.6000, 7.1000)
        val p1 = RoutePoint(43.6100, 7.1000)
        val p2 = RoutePoint(43.6200, 7.1000)
        val track = TrackFromCourse.build(
            start = p0,
            legs = listOf(
                TrackFromCourse.legBetween(p0, p1, durationSec = 120.0),
                TrackFromCourse.legBetween(p1, p2, durationSec = 240.0)
            ),
            id = "course-1",
            createdAtMs = 1_000_000L
        )

        val plan = mirroredPlanOf(track, armingInstantMs) ?: error("a mirrored plan stands")

        assertEquals("the points are the stored ones reversed", listOf(p2, p1, p0), plan.points)
        assertEquals("and the leg times the stored ones reversed", listOf(240.0, 120.0), plan.legTimesSec)
    }

    /** The distance is the track's own figure, read exactly as `routePlanOf` reads it. */
    @Test
    fun theDistanceIsTheTracksOwn() {
        val plan = mirroredPlanOf(savedTrack(distanceNm = 1.25f), armingInstantMs) ?: error("a mirrored plan stands")

        assertEquals(
            "the track's own nautical miles, as metres",
            Units.nauticalMilesToMetres(1.25),
            plan.distanceM,
            1e-9
        )
    }

    /** The return trip is a new plan: dated the arming instant, with no badge and no moved destination. */
    @Test
    fun thePlanIsDatedTheArmingInstantAndInventsNothingElse() {
        val plan = mirroredPlanOf(savedTrack(), armingInstantMs) ?: error("a mirrored plan stands")

        assertEquals("its own day, not the stored start", armingInstantMs, plan.computedAtMs)
        assertFalse("the destination did not move", plan.destinationMoved)
        assertTrue("no crossing badge rides a mirrored line", plan.forcedCrossingZoneNames.isEmpty())
    }

    /** Under two points there is no line to mirror, exactly as in the shipped inverse. */
    @Test
    fun fewerThanTwoPointsIsNothing() {
        val single = savedTrack().copy(trackPoints = savedTrack().trackPoints.take(1))

        assertNull(mirroredPlanOf(single, armingInstantMs))
        assertNull(mirroredPlanOf(savedTrack().copy(trackPoints = emptyList()), armingInstantMs))
    }
}
