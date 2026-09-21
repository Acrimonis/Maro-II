package ykws.android.maro.spatial.taut

import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The geometry home's own readings: the abstraction may only grow the wall, and the dilation is
 * checked by the distance between segments rather than by a sample.**
 *
 * These are the properties the design leans on hardest — a one-sided simplification is what makes the
 * abstraction safe rather than merely convenient, and an exact validation is what the retired
 * string-pull's sampling could not give. Both are cheap to state and cheap to break, so both are pinned
 * here rather than discovered on the water.
 */
class TautGeometryTest {

    @Test
    fun `the simplification never moves the wall into the water`() {
        // A coastline running east with the water to the south (waterSign = -1): a **bump into the water**
        // must survive, a **notch into the land** may be filled.
        val coast = listOf(
            Pt(0.0, 0.0),
            Pt(100.0, -30.0),
            Pt(200.0, 0.0),
            Pt(300.0, 20.0),
            Pt(400.0, 0.0)
        )
        val simplified = simplifyOutward(coast, toleranceM = 40.0, waterSign = -1.0)
        assertTrue(
            "the bump at (100, −30) protrudes into the water and must stay a vertex",
            simplified.any { abs(it.x - 100.0) < 1e-9 }
        )
        assertTrue(
            "the notch at (300, 20) is inside the land and may be dropped",
            simplified.none { abs(it.x - 300.0) < 1e-9 }
        )
        for (point in coast) {
            val nearest = nearestSignedDeviation(point, simplified, waterSign = -1.0)
            assertTrue(
                "every harvested vertex must lie on the obstacle's side of the abstracted wall, " +
                    "never in the water (off by ${nearest})",
                nearest <= 1e-6
            )
        }
    }

    @Test
    fun `the dilation stands a berth off the shape and is validated by segment distance`() {
        val berth = 25.0
        val coast = listOf(Pt(0.0, 0.0), Pt(400.0, 0.0))
        val simplified = simplifyOutward(coast, berth * TautObstacles.TOLERANCE_FRACTION, -1.0)
        val dilated = dilateOutward(simplified, berth, waterSign = -1.0, closed = false)
        assertEquals("an open shape keeps its two ends", 2, dilated.size)

        val (validated, short) = validateDilation(
            coast, dilated, berth, berth * TautObstacles.TOLERANCE_FRACTION, waterSign = -1.0, closed = false
        )
        assertEquals("a sound dilation needs no push", 0, short)
        val margin = shortestDistance(coast, validated)
        assertEquals("the wall stands exactly one berth off, to the tolerance", berth, margin, 0.5)
        for (point in validated) {
            assertTrue(
                "the wall moves away from the water, so every dilated point is south of the shape",
                point.y <= -berth + 1e-9
            )
        }
    }

    @Test
    fun `a dilation standing too close is pushed until it clears`() {
        val berth = 20.0
        val coast = listOf(Pt(0.0, 0.0), Pt(300.0, 0.0))
        // Deliberately wrong: left where it started, five metres off rather than twenty.
        val tooClose = listOf(Pt(0.0, -5.0), Pt(300.0, -5.0))
        val (validated, short) = validateDilation(
            coast, tooClose, berth, 0.0, waterSign = -1.0, closed = false
        )
        assertEquals("it clears on the first push, so nothing is reported short", 0, short)
        assertEquals("and it is pushed to the whole berth, not to the tolerance", berth, shortestDistance(coast, validated), 0.5)
    }

    @Test
    fun `the clip keeps one vertex beyond each side of the corridor`() {
        val line = listOf(Pt(-50.0, 0.0), Pt(50.0, 0.0), Pt(150.0, 0.0), Pt(250.0, 0.0))
        val box = BoxM(minX = 100.0, maxX = 200.0, minY = -10.0, maxY = 10.0)
        val pieces = clipPolyline(line, box)
        assertEquals("one run of the line stands inside", 1, pieces.size)
        val piece = pieces.first()
        assertEquals("the run is bounded by the first vertex outside on each side, both kept", 3, piece.size)
        assertEquals(50.0, piece.first().x, 1e-9)
        assertEquals(250.0, piece.last().x, 1e-9)
    }

