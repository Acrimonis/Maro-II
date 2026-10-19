package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * **The sub-cell (m) a mixed raster quad is split into before the 3 m line is traced** — the trace's own
 * precision, and the whole of the bound every reader subtracts ([ShallowEdgeIndex.padM]). At 1.5 m the pad
 * is 2.1 m, so a reading sits at most two pads (4.2 m) under the truth and a passage of 50 m always keeps a
 * legal row on a 20 m grid with a 10 m wall. **It shapes the stored wall**: a change here must bump
 * [`FineTileCodec.FORMAT_VERSION`], since no tile key carries it.
 */
internal const val SHALLOW_EDGE_SUBCELL_M = 1.5

/**
 * **The node spacing (m) a world without a raster of its own is read at** — a fixture's analytic depth is
 * sampled as if it were a raster of this cell, so a shallow feature finer than it may go unseen there. The
 * live world answers its own raster's lattice and never reads this.
 */
internal const val NOMINAL_DEPTH_NODE_M = 10.0

/** Raster quads per chunk side — the unit the index is built in, lazily, on first touch. */
private const val CHUNK_QUADS = 32

/** The value an unsurveyed node is read as: NoData never walls, so it stands with the deep water. */
private const val NO_DATA_DEPTH = 1.0e9

/** How far one sphere-tracing read looks ahead (m) where no edge stands within it. */
private const val RAY_READ_M = 25.0

/** The distance (m) under which a traced ray is taken to have met the edge. */
private const val RAY_HIT_M = 0.5

private const val M_PER_DEG_LAT = SpatialOperations.EARTH_RADIUS_M * PI / 180.0

/**
 * **The depth raster's node geometry** — the lattice the bilinear read interpolates between: node `(row,
 * col)` stands at the raster cell's own centre, exactly as [`DepthGrid.depthAt`] places it, and a **quad**
 * is the square between four neighbouring nodes, named by its south-west node.
 *
 * [referenceLat] is the latitude the longitude cell is measured at, so the quad's two sides in metres are
 * one figure for the whole lattice.
 */
class DepthLattice(
    val latSouth: Double,
    val lonWest: Double,
    val cellSizeDegLat: Double,
    val cellSizeDegLon: Double,
    val referenceLat: Double
) {
    /** The quad's south–north side (m). */
    val cellLatM: Double = cellSizeDegLat * M_PER_DEG_LAT

    /** The quad's west–east side (m), at [referenceLat]. */
    val cellLonM: Double = cellSizeDegLon * M_PER_DEG_LAT * cos(Math.toRadians(referenceLat))

    fun nodeLat(row: Int): Double = latSouth + (row + 0.5) * cellSizeDegLat

    fun nodeLon(col: Int): Double = lonWest + (col + 0.5) * cellSizeDegLon

    /** The quad row holding [latitude] — the south node row of the four the read interpolates between. */
    fun quadRowOf(latitude: Double): Int = floor((latitude - latSouth) / cellSizeDegLat - 0.5).toInt()

    /** The quad column holding [longitude]. */
    fun quadColOf(longitude: Double): Int = floor((longitude - lonWest) / cellSizeDegLon - 0.5).toInt()

    companion object {
        /**
         * **The nominal lattice a world without a raster is read on** — [spacingM] nodes laid from the world's
         * own anchor where it names one, from the equator and meridian otherwise. The longitude side is set at
         * the anchor's reference latitude, and at the equator where none is named: a side measured there only
         * shrinks with the latitude, so the pad the index derives from it stays on the safe side everywhere.
         */
        fun nominal(anchor: LatticeAnchor?, spacingM: Double): DepthLattice {
            val referenceLat = anchor?.referenceLat ?: 0.0
            val degLat = spacingM / M_PER_DEG_LAT
            val degLon = spacingM / (M_PER_DEG_LAT * cos(Math.toRadians(referenceLat)))
            return DepthLattice(anchor?.latSouth ?: 0.0, anchor?.lonWest ?: 0.0, degLat, degLon, referenceLat)
        }
    }
}

