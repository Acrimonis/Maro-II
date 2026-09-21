package ykws.android.maro.spatial.taut

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations

/**
 * **The corridor's own frame, and the geometry the wall is built in.**
 *
 * Everything the obstacle harvest measures is measured here rather than in degrees: a berth in metres,
 * a simplification tolerance of a quarter of it, a dilation of the whole of it, and the exact
 * segment-to-segment distance the dilation is validated by. One projection is taken per search, centred
 * on the corridor, because the corridor is a few kilometres across and a local tangent plane is exact
 * to well under the tolerances in play.
 *
 * The one thing this file never does is **sample** an obstacle: the ring is abstracted, dilated and
 * then checked by the distance between two segments, computed in closed form — the sampled test the
 * retired string-pull used is exactly what let a 25 m hole through a gate go unseen.
 */
internal class Frame(val lat0: Double, val lon0: Double) {

    private val metresPerDegreeLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    private val metresPerDegreeLon = metresPerDegreeLat * cos(lat0 * PI / 180.0)

    fun pt(point: LatLng): Pt = Pt(x(point.longitude), y(point.latitude))

    fun x(longitude: Double): Double = (longitude - lon0) * metresPerDegreeLon

    fun y(latitude: Double): Double = (latitude - lat0) * metresPerDegreeLat

    fun latLng(point: Pt): LatLng = LatLng(
        lat0 + point.y / metresPerDegreeLat,
        lon0 + point.x / metresPerDegreeLon
    )

    fun pts(points: List<LatLng>): List<Pt> = points.map { pt(it) }

    fun latLngs(points: List<Pt>): List<LatLng> = points.map { latLng(it) }
}

/** A point in the corridor's metres frame: `x` east, `y` north. */
internal data class Pt(val x: Double, val y: Double)

/**
 * The corridor in the frame's own metres — the box every harvested shape is clipped to, and the box the
 * graph's own cheap rejects are asked of.
 *
 * It carries the two operations the metre-space geometry needs and nothing else: whether two boxes share
 * an area (a ring that cannot reach a leg cannot be entered by it), and the union the corridor is sized
 * with. Keeping them here rather than in the callers is what makes "one geometry home" true of the boxes
 * as well as of the segments.
 */
internal class BoxM(val minX: Double, val maxX: Double, val minY: Double, val maxY: Double) {
    fun contains(point: Pt): Boolean =
        point.x in minX..maxX && point.y in minY..maxY

    /** Whether two boxes share any area at all — the inclusive test, a shared edge counting as shared. */
    fun overlaps(other: BoxM): Boolean =
        minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY

    /** The same box grown by [marginM] on every side. */
    fun inflate(marginM: Double): BoxM =
        BoxM(minX - marginM, maxX + marginM, minY - marginM, maxY + marginM)

    /** The smallest box holding both. */
    fun union(other: BoxM): BoxM =
        BoxM(min(minX, other.minX), max(maxX, other.maxX), min(minY, other.minY), max(maxY, other.maxY))

    companion object {

        /** The box a set of points spans, or null when there are none — the ring's own extent. */
        fun of(points: List<Pt>): BoxM? {
            if (points.isEmpty()) return null
            var minX = points[0].x
            var maxX = minX
            var minY = points[0].y
            var maxY = minY
            for (point in points) {
                if (point.x < minX) minX = point.x
                if (point.x > maxX) maxX = point.x
                if (point.y < minY) minY = point.y
                if (point.y > maxY) maxY = point.y
            }
            return BoxM(minX, maxX, minY, maxY)
        }

        /** The box one segment spans — what a leg is compared with a ring's own extent through. */
        fun ofSegment(a: Pt, b: Pt): BoxM =
            BoxM(min(a.x, b.x), max(a.x, b.x), min(a.y, b.y), max(a.y, b.y))
    }
}

/**
 * **Clips a polyline to the corridor**, keeping one vertex beyond each side so the piece really crosses
 * the boundary rather than stopping inside it.
 *
 * It is what makes the harvest affordable: the region's coastline is one long polyline and a 5 km
 * corridor needs a few kilometres of it, not a hundred. And clipping at the **search box** rather than
 * outside it is safe by construction — the box is convex, so no edge between two of its own vertices can
 * leave it, and a route can therefore never slip around a piece's clipped end.
 */
