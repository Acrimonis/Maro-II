package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the GAP/dash split after the selection refactor: a line containing a break
 * must still yield solid runs plus a dashed bridge per gap. The pure segmentation is asserted here;
 * the actual osmdroid drawing stays in the Compose shell (device-verified).
 *
 * The points are the seam's own [RenderPoint]s, so the same contract holds for a recorded track and a
 * route alike — the split reads `isBreak`, whichever kind produced the line.
 */
class MapTrackSegmentsTest {

    private fun p(lat: Double, isBreak: Boolean = false) =
        RenderPoint(lat = lat, lon = 7.0, isBreak = isBreak)

    @Test
    fun gap_splitsIntoSolidRunsPlusDashedBridge() {
        val points = listOf(p(1.0), p(2.0), p(3.0), p(4.0, isBreak = true), p(5.0), p(6.0))
        val segments = splitTrackSegments(points)

        assertEquals(3, segments.size)
        assertEquals(listOf(0, 1, 2), segments[0].pointIndices)
        assertFalse(segments[0].dashed)
        assertEquals(listOf(3, 4), segments[1].pointIndices)
        assertTrue(segments[1].dashed)
        assertEquals(listOf(4, 5), segments[2].pointIndices)
        assertFalse(segments[2].dashed)
    }

    @Test
    fun gapAsLastPoint_emitsDashedBridgeToItself() {
        val segments = splitTrackSegments(listOf(p(1.0), p(2.0), p(3.0, isBreak = true)))

        assertEquals(2, segments.size)
        assertEquals(listOf(0, 1), segments[0].pointIndices)
        assertFalse(segments[0].dashed)
        assertEquals(listOf(2, 2), segments[1].pointIndices)
        assertTrue(segments[1].dashed)
    }

    @Test
    fun emptyAndSinglePoint_produceNoSegments() {
        assertTrue(splitTrackSegments(emptyList()).isEmpty())
        assertTrue(splitTrackSegments(listOf(p(1.0))).isEmpty())
    }

    @Test
    fun twoNormalPoints_produceSingleSolidSegment() {
        val segments = splitTrackSegments(listOf(p(1.0), p(2.0)))

        assertEquals(1, segments.size)
        assertEquals(listOf(0, 1), segments[0].pointIndices)
        assertFalse(segments[0].dashed)
    }
}
