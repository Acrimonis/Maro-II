package ykws.android.maro.spatial

import ykws.android.maro.data.model.BandPolygon
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.Zone300Data
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * Builds the precomputed geometry of the **300 m regulatory band** from the
 * coastline distance field. Pure JVM — no Android dependencies — so it is fully
 * unit-testable.
 *
 * Algorithm (see `docs/300MLinePlan.md` §3.3):
 *  1. Lay a uniform grid over a 0–[ribbonM] m **near-coast ribbon** (cells near the
 *     coast geometry only — never the open sea).
 *  2. Sample a binary mask: `isWater && distanceToCoast ≤ bandM` (parallel).
 *  3. Marching squares → closed rings.
 *  4. Classify each ring vertex *seaward* (d≈[bandM]) vs *landward* (d≈0).
 *  5. Smooth seaward runs (Douglas–Peucker → Chaikin); snap landward runs to the
 *     real coastline so the fill stays flush with the coast and never on land.
 *  6. Group rings into fill polygons with holes (island land = hole).
 *
 * The fill is **water only**: land (mainland or island) is `!isWater` and is never
 * filled. The red boundary line is the **seaward runs only**.
 *
 * @param index    Spatial index over [segments] (nearest distance + closest point).
 * @param segments Coastline polylines, same list the index was built from
 *                 (`allSegments`: [0] = mainland, 1..N = islands).
 * @param refLat   Reference latitude for the local planar projection.
 * @param isWater  Water/land predicate `(lat, lon, distToCoastM) -> Boolean`.
 */