internal fun clipPolyline(points: List<Pt>, box: BoxM): List<List<Pt>> {
    if (points.size < 2) return emptyList()
    val ring = points.size >= 4 && points.first() == points.last()
    val working = if (ring) points.dropLast(1) else points
    val flags = working.map { box.contains(it) }
    if (flags.all { it }) return listOf(if (ring) working + working.first() else working)
    if (flags.none { it }) return emptyList()
    if (!ring) return runsOf(flags, working)

    // A ring that only partly stands inside is walked from the first vertex outside, so the wrap-around
    // join is stitched by the walk itself and the piece comes out as the runs that remain.
    val offset = flags.indexOfFirst { !it }
    val rotated = ArrayList<Pt>(working.size)
    val rotatedFlags = ArrayList<Boolean>(working.size)
    for (k in working.indices) {
        rotated.add(working[(offset + k) % working.size])
        rotatedFlags.add(flags[(offset + k) % working.size])
    }
    return runsOf(rotatedFlags, rotated)
}

private fun runsOf(flags: List<Boolean>, points: List<Pt>): List<List<Pt>> {
    val runs = ArrayList<List<Pt>>()
    var i = 0
    while (i < points.size) {
        if (!flags[i]) {
            i++
            continue
        }
        var j = i
        while (j + 1 < points.size && flags[j + 1]) j++
        val from = (i - 1).coerceAtLeast(0)
        val to = (j + 1).coerceAtMost(points.size - 1)
        runs.add(points.subList(from, to + 1).toList())
        i = j + 1
    }
    return runs
}

/** The turn at `v`, signed: `> 0` a left turn, `< 0` a right one, `0` straight. */
internal fun turnSign(a: Pt, v: Pt, b: Pt): Double =
    (v.x - a.x) * (b.y - v.y) - (v.y - a.y) * (b.x - v.x)

/**
 * **A corner that protrudes into the water**, which is the only kind a taut string can bend on.
 *
 * [waterSign] is `+1` when the water lies to the left of the ring's direction of travel and `-1` when it
 * lies to the right, and the vertex protrudes when the ring turns **towards** that water — the turn
 * opposite the water's side. A corner where the obstacle bends away from the water is a bay: a line
 * touching it would run inside the obstacle, so it is not a node and never a tangent point.
 *
 * The two cases that fix the sign: an island walked with its interior on the left has its water on the
 * right and every corner of it a **left** turn, and a cape on a coast with the water to the south is a
 * left turn too — both protrude. A bay bends the other way and does not.
 */
internal fun protrudesIntoWater(a: Pt, v: Pt, b: Pt, waterSign: Double): Boolean =
    turnSign(a, v, b) * waterSign < 0.0

/** The point-to-segment distance, in the frame's own metres. */
internal fun pointSegmentDistance(p: Pt, a: Pt, b: Pt): Double {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val lengthSq = abx * abx + aby * aby
    if (lengthSq <= 0.0) return hypot(p.x - a.x, p.y - a.y)
    val t = (((p.x - a.x) * abx + (p.y - a.y) * aby) / lengthSq).coerceIn(0.0, 1.0)
    return hypot(p.x - (a.x + t * abx), p.y - (a.y + t * aby))
}

/**
 * **The exact distance between two segments** — `0` when they meet, otherwise the smallest of the four
 * endpoint-to-opposite-segment distances, which is the whole of the two-segment case in the plane.
 */
internal fun segmentDistance(a: Pt, b: Pt, c: Pt, d: Pt): Double {
    if (segmentsMeet(a, b, c, d)) return 0.0
    return min(
        min(pointSegmentDistance(a, c, d), pointSegmentDistance(b, c, d)),
        min(pointSegmentDistance(c, a, b), pointSegmentDistance(d, a, b))
    )
}

/**
 * Whether two segments share a point — the closed test, used only where a genuine touch matters.
 *
 * The wall test is [properlyCrosses] instead: a path bends *on* a wall corner, so two consecutive legs
 * share that corner with the wall by construction and a closed test would refuse the only line the
 * graph is built to produce.
 */