/**
 * **The 3 m edge as a line — measured once, read everywhere.**
 *
 * The depth the router reads is bilinear between the raster's nodes, so *where the water is under the
 * gate* has an exact boundary: the gate depth's contour. This index traces it as segments and answers the
 * distance to it, the way the coastline's own index answers the distance to the shore, so every guard —
 * the raster's paint, the search's step, the pull's chord, the corner's bow and the trace's reading —
 * measures the standoff instead of sampling depth at a handful of points around it.
 *
 * **Built lazily, chunk by chunk.** A chunk is [CHUNK_QUADS] × [CHUNK_QUADS] raster quads, built on first
 * touch and kept for the index's life. Its nodes are read once, twice over: through the gate's own
 * [depthAt], and through [rawDepthAt], the raster's own value before any chart gate hides a node. A quad
 * **may hold blocked water** where a node's raw depth is under the gate — a node the EMODnet cutoff hides
 * still blends its depth into its neighbours' read, so the band beside it can fall under the gate — and
 * **may hold open water** where a node's gated read clears it. Every quad that may hold both is split into
 * [split] × [split] sub-cells of at most [SHALLOW_EDGE_SUBCELL_M], each sub-node read through the gate's
 * own [depthAt], NoData read as deep, and the contour traced on that sub-field; a chunk holding no such
 * quad holds no segment. Tracing the gate's own function — the EMODnet shallow gate and the NoData
 * renormalisation included — is what keeps the line and the wall from ever disagreeing about which water
 * is blocked.
 *
 * **The bound ([padM]).** A crossing on a sub-cell's side is exact (the field is linear along it) and the
 * true contour never leaves its sub-cell, so a true edge point stands within one sub-cell diagonal of an
 * emitted segment. Every reader therefore subtracts [padM] from the distance it reads, which makes the
 * reading a **lower bound** on the true distance — the side a wall must err on. The bound holds wherever the
 * gate's depth is continuous, NoData renormalisation included (the read is then monotone along each axis, so
 * a sub-cell's extremes stand at its corners). **The one caveat is the EMODnet shallow cutoff**, which the gate
 * applies by the dominant node's source and so switches discontinuously: inside a traced quad, a sliver of
 * blocked water narrower than one sub-cell, whose four sub-corners all read gated or deep, is not traced.
 * Only coarse EMODnet water shallower than the chart's own cutoff can make one, and never wider than a
 * sub-cell.
 *
 * Thread-safe: tile builds run on several workers, and a chunk is built once under the map's own
 * `computeIfAbsent`.
 */
