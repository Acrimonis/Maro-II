package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.track.TrackFromCourse
import ykws.android.maro.spatial.SpatialOperations

/**
 * **The early save's partial-plan builder** — the provisional points become a plan dated by the
 * caller's instant and anchored by the acquisition's start, **with no time**: a draft that never sees a
 * plan reports no time rather than a paced guess, and the landing overwrites it with the plan's own.
 */
class PartialPlanOfTest {

    private val a = RoutePoint(43.5000, 7.0000)
    private val b = RoutePoint(43.5100, 7.0000)
    private val c = RoutePoint(43.5200, 7.0000)

    @Test
    fun fewerThanTwoPointsReturnsNull() {
        assertNull(partialPlanOf(emptyList(), a, 1_000L))
        assertNull(partialPlanOf(listOf(a), a, 1_000L))
    }

    /**
     * **A draft that never sees a plan carries no offsets** (the plan's decision, 2026-10-05) — the
     * early save writes the drawn line at the cruise pace no longer: every leg is zero and the duration
     * is zero, so the stored track reports **no time** rather than a plausible wrong one. The geometry
     * still travels (distance, anchor, destination, date), and because the landing overwrites the same
     * id with the plan's own times, **both orders of that race** — save-then-plan-lands and
     * plan-lands-then-save — end on the plan's track.
     */
    @Test
    fun aDraftWithNoPlanCarriesNoOffsetsAndConvergesOnTheLandedPlan() {
        val draft = partialPlanOf(listOf(a, b, c), null, 1_234L)!!

        val ab = SpatialOperations.haversine(LatLng(a.latitude, a.longitude), LatLng(b.latitude, b.longitude))
        val bc = SpatialOperations.haversine(LatLng(b.latitude, b.longitude), LatLng(c.latitude, c.longitude))

        assertEquals("the anchor falls back to the line's first point", a, draft.start)
        assertEquals("the destination is the last drawn point", c, draft.destination)
        assertEquals("one leg per segment", 2, draft.legTimesSec.size)
        assertEquals("the distance is the segments' sum", ab + bc, draft.distanceM, 0.001)
        assertEquals("and every leg carries no time", listOf(0.0, 0.0), draft.legTimesSec)
        assertEquals("so the duration is zero, never a paced guess", 0.0, draft.durationSec, 1e-9)
        assertEquals("the plan is dated by the caller's instant", 1_234L, draft.computedAtMs)

        // Both orders of the early-save race converge: the draft's own stored track reports no time,
        // and the landing overwrites that same id with the plan's own duration.
        val draftTrack = TrackFromCourse.build(
            start = a,
            legs = draft.points.drop(1).mapIndexed { i, pt ->
                TrackFromCourse.legBetween(draft.points[i], pt, draft.legTimesSec[i])
            },
            id = "draft-1",
            createdAtMs = draft.computedAtMs
        )
        assertEquals("the draft's own stored track reports no time", 0L, draftTrack.navigatingDurationSec)

        val planLegs = listOf(120.0, 240.0)
        val landedTrack = TrackFromCourse.build(
            start = a,
            legs = draft.points.drop(1).mapIndexed { i, pt ->
                TrackFromCourse.legBetween(draft.points[i], pt, planLegs[i])
            },
            id = "draft-1",
            createdAtMs = draft.computedAtMs
        )
        assertEquals(
            "and the landed save at the same id carries the plan's duration",
            360L,
            landedTrack.navigatingDurationSec
        )
    }

    @Test
    fun usesTheAnchorWhenOneIsGiven() {
        val plan = partialPlanOf(listOf(a, b), c, 0L)!!
        assertEquals(c, plan.start)
        assertEquals(b, plan.destination)
    }
}