internal fun segmentsMeet(a: Pt, b: Pt, c: Pt, d: Pt): Boolean {
    val d1 = cross(a, b, c)
    val d2 = cross(a, b, d)
    val d3 = cross(c, d, a)
    val d4 = cross(c, d, b)
    if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
        return true
    }
    return (d1 == 0.0 && onSegment(a, b, c)) || (d2 == 0.0 && onSegment(a, b, d)) ||
        (d3 == 0.0 && onSegment(c, d, a)) || (d4 == 0.0 && onSegment(c, d, b))
}

/** The cross product `(b − a) × (p − a)`. */
internal fun cross(a: Pt, b: Pt, p: Pt): Double =
    (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)

private fun onSegment(a: Pt, b: Pt, p: Pt): Boolean =
    p.x >= min(a.x, b.x) && p.x <= max(a.x, b.x) &&
        p.y >= min(a.y, b.y) && p.y <= max(a.y, b.y)

/**
 * Whether two segments cross with both crossings **strictly interior** — a touch at a shared endpoint is
 * not a crossing for either caller that asks this: the wall test, for the reason above.
 */
internal fun properlyCrosses(a: Pt, b: Pt, c: Pt, d: Pt): Boolean {
    val d1 = cross(a, b, c)
    val d2 = cross(a, b, d)
    val d3 = cross(c, d, a)
    val d4 = cross(c, d, b)
    return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
}

internal fun hypot(dx: Double, dy: Double): Double = sqrt(dx * dx + dy * dy)

/** The length of a polyline in the frame's metres. */
internal fun polylineLength(points: List<Pt>): Double {
    var total = 0.0
    for (i in 0 until points.size - 1) {
        total += hypot(points[i + 1].x - points[i].x, points[i + 1].y - points[i].y)
    }
    return total
}

/** The shoelace area of a closed ring, absolute. */
internal fun polygonAreaM2(points: List<Pt>): Double {
    if (points.size < 3) return 0.0
    var sum = 0.0
    for (i in points.indices) {
        val p = points[i]
        val q = points[(i + 1) % points.size]
        sum += p.x * q.y - q.x * p.y
    }
    return abs(sum) / 2.0
}

/** Whether the point lies inside the closed ring — the ray-cast test, on the frame's own coordinates. */
internal fun pointInRing(p: Pt, ring: List<Pt>): Boolean {
    if (ring.size < 3) return false
    var inside = false
    var j = ring.size - 1
    for (i in ring.indices) {
        val yi = ring[i].y
        val xi = ring[i].x
        val yj = ring[j].y
        val xj = ring[j].x
        if (((yi > p.y) != (yj > p.y)) && (p.x < (xj - xi) * (p.y - yi) / (yj - yi) + xi)) {
            inside = !inside
        }
        j = i
    }
    return inside
}

/**
 * **The abstraction, one-sided and outward only.**
 *
 * Douglas–Peucker moves a line either way, and half of its moves would cut the wall back into the water
 * — which is exactly the water the berth is there to keep clear. So the tolerance is spent only where it
 * can be: a candidate segment is kept when every vertex it would drop lies **on the obstacle's side** of
 * it (or on it), never in the water. The wall can therefore only ever grow, and [waterSign] says which
 * side of travel the water is on (`+1` left, `-1` right).
 *
 * The tolerance itself is a quarter of the berth: a wall that may only grow is safe by construction, and
 * a coarser ring is the lever a slow search would loosen — the one lever that cannot open a passage,
 * because it never deletes water.
 */
internal fun simplifyOutward(points: List<Pt>, toleranceM: Double, waterSign: Double): List<Pt> {
    if (points.size <= 2 || toleranceM <= 0.0) return points
    val out = ArrayList<Pt>(points.size)
    out.add(points[0])
    var i = 0
    while (i < points.size - 1) {
        var best = i + 1
        var j = i + 2
        while (j < points.size) {
            if (!segmentAbsorbs(points, i, j, toleranceM, waterSign)) break
            best = j
            j++
        }
        out.add(points[best])
        i = best
    }
    return out
}