class ShallowEdgeIndex(
    private val depthAt: (Double, Double) -> Double,
    private val lattice: DepthLattice,
    /** The gate's own depth (m): water under it is blocked, NoData never is. */
    val minDepthM: Double,
    /**
     * The raster's own depth before any chart gate, `NaN` for NoData — the read that says whether a quad
     * may hold blocked water. It is [depthAt] itself where no gate hides a node.
     */
    private val rawDepthAt: (Double, Double) -> Double = depthAt
) {
    /** How many sub-cells a quad side is split into, so neither sub-side exceeds [SHALLOW_EDGE_SUBCELL_M]. */
    private val split: Int =
        max(1, ceil(max(lattice.cellLatM, lattice.cellLonM) / SHALLOW_EDGE_SUBCELL_M).toInt())

    /** The bound every reader subtracts (m): one sub-cell's diagonal. */
    val padM: Double = sqrt(
        (lattice.cellLatM / split) * (lattice.cellLatM / split) +
            (lattice.cellLonM / split) * (lattice.cellLonM / split)
    )

    private val chunks = ConcurrentHashMap<Long, Chunk>()

    private val builtChunks = AtomicLong()
    private val builtMixedQuads = AtomicLong()
    private val builtSegments = AtomicLong()
    private val buildNanos = AtomicLong()

    /** Whether the point itself stands in water the gate blocks — the interior the edge alone cannot tell. */
    fun isShallow(latitude: Double, longitude: Double): Boolean =
        !depthClearsGate(depthAt(latitude, longitude), minDepthM)

    /**
     * The distance (m) from a point to the nearest traced segment, or `Double.MAX_VALUE` where none stands
     * within [reachM]. **The pad is not taken off here** — a reader that wants the bound reads [GateRule.edgeM].
     */
    fun distanceM(latitude: Double, longitude: Double, reachM: Double): Double {
        val mLon = M_PER_DEG_LAT * cos(Math.toRadians(latitude))
        var best = Double.MAX_VALUE
        forEachSegmentNear(
            latitude - reachM / M_PER_DEG_LAT, latitude + reachM / M_PER_DEG_LAT,
            longitude - reachM / mLon, longitude + reachM / mLon
        ) { s, i ->
            val ax = (s[i + 1] - longitude) * mLon
            val ay = (s[i] - latitude) * M_PER_DEG_LAT
            val bx = (s[i + 3] - longitude) * mLon
            val by = (s[i + 2] - latitude) * M_PER_DEG_LAT
            val d = pointToSegmentM(0.0, 0.0, ax, ay, bx, by)
            if (d < best) best = d
        }
        return if (best <= reachM) best else Double.MAX_VALUE
    }

    /**
     * The least distance (m) between the segment [a]–[b] and any traced segment, `0.0` where they cross,
     * or `Double.MAX_VALUE` where none stands within [reachM] of it.
     */
    fun segmentDistanceM(a: LatLng, b: LatLng, reachM: Double): Double {
        val mLon = M_PER_DEG_LAT * cos(Math.toRadians(a.latitude))
        val bx = (b.longitude - a.longitude) * mLon
        val by = (b.latitude - a.latitude) * M_PER_DEG_LAT
        var best = Double.MAX_VALUE
        forEachSegmentNear(
            min(a.latitude, b.latitude) - reachM / M_PER_DEG_LAT,
            max(a.latitude, b.latitude) + reachM / M_PER_DEG_LAT,
            min(a.longitude, b.longitude) - reachM / mLon,
            max(a.longitude, b.longitude) + reachM / mLon
        ) { s, i ->
            val cx = (s[i + 1] - a.longitude) * mLon
            val cy = (s[i] - a.latitude) * M_PER_DEG_LAT
            val dx = (s[i + 3] - a.longitude) * mLon
            val dy = (s[i + 2] - a.latitude) * M_PER_DEG_LAT
            val d = segmentToSegmentM(0.0, 0.0, bx, by, cx, cy, dx, dy)
            if (d < best) best = d
        }
        return if (best <= reachM) best else Double.MAX_VALUE
    }

    /**
     * **Every traced segment standing in a quad that meets [box]**, handed to [action] as its two ends —
     * the rasterizer's own read, which sweeps the cells near each.
     */
    fun forEachSegmentIn(box: BBox, action: (aLat: Double, aLon: Double, bLat: Double, bLon: Double) -> Unit) {
        forEachSegmentNear(box.latSouth, box.latNorth, box.lonWest, box.lonEast) { s, i ->
            action(s[i], s[i + 1], s[i + 2], s[i + 3])
        }
    }

    /**
     * **Where a ray from [origin] first meets the edge** (m along it), or `null` within [maxM] — the medial
     * nudge's read of a passage's two banks. The ray steps by the distance the index answers, so it never
     * steps over an edge; a point standing in blocked water is a hit where it stands. [eastUnit] and
     * [northUnit] are the direction in local metres, of unit length.
     */
    fun rayHitM(origin: LatLng, eastUnit: Double, northUnit: Double, maxM: Double): Double? {
        val mLon = M_PER_DEG_LAT * cos(Math.toRadians(origin.latitude))
        var t = 0.0
        while (t <= maxM) {
            val lat = origin.latitude + northUnit * t / M_PER_DEG_LAT
            val lon = origin.longitude + eastUnit * t / mLon
            if (isShallow(lat, lon)) return t
            val d = distanceM(lat, lon, RAY_READ_M)
            if (d == Double.MAX_VALUE) {
                t += RAY_READ_M
                continue
            }
            if (d < RAY_HIT_M) return t
            t += d
        }
        return null
    }

    /** The index's own tally for the device trace: what it has built so far and what that cost. */
    fun statsLine(): String =
        "EDGES chunks=${builtChunks.get()} mixed=${builtMixedQuads.get()} segments=${builtSegments.get()} " +
            "ms=${fmt(buildNanos.get() / NANOS_PER_MS)} pad=${fmt(padM, 2)}m split=$split"

    /** The segments of every quad overlapping the box, each handed as its array and its offset. */
    private inline fun forEachSegmentNear(
        latSouth: Double,
        latNorth: Double,
        lonWest: Double,
        lonEast: Double,
        action: (DoubleArray, Int) -> Unit
    ) {
        val qrMin = lattice.quadRowOf(latSouth)
        val qrMax = lattice.quadRowOf(latNorth)
        val qcMin = lattice.quadColOf(lonWest)
        val qcMax = lattice.quadColOf(lonEast)
        for (cr in Math.floorDiv(qrMin, CHUNK_QUADS)..Math.floorDiv(qrMax, CHUNK_QUADS)) {
            for (cc in Math.floorDiv(qcMin, CHUNK_QUADS)..Math.floorDiv(qcMax, CHUNK_QUADS)) {
                val chunk = chunkAt(cr, cc)
                val starts = chunk.quadStart ?: continue
                val r0 = max(qrMin, chunk.quadRow0) - chunk.quadRow0
                val r1 = min(qrMax, chunk.quadRow0 + CHUNK_QUADS - 1) - chunk.quadRow0
                val c0 = max(qcMin, chunk.quadCol0) - chunk.quadCol0
                val c1 = min(qcMax, chunk.quadCol0 + CHUNK_QUADS - 1) - chunk.quadCol0
                val segments = chunk.segments
                for (r in r0..r1) {
                    for (c in c0..c1) {
                        val q = r * CHUNK_QUADS + c
                        var s = starts[q]
                        val end = starts[q + 1]
                        while (s < end) {
                            action(segments, s * 4)
                            s++
                        }
                    }
                }
            }
        }
    }

    private fun chunkAt(chunkRow: Int, chunkCol: Int): Chunk {
        val key = (chunkRow.toLong() shl 32) or (chunkCol.toLong() and 0xFFFFFFFFL)
        return chunks[key] ?: chunks.computeIfAbsent(key) { buildChunk(chunkRow, chunkCol) }
    }

    /** One node or sub-node read: the gate's own depth, NoData standing with the deep water. */
    private fun read(latitude: Double, longitude: Double): Double {
        val d = depthAt(latitude, longitude)
        return if (d.isNaN()) NO_DATA_DEPTH else d
    }

    private fun buildChunk(chunkRow: Int, chunkCol: Int): Chunk {
        val startNs = System.nanoTime()
        val qr0 = chunkRow * CHUNK_QUADS
        val qc0 = chunkCol * CHUNK_QUADS
        val n = CHUNK_QUADS + 1
        val nodes = DoubleArray(n * n) { idx -> read(lattice.nodeLat(qr0 + idx / n), lattice.nodeLon(qc0 + idx % n)) }
        // Whether a node may stand under the gate: its raw depth is — a node the chart's cutoff hides still
        // blends that depth into its neighbours' read.
        val rawShallow = BooleanArray(n * n) { idx ->
            rawDepthAt(lattice.nodeLat(qr0 + idx / n), lattice.nodeLon(qc0 + idx % n)) < minDepthM
        }
        var anyShallow = false
        var anyClear = false
        for (idx in nodes.indices) {
            if (rawShallow[idx]) anyShallow = true
            if (nodes[idx] >= minDepthM) anyClear = true
        }
        if (!anyShallow || !anyClear) {
            builtChunks.incrementAndGet()
            buildNanos.addAndGet(System.nanoTime() - startNs)
            return Chunk(qr0, qc0, null, EMPTY_SEGMENTS)
        }
        val out = SegmentBuffer()
        val starts = IntArray(CHUNK_QUADS * CHUNK_QUADS + 1)
        val sub = DoubleArray((split + 1) * (split + 1))
        var mixed = 0
        for (qr in 0 until CHUNK_QUADS) {
            for (qc in 0 until CHUNK_QUADS) {
                starts[qr * CHUNK_QUADS + qc] = out.count
                val sw = nodes[qr * n + qc]
                val se = nodes[qr * n + qc + 1]
                val ne = nodes[(qr + 1) * n + qc + 1]
                val nw = nodes[(qr + 1) * n + qc]
                val mayBeShallow = rawShallow[qr * n + qc] || rawShallow[qr * n + qc + 1] ||
                    rawShallow[(qr + 1) * n + qc + 1] || rawShallow[(qr + 1) * n + qc]
                val mayBeClear = sw >= minDepthM || se >= minDepthM || ne >= minDepthM || nw >= minDepthM
                if (!mayBeShallow || !mayBeClear) continue
                mixed++
                traceQuad(qr0 + qr, qc0 + qc, sw, se, ne, nw, sub, out)
            }
        }
        starts[CHUNK_QUADS * CHUNK_QUADS] = out.count
        builtChunks.incrementAndGet()
        builtMixedQuads.addAndGet(mixed.toLong())
        builtSegments.addAndGet(out.count.toLong())
        buildNanos.addAndGet(System.nanoTime() - startNs)
        return Chunk(qr0, qc0, starts, out.toArray())
    }

    /**
     * **One mixed quad, traced.** The quad is split [split] × [split]; its four corners keep the node reads
     * already paid and every other sub-node is read through the gate's own depth. Each sub-cell then emits
     * the segment(s) its corners call for — a crossing on a side placed by linear interpolation, which is
     * exact there — and a saddle is resolved by the sub-cell's own centre, cutting off the corners whose
     * class differs from it.
     */
    private fun traceQuad(
        quadRow: Int,
        quadCol: Int,
        sw: Double,
        se: Double,
        ne: Double,
        nw: Double,
        sub: DoubleArray,
        out: SegmentBuffer
    ) {
        val k = split
        val side = k + 1
        val lat0 = lattice.nodeLat(quadRow)
        val lon0 = lattice.nodeLon(quadCol)
        val dLat = lattice.cellSizeDegLat / k
        val dLon = lattice.cellSizeDegLon / k
        for (i in 0..k) {
            for (j in 0..k) {
                sub[i * side + j] = when {
                    i == 0 && j == 0 -> sw
                    i == 0 && j == k -> se
                    i == k && j == k -> ne
                    i == k && j == 0 -> nw
                    else -> read(lat0 + i * dLat, lon0 + j * dLon)
                }
            }
        }
        val level = minDepthM
        val crossing = DoubleArray(4)
        for (i in 0 until k) {
            for (j in 0 until k) {
                val a = sub[i * side + j]
                val b = sub[i * side + j + 1]
                val c = sub[(i + 1) * side + j + 1]
                val d = sub[(i + 1) * side + j]
                val sa = a < level
                val sb = b < level
                val sc = c < level
                val sd = d < level
                if (sa == sb && sb == sc && sc == sd) continue
                val south = sa != sb
                val east = sb != sc
                val north = sd != sc
                val west = sa != sd
                // The four crossing points, each on its own side, placed where the side's linear read meets
                // the gate's depth — the same point the neighbouring sub-cell computes for the shared side.
                val sLat = lat0 + i * dLat
                val nLat = lat0 + (i + 1) * dLat
                val wLon = lon0 + j * dLon
                val eLon = lon0 + (j + 1) * dLon
                val southLon = if (south) wLon + frac(a, b, level) * dLon else 0.0
                val northLon = if (north) wLon + frac(d, c, level) * dLon else 0.0
                val eastLat = if (east) sLat + frac(b, c, level) * dLat else 0.0
                val westLat = if (west) sLat + frac(a, d, level) * dLat else 0.0
                if (south && east && north && west) {
                    // The saddle: the centre decides which pair of corners the water connects.
                    val centreShallow = (a + b + c + d) / 4.0 < level
                    if (sa != centreShallow) out.add(sLat, southLon, westLat, wLon)
                    if (sb != centreShallow) out.add(sLat, southLon, eastLat, eLon)
                    if (sc != centreShallow) out.add(nLat, northLon, eastLat, eLon)
                    if (sd != centreShallow) out.add(nLat, northLon, westLat, wLon)
                    continue
                }
                // Exactly two sides are crossed: one segment between their two crossings.
                var count = 0
                if (south) {
                    crossing[0] = sLat
                    crossing[1] = southLon
                    count = 1
                }
                if (east) {
                    crossing[count * 2] = eastLat
                    crossing[count * 2 + 1] = eLon
                    count++
                }
                if (north && count < 2) {
                    crossing[count * 2] = nLat
                    crossing[count * 2 + 1] = northLon
                    count++
                }
                if (west && count < 2) {
                    crossing[count * 2] = westLat
                    crossing[count * 2 + 1] = wLon
                    count++
                }
                if (count == 2) out.add(crossing[0], crossing[1], crossing[2], crossing[3])
            }
        }
    }

    /** One chunk's segments, bucketed by quad: quad `q`'s segments are `[quadStart[q], quadStart[q + 1])`. */
    private class Chunk(
        val quadRow0: Int,
        val quadCol0: Int,
        /** `null` where the chunk holds no segment at all — its fast path. */
        val quadStart: IntArray?,
        /** Four doubles a segment: `lat0, lon0, lat1, lon1`. */
        val segments: DoubleArray
    )

    /** A growing flat array of segments, four doubles each. */
    private class SegmentBuffer {
        private var data = DoubleArray(64)
        var count = 0
            private set

        fun add(aLat: Double, aLon: Double, bLat: Double, bLon: Double) {
            if ((count + 1) * 4 > data.size) data = data.copyOf(data.size * 2)
            val base = count * 4
            data[base] = aLat
            data[base + 1] = aLon
            data[base + 2] = bLat
            data[base + 3] = bLon
            count++
        }

        fun toArray(): DoubleArray = data.copyOf(count * 4)
    }

    private companion object {
        val EMPTY_SEGMENTS = DoubleArray(0)

        /** Where the linear read from [v0] to [v1] meets [level], as a fraction of the side. */
        fun frac(v0: Double, v1: Double, level: Double): Double {
            val span = v1 - v0
            if (span == 0.0) return 0.5
            return ((level - v0) / span).coerceIn(0.0, 1.0)
        }
    }
}

