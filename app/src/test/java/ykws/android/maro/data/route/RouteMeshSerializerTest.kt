package ykws.android.maro.data.route

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.RouteEdge
import ykws.android.maro.data.model.RouteMesh
import ykws.android.maro.data.model.RouteNode
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteTriangle

/**
 * The mesh must survive the wire unchanged: the builder writes the object shape, the app reads the
 * packed one, and every number has to come back identical — including the CSR grouping the search
 * walks.
 */
class RouteMeshSerializerTest {

    private val points = listOf(
        RoutePoint(43.5, 7.0),
        RoutePoint(43.5, 7.001),
        RoutePoint(43.501, 7.0)
    )

    private val mesh = RouteMesh(
        regionId = "nice-menton",
        box = BoundingBox(latSouth = 43.4, latNorth = 43.6, lonWest = 6.9, lonEast = 7.2),
        points = points,
        components = listOf(0, 0, 0),
        nodes = listOf(
            RouteNode(points[0], listOf(0, 1)),
            RouteNode(points[1], listOf(0)),
            RouteNode(points[2], listOf(1))
        ),
        edges = listOf(
            RouteEdge(from = 0, to = 1, lengthM = 81.0, inBand = false),
            RouteEdge(from = 0, to = 2, lengthM = 111.0, inBand = true)
        ),
        triangles = listOf(RouteTriangle(a = 0, b = 1, c = 2))
    )

    @Test
    fun everyNumberSurvivesTheRoundTrip() {
        val arrays = RouteMeshSerializer.deserialize(RouteMeshSerializer.serialize(mesh))

        assertEquals("nice-menton", arrays.regionId)
        assertEquals(43.4, arrays.box.latSouth, 1e-12)
        assertEquals(43.6, arrays.box.latNorth, 1e-12)
        assertEquals(6.9, arrays.box.lonWest, 1e-12)
        assertEquals(7.2, arrays.box.lonEast, 1e-12)

        assertArrayEquals(
            doubleArrayOf(43.5, 43.5, 43.501), arrays.pointsLat, 1e-12
        )
        assertArrayEquals(
            doubleArrayOf(7.0, 7.001, 7.0), arrays.pointsLon, 1e-12
        )
        assertArrayEquals(intArrayOf(0, 0, 0), arrays.nodeComponent)
        assertArrayEquals(intArrayOf(0, 0), arrays.edgeFrom)
        assertArrayEquals(intArrayOf(1, 2), arrays.edgeTo)
        assertArrayEquals(floatArrayOf(81f, 111f), arrays.edgeLengthM, 1e-4f)
        assertEquals(false, arrays.edgeInBand[0])
        assertTrue(arrays.edgeInBand[1])
    }

    @Test
    fun theCsrPayloadGroupsEveryEdgeUnderBothOfItsEnds() {
        val arrays = RouteMeshSerializer.deserialize(RouteMeshSerializer.serialize(mesh))

        // node 0 carries edges {0, 1}; node 1 carries {0}; node 2 carries {1}.
        assertArrayEquals(intArrayOf(0, 2, 3, 4), arrays.nodeOffsets)
        assertArrayEquals(intArrayOf(0, 1, 0, 1), arrays.nodeEdges)
        assertEquals(3, arrays.nodeCount)
        assertEquals(2, arrays.edgeCount)
    }

    @Test
    fun theKeptTrianglesSurviveTheRoundTrip() {
        val arrays = RouteMeshSerializer.deserialize(RouteMeshSerializer.serialize(mesh))

        assertEquals(1, arrays.triangleCount)
        assertArrayEquals(intArrayOf(0), arrays.triA)
        assertArrayEquals(intArrayOf(1), arrays.triB)
        assertArrayEquals(intArrayOf(2), arrays.triC)
    }

    @Test
    fun aMeshBakedBeforeTheTrianglesWereShippedStillReadsAsOneWithout() {
        // The older shape is a mesh that carries no triangles at all, and it must read as such —
        // empty, never as an accidental triangle at index 0.
        val older = mesh.copy(triangles = emptyList())

        val arrays = RouteMeshSerializer.deserialize(RouteMeshSerializer.serialize(older))

        assertEquals(0, arrays.triangleCount)
        assertEquals(3, arrays.nodeCount)
        assertEquals(2, arrays.edgeCount)
    }

    @Test
    fun anEdgeReadBackKeepsItsGeometryButNeverALimitOrTime() {
        val arrays = RouteMeshSerializer.deserialize(RouteMeshSerializer.serialize(mesh))

        assertEquals(RouteEdge(0, 1, 81.0, inBand = false), arrays.edge(0))
        assertEquals(RouteEdge(0, 2, 111.0, inBand = true), arrays.edge(1))
        assertFalse(arrays.edge(0).inBand)
    }
}