/** Whether the segment `points[i] → points[j]` may stand in for every vertex between them. */
private fun segmentAbsorbs(
    points: List<Pt>,
    i: Int,
    j: Int,
    toleranceM: Double,
    waterSign: Double
): Boolean {
    val a = points[i]
    val b = points[j]
    for (k in i + 1 until j) {
        val p = points[k]
        if (cross(a, b, p) * waterSign > 0.0) return false
        if (pointSegmentDistance(p, a, b) > toleranceM) return false
    }
    return true
}

/**
 * **The dilation, after the simplification and away from the water.**
 *
 * Each segment is pushed along its own outward normal by the berth and consecutive offsets are met at
 * their intersection, so a corner is not rounded off by the offset and two neighbours do not double the
 * margin. `waterSign` says which side the water is on, and the wall therefore moves the other way — the
 * direction is *known* here rather than guessed, which is what lets the abstraction grow the wall safely.
 * A ring is dilated as a ring (its last vertex joins its first); an open land polyline keeps its two
 * ends, offset with its own segment.
 */
internal fun dilateOutward(
    points: List<Pt>,
    berthM: Double,
    waterSign: Double,
    closed: Boolean
): List<Pt> {
    if (points.size < 2 || berthM <= 0.0) return points
    val lines = offsetLines(points, berthM, waterSign, closed)
    // A shape whose every segment is degenerate has no wall to draw: it is dropped rather than chased,
    // and the harvest counts it as the nothing it is. Real coastline data carries repeated vertices.
    if (lines.isEmpty()) return emptyList()
    val out = ArrayList<Pt>(lines.size + 1)
    for (i in lines.indices) {
        val previous = if (i == 0) (if (closed) lines.size - 1 else -1) else i - 1
        if (previous < 0) {
            out.add(lines[i].first)
            continue
        }
        out.add(intersect(lines[previous], lines[i]) ?: lines[i].first)
    }
    if (!closed) out.add(lines.last().second)
    return out
}

/** One offset segment: the original moved along its outward normal. */
private class OffsetLine(val first: Pt, val second: Pt, val dx: Double, val dy: Double)

private fun offsetLines(
    points: List<Pt>,
    berthM: Double,
    waterSign: Double,
    closed: Boolean
): List<OffsetLine> {
    val last = if (closed) points.size else points.size - 1
    val lines = ArrayList<OffsetLine>(last)
    for (i in 0 until last) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val length = hypot(dx, dy)
        if (length <= 0.0) continue
        // The unit normal pointing **into the water**: dilating the exclusion set moves its boundary
        // away from the obstacle and into the water it keeps clear, which is the direction the berth is
        // measured in. The water is to the left when waterSign = +1, so that is the left normal there.
        val nx = -waterSign * dy / length
        val ny = waterSign * dx / length
        lines.add(
            OffsetLine(
                Pt(a.x + nx * berthM, a.y + ny * berthM),
                Pt(b.x + nx * berthM, b.y + ny * berthM),
                dx,
                dy
            )
        )
    }
    return lines
}

/** Where two offset lines meet, or null when they are parallel — a straight run, whose ends coincide. */
private fun intersect(first: OffsetLine, second: OffsetLine): Pt? {
    val denominator = first.dx * second.dy - first.dy * second.dx
    if (abs(denominator) < 1e-12) return null
    val t = ((second.first.x - first.first.x) * second.dy -
        (second.first.y - first.first.y) * second.dx) / denominator
    return Pt(first.first.x + t * first.dx, first.first.y + t * first.dy)
}

/**
 * **The buffer's own validation: the exact segment-to-segment distance, never a sample.**
 *
 * The dilated ring must stand at least `berth − tolerance` away from the **un-abstracted** obstacle: the
 * abstraction may move the wall outward by up to the tolerance, and the berth is measured from the
 * abstracted ring, so the margin it really holds against the true obstacle is the berth less that
 * tolerance — which is exactly what "the dilation absorbs the tolerance by construction" means, and what
 * makes it a number rather than a hope.
 *
 * A segment that comes closer than that is **pushed** further out, by the shortfall, and re-measured —
 * never sampled, never moved sideways. A segment still short after [MAX_PUSHES] is counted: the count is
 * the finding, and the geometry stays where the last push put it rather than being quietly accepted.
 */