/**
 * The distance (m) from point `(px, py)` to segment `(ax, ay)–(bx, by)`, all in local metres — the edge's one
 * distance kernel, read by the index and by the rasterizer's sweep of it alike.
 */
internal fun pointToSegmentM(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dx = bx - ax
    val dy = by - ay
    val len2 = dx * dx + dy * dy
    val t = if (len2 <= 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
    val ex = ax + t * dx - px
    val ey = ay + t * dy - py
    return sqrt(ex * ex + ey * ey)
}

/** The least distance (m) between two segments in local metres, `0.0` where they cross. */
private fun segmentToSegmentM(
    ax: Double, ay: Double, bx: Double, by: Double,
    cx: Double, cy: Double, dx: Double, dy: Double
): Double {
    val d1 = cross(bx - ax, by - ay, cx - ax, cy - ay)
    val d2 = cross(bx - ax, by - ay, dx - ax, dy - ay)
    val d3 = cross(dx - cx, dy - cy, ax - cx, ay - cy)
    val d4 = cross(dx - cx, dy - cy, bx - cx, by - cy)
    if (((d1 > 0.0 && d2 < 0.0) || (d1 < 0.0 && d2 > 0.0)) &&
        ((d3 > 0.0 && d4 < 0.0) || (d3 < 0.0 && d4 > 0.0))
    ) {
        return 0.0
    }
    return min(
        min(pointToSegmentM(ax, ay, cx, cy, dx, dy), pointToSegmentM(bx, by, cx, cy, dx, dy)),
        min(pointToSegmentM(cx, cy, ax, ay, bx, by), pointToSegmentM(dx, dy, ax, ay, bx, by))
    )
}

private fun cross(ux: Double, uy: Double, vx: Double, vy: Double): Double = ux * vy - uy * vx

/**
 * **The depth gate's rule, one value threaded to every guard** — the measured edge and the three
 * distances that bind on it.
 *
 * - [hardM] — the wall: no point of a route comes closer to the edge than this. `evolutive` answers its
 *   berth's floor (`route.evolutive.berth.floorM`), the water between it and [preferredM] being priced;
 *   `avoid`, which prices no berth, answers the gate's own margin.
 * - [preferredM] — the standoff kept wherever there is room (`route.avoid.depthGate.marginM`): the corner
 *   pass's bow keeps it, and the berth's inner ramp ends at it.
 * - [waiverM] — the disc around each raw end inside which the gate does not bind, the end's own carved
 *   approach beside it: an end standing in shallow-side water must still be left.
 *
 * Every distance it answers is the index's reading **minus the pad**, so a guard reading it reads a lower
 * bound on the true distance and a line it clears is clear.
 */
class GateRule(
    val edges: ShallowEdgeIndex,
    val hardM: Double,
    val preferredM: Double,
    val waiverM: Double,
    /** The berth's outer edge (m), `0.0` where the plan prices none — the reach the raster writes exactly. */
    val berthOuterM: Double = 0.0
) {
    /** The bound subtracted from every reading (m). */
    val padM: Double get() = edges.padM

    /**
     * **How far (m) a raster of [cellM] cells writes the measured edge's distance exactly**, the rest of the
     * field relaxing from those seeds: the berth's whole priced span, and at least the wall plus one cell
     * diagonal, so a step between two neighbouring cells can be proved clear from the two cells' stored
     * distances alone. The raster and the search's step guard read this one rule, so the guard never trusts a
     * distance the raster did not write exactly. **It shapes the stored field**: a change here must bump
     * [`FineTileCodec.FORMAT_VERSION`].
     */
    fun seedReachM(cellM: Double): Double = max(berthOuterM, hardM + cellM * sqrt(2.0))

    /** The metres from [p] to the edge, the pad taken off — `Double.MAX_VALUE` beyond [reachM]. */
    fun edgeM(p: LatLng, reachM: Double): Double {
        val d = edges.distanceM(p.latitude, p.longitude, reachM + edges.padM)
        return if (d == Double.MAX_VALUE) Double.MAX_VALUE else max(0.0, d - edges.padM)
    }

    /** The least metres from the segment [a]–[b] to the edge, the pad taken off — `MAX_VALUE` beyond [reachM]. */
    fun segmentEdgeM(a: LatLng, b: LatLng, reachM: Double): Double {
        val d = edges.segmentDistanceM(a, b, reachM + edges.padM)
        return if (d == Double.MAX_VALUE) Double.MAX_VALUE else max(0.0, d - edges.padM)
    }

    /** Whether [p] itself stands in water the gate blocks. */
    fun isShallow(p: LatLng): Boolean = edges.isShallow(p.latitude, p.longitude)

    /**
     * **Whether the gate waives itself at [p]** — within its own disc ([waiverM]) around either raw end, or on
     * an end's carved approach ([EndApproaches]), read to within [APPROACH_TOLERANCE_M]: the approach is the
     * polyline the carve stretched through its own cells' centres, so a point the walk stands on it lies on
     * it. The search's step guard, the pull, the medial nudge and the trace all read this one rule.
     */
    fun waives(p: LatLng, start: LatLng, aim: LatLng, approaches: EndApproaches): Boolean =
        MultipassPull.marginWaived(p, waiverM, start, aim, approaches, APPROACH_TOLERANCE_M)

    companion object {
        /** How near (m) a point must stand to a carved approach to stand on it. */
        const val APPROACH_TOLERANCE_M = 2.0
    }
}

/**
 * **The edge index over a loaded raster, as the live world reads it** — the gate's depth through the chart's
 * own EMODnet cutoff, the raw depth beside it for the prefilter, and the raster's own lattice with its
 * longitude side measured at the southern edge, where it is widest, so the pad holds over the whole region.
 * The raster is held weakly: an index outlives its raster only until the next search replaces it.
 */
fun shallowEdgesOver(grid: DepthGrid, cutoffM: Float, minDepthM: Double): ShallowEdgeIndex {
    val held = WeakReference(grid)
    return ShallowEdgeIndex(
        depthAt = { lat, lon ->
            held.get()?.let { depthMOf(it.depthAt(lat, lon).gatedForEmodnetShallow(cutoffM)) } ?: Double.NaN
        },
        lattice = DepthLattice(
            grid.boundingBox.latSouth, grid.boundingBox.lonWest,
            grid.cellSizeDegLat, grid.cellSizeDegLon, grid.boundingBox.latSouth
        ),
        minDepthM = minDepthM,
        rawDepthAt = { lat, lon -> held.get()?.let { depthMOf(it.depthAt(lat, lon)) } ?: Double.NaN }
    )
}