class Zone300Builder(
    private val index: CoastlineSpatialIndex,
    private val segments: List<CoastlineSegment>,
    private val refLat: Double,
    private val isWater: (lat: Double, lon: Double, distM: Double) -> Boolean,
    private val bandM: Double = 300.0,
    private val ribbonM: Double = 500.0,
    private val cellM: Double = 15.0,
    private val dpEpsilonM: Double = 15.0,
    private val chaikinIters: Int = 2,
    /** Fill holes smaller than this (m²) — removes spurious water-gap noise while
     *  keeping real island holes. */
    private val minHoleAreaM2: Double = 6_000.0,
) {
    private val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    private val mPerDegLon = mPerDegLat * cos(Math.toRadians(refLat))

    private fun lon2x(lon: Double) = lon * mPerDegLon
    private fun lat2y(lat: Double) = lat * mPerDegLat
    private fun x2lon(x: Double) = x / mPerDegLon
    private fun y2lat(y: Double) = y / mPerDegLat

    private var x0 = 0.0
    private var y0 = 0.0
    private var cols = 0
    private var rows = 0

    fun build(): Zone300Data {
        val empty = Zone300Data(emptyList(), emptyList(), cellM, bandM)
        if (!index.hasData || segments.isEmpty()) return empty
        setupGrid()
        if (cols < 2 || rows < 2) return empty

        val mask = sampleMask()
        val rings = SpatialOperations.marchingSquares(mask, cols, rows)
            .map { ring -> ring.map { gridToLatLng(it) } }
            .filter { it.size >= 3 }
        if (rings.isEmpty()) return empty

        val cleaned = ArrayList<List<LatLng>>()
        val seaward = ArrayList<List<LatLng>>()
        for (ring in rings) {
            val (cleanedRing, seawardRuns) = processRing(ring)
            if (cleanedRing.size >= 3) cleaned.add(cleanedRing)
            seaward.addAll(seawardRuns)
        }
        // Bridge red-line fragments split by noise / ring boundaries into continuous lines.
        return Zone300Data(groupRings(cleaned), mergeLines(seaward), cellM, bandM)
    }

    // ── Grid setup ───────────────────────────────────────────────────────────

    private fun setupGrid() {
        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        for (seg in segments) for (p in seg.points) {
            val x = lon2x(p.lon.toDouble()); val y = lat2y(p.lat.toDouble())
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
        }
        if (minX > maxX) return
        val margin = ribbonM + 2.0 * cellM   // guarantees a false border → closed contours
        x0 = minX - margin; y0 = minY - margin
        cols = ceil((maxX - minX + 2.0 * margin) / cellM).toInt() + 1
        rows = ceil((maxY - minY + 2.0 * margin) / cellM).toInt() + 1
    }

    private fun cellLat(r: Int) = y2lat(y0 + r * cellM)
    private fun cellLon(c: Int) = x2lon(x0 + c * cellM)
    private fun gridToLatLng(gp: SpatialOperations.GridPt) =
        LatLng(y2lat(y0 + gp.row * cellM), x2lon(x0 + gp.col * cellM))

    // ── Mask sampling (parallel) ──────────────────────────────────────────────

    private fun sampleMask(): BooleanArray {
        val mask = BooleanArray(cols * rows)
        val candidate = BooleanArray(cols * rows)
        val pad = ribbonM + cellM
        for (seg in segments) {
            val pts = seg.points
            for (i in 0 until pts.size - 1) {
                val ax = lon2x(pts[i].lon.toDouble()); val ay = lat2y(pts[i].lat.toDouble())
                val bx = lon2x(pts[i + 1].lon.toDouble()); val by = lat2y(pts[i + 1].lat.toDouble())
                val c0 = ((min(ax, bx) - pad - x0) / cellM).toInt().coerceIn(0, cols - 1)
                val c1 = ((max(ax, bx) + pad - x0) / cellM).toInt().coerceIn(0, cols - 1)
                val r0 = ((min(ay, by) - pad - y0) / cellM).toInt().coerceIn(0, rows - 1)
                val r1 = ((max(ay, by) + pad - y0) / cellM).toInt().coerceIn(0, rows - 1)
                for (r in r0..r1) for (c in c0..c1) candidate[r * cols + c] = true
            }
        }
        val idx = ArrayList<Int>()
        for (i in candidate.indices) if (candidate[i]) idx.add(i)
        // Parallel: distinct-index writes are safe; index/isWater are read-only.
        java.util.Arrays.stream(idx.toIntArray()).parallel().forEach { i ->
            val lat = cellLat(i / cols); val lon = cellLon(i % cols)
            val d = index.query(lat, lon).distanceMeters
            if (d <= bandM && isWater(lat, lon, d)) mask[i] = true
        }
        return mask
    }

    // ── Ring processing ───────────────────────────────────────────────────────

    /** Returns the cleaned closed fill ring + the seaward (red-line) runs it yields. */
    private fun processRing(ring: List<LatLng>): Pair<List<LatLng>, List<List<LatLng>>> {
        val n = ring.size
        val rawSeaward = BooleanArray(n) {
            index.query(ring[it].latitude, ring[it].longitude).distanceMeters >= bandM * 0.5
        }
        // De-noise: a lone vertex dipping across the 150 m threshold must not split a
        // run (which would break the red line). Flip runs shorter than MIN_LABEL_RUN.
        val seaward = denoiseLabels(rawSeaward, MIN_LABEL_RUN)
        if (seaward.all { it }) {
            val smooth = smoothSeaward(ring, closed = true)
            val redClosed = if (smooth.size >= 2) smooth + smooth.first() else smooth
            return Pair(smooth, if (smooth.size >= 3) listOf(redClosed) else emptyList())
        }
        if (seaward.none { it }) {
            return Pair(snapRingToCoast(ring), emptyList())
        }
        // Mixed: rotate to begin at a class transition.
        var start = 0
        for (i in 0 until n) {
            if (seaward[i] != seaward[(i - 1 + n) % n]) { start = i; break }
        }
        val cleaned = ArrayList<LatLng>()
        val seawardRuns = ArrayList<List<LatLng>>()
        var i = 0
        while (i < n) {
            val cls = seaward[(start + i) % n]
            val run = ArrayList<LatLng>()
            var j = i
            while (j < n && seaward[(start + j) % n] == cls) { run.add(ring[(start + j) % n]); j++ }
            if (cls) {
                val sm = smoothSeaward(run, closed = false)
                cleaned.addAll(sm)
                if (sm.size >= 2) seawardRuns.add(sm)
            } else {
                cleaned.addAll(snapLandwardRun(run))
            }
            i = j
        }
        return Pair(cleaned, seawardRuns)
    }

    /**
     * Flips classification runs shorter than [minRun] to their neighbours' class,
     * removing single-vertex threshold noise. Non-cyclic (the wrap-around run is left
     * as-is) — sufficient to de-jitter the red line without affecting topology.
     */
    private fun denoiseLabels(flags: BooleanArray, minRun: Int): BooleanArray {
        val n = flags.size
        if (n < 2 * minRun + 1) return flags
        val out = flags.copyOf()
        var changed = true
        var guard = 0
        while (changed && guard < 4) {
            changed = false; guard++
            var i = 0
            while (i < n) {
                val cls = out[i]
                var j = i
                while (j < n && out[j] == cls) j++
                val len = j - i
                if (len < minRun && i > 0 && j < n && out[i - 1] != cls && out[j] != cls) {
                    for (k in i until j) out[k] = !cls
                    changed = true
                }
                i = j
            }
        }
        return out
    }

    private fun smoothSeaward(run: List<LatLng>, closed: Boolean): List<LatLng> {
        if (run.size < 3) return run
        val dp = SpatialOperations.douglasPeucker(run, dpEpsilonM)
        return SpatialOperations.chaikin(dp, chaikinIters, closed)
    }

    /** Snap every vertex of a closed (all-landward) ring onto the nearest coast point. */
    private fun snapRingToCoast(ring: List<LatLng>): List<LatLng> =
        ring.map { index.query(it.latitude, it.longitude).closestPoint }

    /**
     * Replace a landward run with the actual coastline sub-path between its endpoints
     * (flush to the coast). Falls back to per-vertex snapping if the endpoints map to
     * different polylines or the index span wraps the long way around.
     */
    private fun snapLandwardRun(run: List<LatLng>): List<LatLng> {
        if (run.isEmpty()) return run
        val qs = index.query(run.first().latitude, run.first().longitude)
        val qe = index.query(run.last().latitude, run.last().longitude)
        if (qs.polylineIdx >= 0 && qs.polylineIdx == qe.polylineIdx &&
            qs.vertexIndex >= 0 && qe.vertexIndex >= 0
        ) {
            val poly = segments[qs.polylineIdx].points
            val i0 = qs.vertexIndex; val i1 = qe.vertexIndex
            if (abs(i0 - i1) <= poly.size / 2) {
                val path = ArrayList<LatLng>()
                path.add(qs.closestPoint)
                if (i0 <= i1) for (k in i0 + 1..i1) path.add(LatLng(poly[k].lat.toDouble(), poly[k].lon.toDouble()))
                else for (k in i0 downTo i1 + 1) path.add(LatLng(poly[k].lat.toDouble(), poly[k].lon.toDouble()))
                path.add(qe.closestPoint)
                return path
            }
        }
        return run.map { index.query(it.latitude, it.longitude).closestPoint }
    }

    // ── Ring grouping (fill outers + island holes) ────────────────────────────

    private fun groupRings(rings: List<List<LatLng>>): List<BandPolygon> {
        if (rings.isEmpty()) return emptyList()
        val xy = rings.map { ring -> ring.map { doubleArrayOf(lon2x(it.longitude), lat2y(it.latitude)) } }
        val area = xy.map { abs(signedArea(it)) }

        // Immediate (smallest-area strictly-larger) container, and total container count.
        val parent = IntArray(rings.size) { -1 }
        val depth = IntArray(rings.size)
        for (i in rings.indices) {
            var bestArea = Double.MAX_VALUE
            for (j in rings.indices) {
                if (i == j || area[j] <= area[i]) continue
                if (pointInPoly(xy[i][0], xy[j])) {
                    depth[i]++
                    if (area[j] < bestArea) { bestArea = area[j]; parent[i] = j }
                }
            }
        }

        val result = ArrayList<BandPolygon>()
        for (o in rings.indices) {
            if (depth[o] % 2 != 0) continue          // odd depth → it's a hole, not an outer
            val holes = rings.indices
                .filter { parent[it] == o && depth[it] % 2 == 1 && area[it] >= minHoleAreaM2 }
                .map { rings[it] }
            result.add(BandPolygon(rings[o], holes))
        }
        return result
    }

    private fun signedArea(poly: List<DoubleArray>): Double {
        var s = 0.0
        for (i in poly.indices) {
            val p = poly[i]; val q = poly[(i + 1) % poly.size]
            s += p[0] * q[1] - q[0] * p[1]
        }
        return s / 2.0
    }

    private fun pointInPoly(pt: DoubleArray, poly: List<DoubleArray>): Boolean {
        var inside = false
        val x = pt[0]; val y = pt[1]
        var j = poly.size - 1
        for (i in poly.indices) {
            val xi = poly[i][0]; val yi = poly[i][1]
            val xj = poly[j][0]; val yj = poly[j][1]
            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) inside = !inside
            j = i
        }
        return inside
    }

    private companion object {
        /** Minimum classification run length; shorter runs are de-noised away. */
        const val MIN_LABEL_RUN = 3
    }
}