internal fun validateDilation(
    source: List<Pt>,
    dilated: List<Pt>,
    berthM: Double,
    toleranceM: Double,
    waterSign: Double,
    closed: Boolean
): Pair<List<Pt>, Int> {
    if (source.size < 2 || dilated.size < 2 || berthM <= 0.0) return dilated to 0
    val minimum = (berthM - toleranceM).coerceAtLeast(0.0)
    val working = dilated.toMutableList()
    var short = 0
    var pushes = 0
    while (pushes < MAX_PUSHES) {
        val shortfall = shortestMargin(source, working, closed)
        if (shortfall.second >= minimum - GEOMETRY_EPS_M) break
        pushOut(working, shortfall.first, shortfall.second, minimum, waterSign, closed)
        pushes++
    }
    if (shortestMargin(source, working, closed).second < minimum - GEOMETRY_EPS_M) short++
    return working to short
}

/**
 * The smallest distance between any dilated segment and the source ring, with the index of the dilated
 * segment that produced it — the pair [validateDilation] acts on.
 */
private fun shortestMargin(
    source: List<Pt>,
    dilated: List<Pt>,
    closed: Boolean
): Pair<Int, Double> {
    var index = -1
    var smallest = Double.MAX_VALUE
    val last = if (closed) dilated.size else dilated.size - 1
    for (i in 0 until last) {
        val a = dilated[i]
        val b = dilated[(i + 1) % dilated.size]
        var closest = Double.MAX_VALUE
        for (j in 0 until source.size - 1) {
            val d = segmentDistance(a, b, source[j], source[j + 1])
            if (d < closest) closest = d
        }
        if (closest < smallest) {
            smallest = closest
            index = i
        }
    }
    return index to smallest
}

/** Moves one dilated segment further out along its own normal, so it clears [minimum]. */
private fun pushOut(
    dilated: MutableList<Pt>,
    index: Int,
    current: Double,
    minimum: Double,
    waterSign: Double,
    closed: Boolean
) {
    if (index < 0 || index >= dilated.size) return
    val next = (index + 1) % dilated.size
    val a = dilated[index]
    val b = dilated[next]
    val dx = b.x - a.x
    val dy = b.y - a.y
    val length = hypot(dx, dy)
    if (length <= 0.0) return
    val push = (minimum - current).coerceAtLeast(0.0) + GEOMETRY_EPS_M
    val nx = -waterSign * dy / length * push
    val ny = waterSign * dx / length * push
    dilated[index] = Pt(a.x + nx, a.y + ny)
    dilated[next] = Pt(b.x + nx, b.y + ny)
    // The neighbours' ends follow the moved points, so the ring stays joined rather than kinked apart.
    val before = if (index == 0) (if (closed) dilated.size - 1 else -1) else index - 1
    if (before >= 0 && before < dilated.size) {
        val p = dilated[before]
        dilated[before] = Pt(p.x + nx, p.y + ny)
    }
}

/** Metres of slack in the geometry's own comparisons — a centimetre is far below any berth in play. */
internal const val GEOMETRY_EPS_M = 0.01

/** How many times a short dilated segment may be pushed before its shortfall is reported instead. */
private const val MAX_PUSHES = 4

/**
 * The cell size (m) of the corridor's own uniform grids — a few times the berth, so one query walks a
 * handful of cells rather than a box's area.
 *
 * It is one constant because it is one mechanism: the wall index and the band's own index both bucket
 * their segments into this grid and both walk it along the line that asks, and a second cell size would
 * be a second answer to "which cells does this leg touch".
 */
internal const val GRID_CELL_M = 100.0

/** The key of a grid position — a row and a column packed into one value, so a bucket map needs no pair. */
internal fun gridKey(row: Int, column: Int): Long =
    (row.toLong() shl 32) xor (column.toLong() and 0xFFFFFFFFL)

