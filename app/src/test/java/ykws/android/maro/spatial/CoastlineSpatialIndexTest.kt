package ykws.android.maro.spatial

import org.junit.Assert.*
import org.junit.Test
import ykws.android.maro.data.model.CoastlinePoint
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.LatLng
import kotlin.math.abs
import kotlin.math.sin

/**
 * Correctness tests for [CoastlineSpatialIndex.query].
 *
 * The key invariant: the grid query must return the SAME nearest-segment distance
 * as an exhaustive brute-force scan. A grid sweep over many positions guards against
 * the "occasional jumps" bug, where stopping at the first non-empty ring missed a
 * closer segment one ring further out.
 */
class CoastlineSpatialIndexTest {

    /** Wavy synthetic coastline (~2.6 km E–W), vertices a few tens of metres apart. */
    private val coast: CoastlineSegment = run {
        val pts = (0..200).map { k ->
            val t = k / 200.0
            CoastlinePoint(
                lat = (43.5 + 0.008 * sin(t * 6.0)).toFloat(),
                lon = (7.0 + 0.03 * t).toFloat()
            )
        }
        CoastlineSegment(osmWayId = 1L, points = pts, isMainland = true, isClosed = false)
    }

    /** Exhaustive nearest-distance over every segment — the ground truth. */
    private fun bruteForce(lat: Double, lon: Double): Double {
        val p = LatLng(lat, lon)
        var best = Double.MAX_VALUE
        val pts = coast.points
        for (i in 0 until pts.size - 1) {
            val a = LatLng(pts[i].lat.toDouble(), pts[i].lon.toDouble())
            val b = LatLng(pts[i + 1].lat.toDouble(), pts[i + 1].lon.toDouble())
            val d = SpatialOperations.pointToSegmentDistance(p, a, b)
            if (d < best) best = d
        }
        return best
    }

    @Test
    fun `query matches brute-force nearest across a grid (no jumps)`() {
        // Deliberately small cells to stress the ring-expansion / stop logic.
        val index = CoastlineSpatialIndex(listOf(coast), cellSizeM = 100.0)
        var checked = 0
        var maxErr = 0.0
        var lat = 43.488
        while (lat <= 43.512) {
            var lon = 6.997
            while (lon <= 7.033) {
                val expected = bruteForce(lat, lon)
                val actual = index.query(lat, lon).distanceMeters
                val err = abs(actual - expected)
                if (err > maxErr) maxErr = err
                assertTrue(
                    "mismatch at ($lat, $lon): index=$actual brute=$expected (err=$err)",
                    err <= 0.5
                )
                checked++
                lon += 0.0004
            }
            lat += 0.0004
        }
        assertTrue("expected a dense sweep, only checked $checked", checked > 2000)
    }

    @Test
    fun `query returns the closest segment's vertex info`() {
        val index = CoastlineSpatialIndex(listOf(coast), cellSizeM = 100.0)
        val r = index.query(43.5, 7.015)
        assertTrue(r.distanceMeters < Double.MAX_VALUE)
        assertTrue("polylineIdx populated", r.polylineIdx == 0)
        assertTrue("vertexIndex populated", r.vertexIndex in 0 until coast.points.size - 1)
        assertEquals(bruteForce(43.5, 7.015), r.distanceMeters, 0.5)
    }

    @Test
    fun `offshore point near the coast still resolves`() {
        val index = CoastlineSpatialIndex(listOf(coast), cellSizeM = 100.0)
        val r = index.query(43.486, 7.015)   // ~0.7 km south of the coast
        assertTrue(r.distanceMeters < Double.MAX_VALUE)
        assertEquals(bruteForce(43.486, 7.015), r.distanceMeters, 0.5)
    }

    @Test
    fun `empty index returns sentinel`() {
        val index = CoastlineSpatialIndex(emptyList())
        assertEquals(Double.MAX_VALUE, index.query(43.5, 7.0).distanceMeters, 0.0)
    }
}
