package ykws.android.maro.spatial.mesh

import ykws.android.maro.data.model.RouteMeshArrays
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.cos

/**
 * **Is this point inside the mesh?** — the point query the two geometric passes ask: the fillet, once
 * per arc point, and the **shortcut pass** ([`RouteShortcut`]), once per sample of every candidate
 * straight segment. The second one also asks *which stretch* the triangle belongs to, because a
 * straight segment can leave the water the boat can reach without ever leaving the mesh.
 *
 * An edge set has no interior: the search walks from node to node and never has to ask whether the
 * space between two of them is covered water. A *kept* triangle is the bake's own assertion that its
 * interior passed both gates — the 2.5 m depth floor on all three of its vertices and the 30 m
 * channel floor on its centroid — so "inside a kept triangle" is the exact form of the question,
 * and the only form that cannot be fooled by a shallow patch smaller than a sample step.
 *
 * The triangles are bucketed on a uniform grid, because the fillet asks this once per arc point and
 * a sweep over ninety thousand triangles per question would spend the search's whole budget on the
 * answers. The grid is built eagerly here and this object is built lazily by its one owner, so the
 * cost lands on the first search that needs it rather than on the app's load.
 *
 * An empty triangle set answers false everywhere, and that is deliberate: a mesh read from a `.bin`
 * baked before the triangles were shipped proves nothing about its interior, so no point is claimed
 * to be inside it and a smoothing pass that must not guess simply finds nothing to smooth.
 */
internal class RouteMeshContainment(private val mesh: RouteMeshArrays) {

    private val metresPerDegreeLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    private val metresPerDegreeLon = metresPerDegreeLat * cos(mesh.box.centerLat * PI / 180.0)

    private class Grid(
        val rows: Int,
        val cols: Int,
        val latSouth: Double,
        val lonWest: Double,
        val cellLat: Double,
        val cellLon: Double,
        val offsets: IntArray,
        val triangles: IntArray
    )

    private val grid: Grid? = buildGrid()

    /** @return true when the point lies inside one of the mesh's kept triangles. */
    fun contains(latitude: Double, longitude: Double): Boolean =
        triangleAt(latitude, longitude, ANY_STRETCH) >= 0

    /**
     * **The primitive both callers actually need**, and the reason it is public rather than a boolean:
     * the point's own triangle is what says **which water** the point stands on, and the bake's
     * triangles carry their three vertices — so a caller that has the triangle can ask the zone layer
     * about the water under the point without asking it about the point.
     *
     * That is exact rather than a shortcut: a zone ring is a triangulation constraint, so no kept
     * triangle straddles one, and every triangle lies wholly inside or wholly outside every zone. A
     * sample can therefore be classified by its vertices, which are nodes the caller has already
     * priced, instead of by a live zone query per sample — the shape that measured 10,893 ms.
     *
     * @return the first kept triangle containing the point, or [NO_TRIANGLE] when none does.
     */
    fun triangleAt(latitude: Double, longitude: Double): Int =
        triangleAt(latitude, longitude, ANY_STRETCH)

    /**
     * **The stretch-aware form, which is what the shortcut pass asks.**
     *
     * "Inside the mesh" and "inside the boat's own stretch" are two questions, and a shortcut has to
     * answer both: a straight segment can cross a triangle of a stretch the boat cannot reach — the
     * pinch the stretch labelling exists to describe — without ever leaving the kept triangles, and a
     * test that asked containment alone would cut straight across it. The label is read off the
     * triangle's own first vertex, which is sound rather than convenient: a triangle's vertices are
     * joined by its own edges, so its three vertices are always one stretch. A point standing exactly
     * on a vertex that two stretches share may read as either; the shortcut samples every 10 m, so no
     * candidate is decided on such a point.
     */
    fun triangleIn(latitude: Double, longitude: Double, stretch: Int): Int =
        triangleAt(latitude, longitude, stretch)

    /**
     * The first kept triangle that contains the point — [NO_TRIANGLE] when none does, and, when a
     * stretch is named, only one whose vertices carry that stretch ([ANY_STRETCH] for any of them).
     */
    private fun triangleAt(latitude: Double, longitude: Double, stretch: Int): Int {
        val g = grid ?: return NO_TRIANGLE
        val row = ((latitude - g.latSouth) / g.cellLat).toInt()
        val col = ((longitude - g.lonWest) / g.cellLon).toInt()
        if (row < 0 || row >= g.rows || col < 0 || col >= g.cols) return NO_TRIANGLE
        val cell = row * g.cols + col
        for (k in g.offsets[cell] until g.offsets[cell + 1]) {
            val triangle = g.triangles[k]
            if (stretch != ANY_STRETCH && mesh.nodeComponent[mesh.triA[triangle]] != stretch) continue
            if (insideTriangle(triangle, latitude, longitude)) return triangle
        }
        return NO_TRIANGLE
    }

    // ── The bucket grid ───────────────────────────────────────────────────────