    @Test
    fun `a ring only partly inside comes back as the runs that remain`() {
        val ring = listOf(
            Pt(0.0, 0.0), Pt(200.0, 0.0), Pt(200.0, 200.0), Pt(0.0, 200.0), Pt(0.0, 0.0)
        )
        val box = BoxM(minX = -10.0, maxX = 100.0, minY = -10.0, maxY = 210.0)
        val pieces = clipPolyline(ring, box)
        assertTrue(
            "the piece inside the box comes back once, walked from the first vertex outside",
            pieces.size == 1
        )
        for (piece in pieces) {
            assertTrue("each piece crosses the box rather than stopping inside it", piece.size >= 2)
            assertTrue(
                "each piece keeps a vertex beyond the boundary it was cut at",
                !box.contains(piece.first()) || !box.contains(piece.last())
            )
        }
    }

    @Test
    fun `a corner protruding into the water is a bend and a reflex corner is not`() {
        // Water to the south (waterSign = -1): a cape points south, a bay bends north.
        val capePrev = Pt(0.0, 0.0)
        val cape = Pt(100.0, -50.0)
        val capeNext = Pt(200.0, 0.0)
        assertTrue(protrudesIntoWater(capePrev, cape, capeNext, -1.0))
        val bay = Pt(100.0, 50.0)
        assertTrue(!protrudesIntoWater(capePrev, bay, capeNext, -1.0))
    }

    /**
     * **The grid walk visits every cell the segment touches — a corner clipped for a metre included.**
     *
     * The segment below runs from `(0, 101.01)` to `(160, 99.41)`: it stands in row 1 until it crosses
     * `y = 100` at `x = 101`, so it clips cell `(column 1, row 1)` for **one metre** on the way. The walk
     * this replaced sampled the line every half cell — 50 m — and its samples at `x = 40`, `80`, `120`
     * and `160` stepped straight over that metre, which is how a wall bucketed there went unseen.
     */
    @Test
    fun `the grid walk visits a cell the segment clips for less than the step`() {
        val visited = HashSet<Long>()
        val columns = HashSet<Int>()
        forEachGridCell(Pt(0.0, 101.01), Pt(160.0, 99.41), 0.0, 0.0) { key, column, _ ->
            visited.add(key)
            columns.add(column)
        }
        assertTrue(
            "the one-metre clip of cell (column 1, row 1) is visited",
            visited.contains(gridKey(1, 1))
        )
        assertTrue("and the line's own two ends are visited", columns.contains(0) && columns.contains(1))
        assertTrue("nothing beside them is", columns.all { it in 0..1 })
    }

    /**
     * **A coordinate west or south of the grid's own origin is a negative cell, never column nought.**
     *
     * `toInt()` truncates toward zero, so a leg standing outside the box an index was built over aliased
     * onto cell `(0, 0)` and was looked up in that cell's bucket — the wrong answer for a line nowhere
     * near it. The floor gives the coordinate the cell it really stands in, which matches no bucket and
     * is therefore the honest answer.
     */
    @Test
    fun `a coordinate outside the grid's origin is not aliased onto column nought`() {
        val cells = ArrayList<Pair<Int, Int>>()
        forEachGridCell(Pt(-150.0, -50.0), Pt(-50.0, -50.0), 0.0, 0.0) { _, column, row ->
            cells.add(column to row)
        }
        assertTrue("the walk visited something", cells.isNotEmpty())
        assertTrue(
            "every cell it visited is west and south of the origin: $cells",
            cells.all { it.first < 0 && it.second < 0 }
        )
        assertTrue("and the starting cell is the one the point really stands in", cells.any { it == (-2 to -1) })
    }

    /** The largest excursion of any point of [shape] onto the water side of [wall]. */
    private fun nearestSignedDeviation(point: Pt, wall: List<Pt>, waterSign: Double): Double {
        var worst = -Double.MAX_VALUE
        for (i in 0 until wall.size - 1) {
            val a = wall[i]
            val b = wall[i + 1]
            val side = cross(a, b, point) * waterSign
            if (side > worst) worst = side
        }
        return worst
    }

    private fun shortestDistance(from: List<Pt>, to: List<Pt>): Double {
        var best = Double.MAX_VALUE
        for (i in 0 until from.size - 1) {
            for (j in 0 until to.size - 1) {
                val distance = segmentDistance(from[i], from[i + 1], to[j], to[j + 1])
                if (distance < best) best = distance
            }
        }
        return best
    }
}
