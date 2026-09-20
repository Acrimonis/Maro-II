package ykws.android.maro.data.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.spatial.Units

/**
 * Saving a course: the six figures come from the plan's own numbers, and the track they land in is an
 * ordinary one — which is the whole reason the save has no second reader downstream.
 *
 * The name's exact value is deliberately not asserted: it is a local-time format, so a fixed
 * expectation would pass or fail by the machine's timezone rather than by the code. What is asserted
 * is the shape the Tracks feature's auto-name has.
 */
class TrackFromCourseTest {

    private val t0 = 1_700_000_000_000L
    private val p0 = RoutePoint(43.5000, 7.0000)
    private val p1 = RoutePoint(43.5100, 7.0000)
    private val p2 = RoutePoint(43.5200, 7.0000)

    private fun legs() = listOf(
        TrackFromCourse.legBetween(p0, p1, durationSec = 120.0),
        TrackFromCourse.legBetween(p1, p2, durationSec = 240.0)
    )

    private fun build(pinned: Boolean = false) = TrackFromCourse.build(
        start = p0,
        legs = legs(),
        pinned = pinned,
        id = "course-1",
        createdAtMs = t0
    )

    @Test
    fun theVerticesCarryThePlansPaceAndItsCumulativeTime() {
        val track = build()

        assertEquals(3, track.trackPoints.size)
        assertEquals(listOf(0L, 120_000L, 360_000L), track.trackPoints.map { it.timeOffsetMs })
        // Every vertex wears the pace of the leg leaving it, and the destination the last leg's.
        assertEquals(track.trackPoints[0].speedMps!!, track.trackPoints[1].speedMps!!, 1e-4f)
        assertTrue(track.trackPoints[2].speedMps!! > 0f)
        assertTrue(track.trackPoints.all { it.type == PointType.NORMAL })
    }

    @Test
    fun distanceAndDurationComeOutOfThePlan() {
        val track = build()
        val legs = legs()

        assertEquals(
            legs.sumOf { it.distanceM },
            Units.nauticalMilesToMetres(track.distanceNm.toDouble()),
            0.5
        )
        assertEquals(360L, track.navigatingDurationSec)
        assertEquals(t0 + 360_000L, track.lastPointTimeMs)
        assertEquals(t0, track.startTimeMs)
        assertEquals(t0 + 360_000L, track.endTimeMs)
    }

    @Test
    fun theFlagIsWhatMarksItPlannedAndThePinIsWhatWasOffered() {
        assertTrue(build().plannedCourse)
        assertTrue(build(pinned = true).pinned)
        assertFalse(build().pinned)
    }

    @Test
    fun theDerivedStatsAgreeWithThePlansOwnFigures() {
        val track = build()
        val derived = track.withDerivedStats()

        // The derived path is a second *reader* of these figures, never a second arithmetic: it must
        // land on the same distance and the same duration, and it must find no idle anywhere.
        assertEquals(track.distanceNm.toDouble(), derived.distanceNm.toDouble(), 0.02)
        assertEquals(track.navigatingDurationSec, derived.navigatingDurationSec)
        assertEquals(0L, derived.idleDurationSec)
    }

    @Test
    fun aCourseWithNoLegsIsAnEmptyTrackRatherThanAFailure() {
        val track = TrackFromCourse.build(start = p0, legs = emptyList(), createdAtMs = t0)

        assertTrue(track.trackPoints.isEmpty())
        assertEquals(0L, track.navigatingDurationSec)
        assertEquals(0f, track.distanceNm, 1e-6f)
    }

    @Test
    fun theSavedTrackWearsTheStandardAutoNameShape() {
        assertTrue(
            Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""").matches(TrackFromCourse.build(
                start = p0,
                legs = legs(),
                createdAtMs = t0
            ).name)
        )
    }
}