/**
 * **The cell index of a frame coordinate on the shared grid** — the **floor**, never a truncation.
 *
 * One function serves the bucketing and the walk alike, so the two cannot index a coordinate
 * differently. `toInt()` truncates toward zero, so a coordinate west or south of the grid's own origin —
 * a leg outside the bucket's box walked over an index built from segments inside it — used to alias onto
 * column nought and be looked up in that column's bucket; the floor gives it a negative index, which
 * matches no bucket and is therefore the honest answer.
 */
internal fun gridIndex(value: Double, origin: Double): Int =
    floor((value - origin) / GRID_CELL_M).toInt()

/**
 * **Every grid cell the segment `a → b` touches**, handed to [visit] with its key and its own row and
 * column.
 *
 * A **supercover** Amanatides–Woo walk rather than a sampled one: each step crosses the grid line the
 * segment itself crosses next, so the walk visits **every** cell the segment passes through and no cell
 * it merely stands beside. A sampled walk — the shape this replaced — stepped half a cell at a time, so
 * a cell a chord clipped for less than the step could be stepped over, and the wall bucketed in that
 * cell went unseen; here the walk is exact, and a segment passing exactly through a grid corner visits
 * the diagonal cell too, a touch counting as a visit.
 *
 * The grid's own origin is the caller's — [minX] and [minY] — so a corridor-sized index keeps small keys
 * whatever frame it was built in, while [gridIndex]'s floor keeps a coordinate outside the box from
 * aliasing onto column nought. A degenerate segment visits its single cell.
 */
internal inline fun forEachGridCell(
    a: Pt,
    b: Pt,
    minX: Double,
    minY: Double,
    visit: (key: Long, column: Int, row: Int) -> Unit
) {
    val ax = (a.x - minX) / GRID_CELL_M
    val ay = (a.y - minY) / GRID_CELL_M
    val bx = (b.x - minX) / GRID_CELL_M
    val by = (b.y - minY) / GRID_CELL_M
    var column = floor(ax).toInt()
    var row = floor(ay).toInt()
    val lastColumn = floor(bx).toInt()
    val lastRow = floor(by).toInt()
    visit(gridKey(row, column), column, row)
    if (column == lastColumn && row == lastRow) return

    val dx = bx - ax
    val dy = by - ay
    val stepColumn = if (dx > 0.0) 1 else if (dx < 0.0) -1 else 0
    val stepRow = if (dy > 0.0) 1 else if (dy < 0.0) -1 else 0
    var crossColumn = if (dx != 0.0) {
        val line = if (dx > 0.0) (column + 1).toDouble() else column.toDouble()
        abs((line - ax) / dx)
    } else {
        Double.POSITIVE_INFINITY
    }
    var crossRow = if (dy != 0.0) {
        val line = if (dy > 0.0) (row + 1).toDouble() else row.toDouble()
        abs((line - ay) / dy)
    } else {
        Double.POSITIVE_INFINITY
    }
    val perColumn = if (dx != 0.0) abs(1.0 / dx) else Double.POSITIVE_INFINITY
    val perRow = if (dy != 0.0) abs(1.0 / dy) else Double.POSITIVE_INFINITY

    // **Bounded by the cells the segment can possibly touch** — `|Δcolumn| + |Δrow|` after the first — so
    // the walk terminates by construction rather than by its two indices meeting. The unbounded shape it
    // replaces could circle one cell forever: a degenerate direction leaves one step at zero while the
    // other index never reaches its last cell, and the comparison that would have advanced it stays false,
    // so the loop visited nothing new and never ended — a hung graph build, and every wall the walk had
    // still to see left unseen.
    val steps = abs(lastColumn - column) + abs(lastRow - row)
    var taken = 0
    while (taken < steps) {
        if (stepColumn == 0 && stepRow == 0) return
        if (crossColumn < crossRow) {
            column += stepColumn
            crossColumn += perColumn
        } else if (crossRow < crossColumn) {
            row += stepRow
            crossRow += perRow
        } else {
            // Exactly a grid corner: a supercover counts the touch, so the diagonal cell goes in as well.
            column += stepColumn
            row += stepRow
            crossColumn += perColumn
            crossRow += perRow
        }
        taken++
        visit(gridKey(row, column), column, row)
    }
}