    /**
     * Buckets every triangle into the cells its own bounding box touches, counting first and filling
     * second, so the grid is one pair of flat arrays rather than a list per cell.
     */
    private fun buildGrid(): Grid? {
        val count = mesh.triangleCount
        if (count == 0) return null

        var latMin = Double.MAX_VALUE
        var latMax = -Double.MAX_VALUE
        var lonMin = Double.MAX_VALUE
        var lonMax = -Double.MAX_VALUE
        for (t in 0 until count) {
            val a = mesh.triA[t]
            val b = mesh.triB[t]
            val c = mesh.triC[t]
            latMin = minOf(latMin, mesh.pointsLat[a], mesh.pointsLat[b], mesh.pointsLat[c])
            latMax = maxOf(latMax, mesh.pointsLat[a], mesh.pointsLat[b], mesh.pointsLat[c])
            lonMin = minOf(lonMin, mesh.pointsLon[a], mesh.pointsLon[b], mesh.pointsLon[c])
            lonMax = maxOf(lonMax, mesh.pointsLon[a], mesh.pointsLon[b], mesh.pointsLon[c])
        }

        val cellLat = CELL_M / metresPerDegreeLat
        val cellLon = CELL_M / metresPerDegreeLon
        val rows = (((latMax - latMin) / cellLat).toInt() + 1).coerceAtLeast(1)
        val cols = (((lonMax - lonMin) / cellLon).toInt() + 1).coerceAtLeast(1)

        val offsets = IntArray(rows * cols + 1)
        for (t in 0 until count) {
            forEachCell(t, latMin, lonMin, cellLat, cellLon, cols) { row, col ->
                offsets[row * cols + col + 1]++
            }
        }
        for (i in 1..rows * cols) offsets[i] += offsets[i - 1]

        val cursor = offsets.copyOf()
        val payload = IntArray(offsets[rows * cols])
        for (t in 0 until count) {
            forEachCell(t, latMin, lonMin, cellLat, cellLon, cols) { row, col ->
                payload[cursor[row * cols + col]++] = t
            }
        }
        return Grid(rows, cols, latMin, lonMin, cellLat, cellLon, offsets, payload)
    }

    /** Walks the cells a triangle's bounding box touches, rows first. */
    private inline fun forEachCell(
        triangle: Int,
        latSouth: Double,
        lonWest: Double,
        cellLat: Double,
        cellLon: Double,
        cols: Int,
        body: (row: Int, col: Int) -> Unit
    ) {
        val a = mesh.triA[triangle]
        val b = mesh.triB[triangle]
        val c = mesh.triC[triangle]
        val rowLow = rowOf(minOf(mesh.pointsLat[a], mesh.pointsLat[b], mesh.pointsLat[c]), latSouth, cellLat)
        val rowHigh = rowOf(maxOf(mesh.pointsLat[a], mesh.pointsLat[b], mesh.pointsLat[c]), latSouth, cellLat)
        val colLow = rowOf(minOf(mesh.pointsLon[a], mesh.pointsLon[b], mesh.pointsLon[c]), lonWest, cellLon)
        val colHigh = rowOf(maxOf(mesh.pointsLon[a], mesh.pointsLon[b], mesh.pointsLon[c]), lonWest, cellLon)
        for (row in rowLow..rowHigh) {
            for (col in colLow..colHigh) {
                if (row >= 0 && col >= 0 && col < cols) body(row, col)
            }
        }
    }

    private fun rowOf(value: Double, origin: Double, cell: Double): Int =
        ((value - origin) / cell).toInt().coerceAtLeast(0)

    // ── The exact test ────────────────────────────────────────────────────────

    /**
     * The three orientation signs about the point's own position, all of one sign inside the
     * triangle. A point exactly on an edge reads as inside, which is the reading a berth wants:
     * the mesh's own boundary is water the bake kept, so a line grazing it is still on kept water.
     */
    private fun insideTriangle(triangle: Int, latitude: Double, longitude: Double): Boolean {
        val a = mesh.triA[triangle]
        val b = mesh.triB[triangle]
        val c = mesh.triC[triangle]
        val px = longitude * metresPerDegreeLon
        val py = latitude * metresPerDegreeLat
        val ax = mesh.pointsLon[a] * metresPerDegreeLon
        val ay = mesh.pointsLat[a] * metresPerDegreeLat
        val bx = mesh.pointsLon[b] * metresPerDegreeLon
        val by = mesh.pointsLat[b] * metresPerDegreeLat
        val cx = mesh.pointsLon[c] * metresPerDegreeLon
        val cy = mesh.pointsLat[c] * metresPerDegreeLat

        val ab = (bx - ax) * (py - ay) - (by - ay) * (px - ax)
        val bc = (cx - bx) * (py - by) - (cy - by) * (px - bx)
        val ca = (ax - cx) * (py - cy) - (ay - cy) * (px - cx)
        val hasNegative = ab < 0.0 || bc < 0.0 || ca < 0.0
        val hasPositive = ab > 0.0 || bc > 0.0 || ca > 0.0
        return !(hasNegative && hasPositive)
    }

    private companion object {
        /**
         * The grid's own resolution (m). The mesh's triangles are 60 m near a constraint and up to
         * 500 m offshore, so a cell this size holds a handful of them and a query tests a handful.
         */
        const val CELL_M = 400.0

        /** A stretch no node carries: the reading that does not care which stretch holds the point. */
        val ANY_STRETCH = Int.MIN_VALUE

        /** No triangle of this mesh contains the point. */
        const val NO_TRIANGLE = -1
    }
}
