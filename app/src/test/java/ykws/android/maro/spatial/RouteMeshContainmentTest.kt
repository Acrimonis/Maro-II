package ykws.android.maro.spatial

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.RouteMeshArrays
import ykws.android.maro.spatial.mesh.RouteMeshContainment
import kotlin.math.PI
import kotlin.math.cos

/**
 * The mesh's own point query, over a synthetic square of water a kilometre on a side — two triangles
 * over four nodes, so the shared diagonal, the bucket grid's own seams and the empty-mesh case are
 * all reachable without a corner of real coastline.
 */
class RouteMeshContainmentTest {

    private fun metresPerDegLon(): Double = METRES_PER_DEG_LAT * cos(Math.toRadians(ORIGIN_LAT))
    private fun latOf(yM: Double): Double = ORIGIN_LAT + yM / METRES_PER_DEG_LAT
    private fun lonOf(xM: Double): Double = ORIGIN_LON + xM / metresPerDegLon()

    /**
     * The square's four corners as nodes 0-3 — (0,0), (1 km,0), (1 km,1 km), (0,1 km) — and, when
     * [withTriangles], the two kept triangles over them. No edge is needed: the query reads the
     * triangles alone.
     */
    private fun squareMesh(withTriangles: Boolean = true): RouteMeshArrays {
        val lat = doubleArrayOf(latOf(0.0), latOf(0.0), latOf(1_000.0), latOf(1_000.0))
        val lon = doubleArrayOf(lonOf(0.0), lonOf(1_000.0), lonOf(1_000.0), lonOf(0.0))
        val n = lat.size
        return RouteMeshArrays(
            regionId = "test",
            box = BoundingBox(
                latSouth = latOf(-500.0),
                latNorth = latOf(1_500.0),
                lonWest = lonOf(-500.0),
                lonEast = lonOf(1_500.0)
            ),
            pointsLat = lat,
            pointsLon = lon,
            nodeComponent = IntArray(n),
            edgeFrom = IntArray(0),
            edgeTo = IntArray(0),
            edgeLengthM = FloatArray(0),
            edgeInBand = BooleanArray(0),
            nodeOffsets = IntArray(n + 1),
            nodeEdges = IntArray(0),
            triA = if (withTriangles) intArrayOf(0, 0) else IntArray(0),
            triB = if (withTriangles) intArrayOf(1, 2) else IntArray(0),
            triC = if (withTriangles) intArrayOf(2, 3) else IntArray(0)
        )
    }

    @Test
    fun aPointInsideTheKeptTrianglesIsInsideTheMesh() {
        val containment = RouteMeshContainment(squareMesh())

        assertTrue(containment.contains(latOf(500.0), lonOf(500.0)))
        assertTrue(containment.contains(latOf(100.0), lonOf(900.0)))
        assertTrue(containment.contains(latOf(900.0), lonOf(100.0)))
    }

    @Test
    fun aPointOutsideEveryTriangleIsNotInsideTheMesh() {
        val containment = RouteMeshContainment(squareMesh())

        // Inside the box, outside the square: the grid must answer from the triangles, not the box.
        assertFalse(containment.contains(latOf(500.0), lonOf(-200.0)))
        assertFalse(containment.contains(latOf(1_200.0), lonOf(500.0)))
        // And outside the grid's own extent, so the bucket lookup is never even reached.
        assertFalse(containment.contains(latOf(-400.0), lonOf(500.0)))
    }

    @Test
    fun anEdgeAndAVertexReadAsInsideBecauseTheyAreKeptWater() {
        val containment = RouteMeshContainment(squareMesh())

        // On the shared diagonal of the two triangles.
        assertTrue(containment.contains(latOf(500.0), lonOf(500.0)))
        // Exactly on a vertex and exactly on the southern edge.
        assertTrue(containment.contains(latOf(0.0), lonOf(0.0)))
        assertTrue(containment.contains(latOf(0.0), lonOf(500.0)))
    }

    @Test
    fun aMeshWithoutTrianglesClaimsNoPointAtAll() {
        // The conservative reading, and the one that keeps a smoothing pass honest: a mesh that
        // shipped no triangles proves nothing, so nothing is inside it.
        val containment = RouteMeshContainment(squareMesh(withTriangles = false))

        assertFalse(containment.contains(latOf(500.0), lonOf(500.0)))
        assertFalse(containment.contains(latOf(0.0), lonOf(0.0)))
    }

    private companion object {
        const val ORIGIN_LAT = 43.5
        const val ORIGIN_LON = 7.0

        /** The fixture's own flat scale — the query only asks which side of an edge a point is on. */
        val METRES_PER_DEG_LAT: Double = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    }
}
