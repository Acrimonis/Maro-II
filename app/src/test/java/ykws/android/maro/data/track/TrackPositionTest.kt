package ykws.android.maro.data.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.ListFilter
import ykws.android.maro.data.model.RegionBounds
import ykws.android.maro.data.model.matchesFilter

/**
 * The position classifier's own rules and the filter's reading of them: the sample stride, the skipped
 * GAP markers, the region check, the unclassified sentinel, and the two exemptions the axis carries.
 *
 * The water test arrives as a function, so no coastline, index or device is involved.
 */
class TrackPositionTest {

    private val region = RegionBounds(latSouth = 43.0, latNorth = 44.0, lonWest = 7.0, lonEast = 8.0)

    private fun points(
        count: Int,
        gaps: Set<Int> = emptySet(),
        lat: Double = 43.5,
        lon: Double = 7.5
    ): List<TrackPoint> = (0 until count).map { i ->
        TrackPoint(
            lat = lat,
            lon = lon,
            timeOffsetMs = i * 1_000L,
            type = if (i in gaps) PointType.GAP else PointType.NORMAL
        )
    }

    private fun summary(
        water: Int = TrackPositionCounts.UNCLASSIFIED,
        land: Int = TrackPositionCounts.UNCLASSIFIED
    ) = TrackSummary(
        id = "t",
        name = "t",
        startTimeMs = 0L,
        waterPointCount = water,
        landPointCount = land
    )

    @Test
    fun `a track wholly on water is classified water`() {
        val counts = classifyTrackPosition(points(10), region, { _, _ -> true })

        assertTrue(counts.isClassified)
        assertEquals(10, counts.water)
        assertEquals(0, counts.land)
    }

    @Test
    fun `a track wholly on land is classified land`() {
        val counts = classifyTrackPosition(points(10), region, { _, _ -> false })

        assertEquals(0, counts.water)
        assertEquals(10, counts.land)
    }

    @Test
    fun `gap markers are skipped rather than counted or breaking the run`() {
        val sample = points(10, gaps = setOf(0, 3, 6, 9))

        val counts = classifyTrackPosition(sample, region, { _, _ -> true })

        assertEquals(6, counts.water)
        assertEquals(0, counts.land)
    }

    @Test
    fun `a track with nothing but gaps is unclassified`() {
        val counts = classifyTrackPosition(points(4, gaps = setOf(0, 1, 2, 3)), region, { _, _ -> true })

        assertFalse(counts.isClassified)
    }

    @Test
    fun `a track outside the region is unclassified rather than water`() {
        val counts = classifyTrackPosition(
            points(10, lat = 48.0, lon = 2.0),
            region,
            { _, _ -> true }
        )

        assertFalse(counts.isClassified)
    }

    @Test
    fun `a test that cannot answer leaves the track unclassified`() {
        val counts = classifyTrackPosition(points(10), region, { _, _ -> null })

        assertFalse(counts.isClassified)
    }

    @Test
    fun `the sample is bounded however long the track is`() {
        var asked = 0

        classifyTrackPosition(points(5_000), region, { _, _ -> asked++; true })

        assertEquals(POSITION_SAMPLE_COUNT, asked)
    }

    @Test
    fun `water wins the tie and an unclassified track counts as water`() {
        assertTrue(summary(water = 5, land = 5).positionIsWater)
        assertTrue(summary(water = 7, land = 3).positionIsWater)
        assertFalse(summary(water = 3, land = 7).positionIsWater)
        assertTrue(summary().positionIsWater)
    }

    @Test
    fun `the filter reads the counts, and the live track passes whatever they say`() {
        val onWater = ListFilter(mapOf("position" to "WATER"))
        val onLand = ListFilter(mapOf("position" to "LAND"))

        assertTrue(summary(water = 8, land = 2).matchesFilter(onWater, 0L))
        assertFalse(summary(water = 8, land = 2).matchesFilter(onLand, 0L))
        assertTrue(summary(water = 2, land = 8).matchesFilter(onLand, 0L))
        // Unclassified counts as water, which is where an out-of-region passage lands.
        assertTrue(summary().matchesFilter(onWater, 0L))
        assertFalse(summary().matchesFilter(onLand, 0L))

        val live = summary(water = 0, land = 9).apply { isLive = true }
        assertTrue(live.matchesFilter(onWater, 0L))
        assertTrue(live.matchesFilter(onLand, 0L))
    }
}
