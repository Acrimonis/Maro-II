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

    /** Per-cell land flag (candidate cell classified as land). Used to tell a
     *  seaward (deep-water-facing) contour edge from a landward (coast) edge. */
    private var landArr = BooleanArray(0)

    fun build(onProgress: (phase: String, pct: Int) -> Unit = { _, _ -> }): Zone300Data {
        val empty = Zone300Data(emptyList(), emptyList(), cellM, bandM)
        if (!index.hasData || segments.isEmpty()) return empty
        onProgress("Préparation de la zone", 3)
        setupGrid()
        if (cols < 2 || rows < 2) return empty

        val mask = sampleMask(onProgress)                 // reports 5..70
        onProgress("Tracé du contour", 75)
        val gridRings = SpatialOperations.marchingSquares(mask, cols, rows)
            .filter { it.size >= 3 }
        if (gridRings.isEmpty()) return empty

        val cleaned = ArrayList<List<LatLng>>()
        val seaward = ArrayList<List<LatLng>>()
        for (gridRing in gridRings) {
            val flags = classifySeaward(gridRing, mask)
            val ringLL = gridRing.map { gridToLatLng(it) }
            val (cleanedRing, seawardRuns) = processRing(ringLL, flags)
            if (cleanedRing.size >= 3) cleaned.add(cleanedRing)
            seaward.addAll(seawardRuns)
        }
        onProgress("Finalisation de la zone", 95)
        // Bridge red-line fragments split by noise / ring boundaries into continuous lines.
        val result = Zone300Data(groupRings(cleaned), mergeLines(seaward), cellM, bandM)
        onProgress("Terminé", 100)
        return result
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

    private fun sampleMask(onProgress: (String, Int) -> Unit = { _, _ -> }): BooleanArray {
        val nCells = cols * rows
        val flags = ByteArray(nCells)            // bitset: CAND|NEAR|COAST|SEED|FLOOD|WATER
        val dist = DoubleArray(nCells)           // per-cell distance-to-coast (open-sea anchor pick)
        val pad = ribbonM + cellM

        // 1) Mark the candidate ribbon AND rasterize the coastline as a flood barrier.
        for (seg in segments) {
            val pts = seg.points
            for (i in 0 until pts.size - 1) {
                val ax = lon2x(pts[i].lon.toDouble()); val ay = lat2y(pts[i].lat.toDouble())
                val bx = lon2x(pts[i + 1].lon.toDouble()); val by = lat2y(pts[i + 1].lat.toDouble())
                val c0 = ((min(ax, bx) - pad - x0) / cellM).toInt().coerceIn(0, cols - 1)
                val c1 = ((max(ax, bx) + pad - x0) / cellM).toInt().coerceIn(0, cols - 1)
                val r0 = ((min(ay, by) - pad - y0) / cellM).toInt().coerceIn(0, rows - 1)
                val r1 = ((max(ay, by) + pad - y0) / cellM).toInt().coerceIn(0, rows - 1)
                for (r in r0..r1) for (c in c0..c1) flags[r * cols + c] = (flags[r * cols + c].toInt() or CAND).toByte()
                rasterizeBarrier(ax, ay, bx, by, flags)
            }
        }
        // Seal the OPEN ends of the mainland (clipped at the region edge) out to the
        // grid boundary, so the water flood cannot wrap around them onto the land side.
        capOpenEnds(flags)

        // 2) Per-candidate distance + water guess (parallel). Mark NEAR (≤300 m), the
        //    raw WATER guess (ray cast — fallback only), and seaward SEEDs (reliably
        //    open water 180–300 m offshore, clear of the barrier).
        val idx = ArrayList<Int>()
        for (i in 0 until nCells) if (flags[i].toInt() and CAND != 0) idx.add(i)
        val total = idx.size
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val step = maxOf(1, total / 20)
        java.util.Arrays.stream(idx.toIntArray()).parallel().forEach { i ->
            val lat = cellLat(i / cols); val lon = cellLon(i % cols)
            val d = index.query(lat, lon).distanceMeters
            dist[i] = d
            val water = isWater(lat, lon, d)
            var f = flags[i].toInt()
            if (d <= bandM) f = f or NEAR
            if (water) f = f or WATER
            if (water && d >= SEED_MIN_M && d <= bandM && (f and COAST) == 0) f = f or SEED
            flags[i] = f.toByte()
            val c = done.incrementAndGet()
            if (c % step == 0 && total > 0) onProgress("Échantillonnage de la zone", 5 + c * 65 / total)
        }

        // 3) Flood water inward from the seaward seeds through candidate cells, blocked
        //    by the coast barrier (4-connectivity). Topology — not the per-cell ray
        //    cast — decides water vs land, so harbours/bays classify correctly.
        floodWater(flags)

        // 3b) Open-sea anchor = the deepest cell the ray cast still calls water. The real
        //     sea is its connected flood component; an inland pocket fed by a misclassified
        //     seed (an even-crossing cell behind a headland) forms a SEPARATE component —
        //     the coast barrier divides them — so keeping only the anchor's component drops
        //     the mirrored land-side band without trusting any single per-cell guess.
        var anchor = -1; var anchorD = -1.0
        for (i in idx) {
            if (flags[i].toInt() and WATER == 0) continue
            if (dist[i] > anchorD) { anchorD = dist[i]; anchor = i }
        }
        val seaComp = markSeaComponent(flags, anchor)

        // 4) Assemble. Prefer the anchored sea component; else the raw flood; else (no seed
        //    at all → degenerate) fall back to the per-cell ray-cast guess.
        var anySeed = false
        for (i in 0 until nCells) if (flags[i].toInt() and SEED != 0) { anySeed = true; break }

        val mask = BooleanArray(nCells)
        val land = BooleanArray(nCells); landArr = land
        for (i in 0 until nCells) {
            val f = flags[i].toInt()
            if (f and CAND == 0) continue
            val water = when {
                seaComp != null -> seaComp[i]
                anySeed -> f and FLOOD != 0
                else -> f and WATER != 0
            }
            if (water && (f and NEAR != 0)) mask[i] = true
            if (!water) land[i] = true
        }
        return mask
    }

    /**
     * Extends the open ends of each non-closed coastline (the mainland, clipped at the
     * region boundary) out to the grid edge along the coast's tangent, so the barrier
     * is sealed and the water flood cannot wrap around onto the land side. Closed rings
     * (islands) are already sealed and are skipped.
     */
    private fun capOpenEnds(flags: ByteArray) {
        for (seg in segments) {
            if (seg.isClosed) continue
            val pts = seg.points
            if (pts.size < 2) continue
            val n = pts.size
            capFromEnd(
                lon2x(pts[0].lon.toDouble()), lat2y(pts[0].lat.toDouble()),
                lon2x(pts[1].lon.toDouble()), lat2y(pts[1].lat.toDouble()), flags
            )
            capFromEnd(
                lon2x(pts[n - 1].lon.toDouble()), lat2y(pts[n - 1].lat.toDouble()),
                lon2x(pts[n - 2].lon.toDouble()), lat2y(pts[n - 2].lat.toDouble()), flags
            )
        }
    }

    /** Rasterize a barrier from endpoint (px,py) along the outward tangent (away from
     *  the inner neighbour qx,qy) until it reaches the grid boundary. */
    private fun capFromEnd(px: Double, py: Double, qx: Double, qy: Double, flags: ByteArray) {
        var dx = px - qx; var dy = py - qy
        val len = kotlin.math.hypot(dx, dy)
        if (len < 1e-6) return
        dx /= len; dy /= len
        val xMin = x0; val xMax = x0 + (cols - 1) * cellM
        val yMin = y0; val yMax = y0 + (rows - 1) * cellM
        var t = Double.MAX_VALUE
        if (dx > 1e-9) t = min(t, (xMax - px) / dx) else if (dx < -1e-9) t = min(t, (xMin - px) / dx)
        if (dy > 1e-9) t = min(t, (yMax - py) / dy) else if (dy < -1e-9) t = min(t, (yMin - py) / dy)
        if (t == Double.MAX_VALUE || t <= 0.0) return
        rasterizeBarrier(px, py, px + dx * t, py + dy * t, flags)
    }

    /** Rasterizes a segment into the [COAST] flood barrier, densely enough (≈⅓ cell
     *  steps) that the barrier is 8-connected and a 4-connected flood cannot leak. */
    private fun rasterizeBarrier(ax: Double, ay: Double, bx: Double, by: Double, flags: ByteArray) {
        val len = kotlin.math.hypot(bx - ax, by - ay)
        val steps = maxOf(1, kotlin.math.ceil(len / (cellM / 3.0)).toInt())
        for (s in 0..steps) {
            val t = s.toDouble() / steps
            val c = ((ax + (bx - ax) * t - x0) / cellM).toInt()
            val r = ((ay + (by - ay) * t - y0) / cellM).toInt()
            if (r in 0 until rows && c in 0 until cols) {
                val i = r * cols + c
                flags[i] = (flags[i].toInt() or COAST).toByte()
            }
        }
    }

    /** 4-connected flood of [FLOOD] from [SEED] cells through candidate, non-barrier cells. */
    private fun floodWater(flags: ByteArray) {
        val queue = ArrayDeque<Int>()
        for (i in flags.indices) {
            if (flags[i].toInt() and SEED != 0 && flags[i].toInt() and FLOOD == 0) {
                flags[i] = (flags[i].toInt() or FLOOD).toByte()
                queue.addLast(i)
            }
        }
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val r = cur / cols; val c = cur % cols
            var k = 0
            while (k < 4) {
                val nr = r + DR[k]; val nc = c + DC[k]; k++
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue
                val ni = nr * cols + nc
                val nf = flags[ni].toInt()
                if (nf and CAND == 0 || nf and COAST != 0 || nf and FLOOD != 0) continue
                flags[ni] = (nf or FLOOD).toByte()
                queue.addLast(ni)
            }
        }
    }

    /**
     * Marks the connected flood component of the open-sea [anchor] (the deepest cell the
     * ray cast still calls water — unambiguous open sea). Inland pockets reached by a
     * misclassified seed are separate components (the coast barrier divides the flood),
     * so they are left out and never produce a mirrored land-side band. Returns `null`
     * when the anchor is unusable (no water cell, or it was not reached by the flood), so
     * the caller falls back to the raw flood.
     */
    private fun markSeaComponent(flags: ByteArray, anchor: Int): BooleanArray? {
        if (anchor < 0 || flags[anchor].toInt() and FLOOD == 0) return null
        val keep = BooleanArray(flags.size)
        val queue = ArrayDeque<Int>()
        keep[anchor] = true; queue.addLast(anchor)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val r = cur / cols; val c = cur % cols
            var k = 0
            while (k < 4) {
                val nr = r + DR[k]; val nc = c + DC[k]; k++
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue
                val ni = nr * cols + nc
                if (keep[ni] || flags[ni].toInt() and FLOOD == 0) continue
                keep[ni] = true; queue.addLast(ni)
            }
        }
        return keep
    }

    // ── Seaward/landward classification (by what's OUTSIDE each contour edge) ──

    /**
     * For each ring vertex, classify *seaward* (the out-of-zone cell it borders is
     * deep water / open sea) vs *landward* (the out cell is land). This is robust for
     * narrow bands where absolute distance-to-coast would misclassify.
     */
    private fun classifySeaward(ring: List<SpatialOperations.GridPt>, mask: BooleanArray): BooleanArray =
        BooleanArray(ring.size) { k ->
            val out = outCellOf(ring[k], mask)
            out < 0 || !landArr[out]      // off-grid or non-land out cell → deep water → seaward
        }

    /** Index of the out-of-zone (mask == false) cell adjacent to a contour vertex,
     *  or -1 if that cell is off the grid (treated as open sea). */
    private fun outCellOf(gp: SpatialOperations.GridPt, mask: BooleanArray): Int {
        val col2 = Math.round(gp.col * 2).toInt()
        val row2 = Math.round(gp.row * 2).toInt()
        return if (col2 % 2 != 0) {                       // horizontal edge → cells left/right
            val r = row2 / 2
            pickOut(idxOrNeg(r, (col2 - 1) / 2), idxOrNeg(r, (col2 + 1) / 2), mask)
        } else {                                          // vertical edge → cells above/below
            val c = col2 / 2
            pickOut(idxOrNeg((row2 - 1) / 2, c), idxOrNeg((row2 + 1) / 2, c), mask)
        }
    }

    private fun idxOrNeg(r: Int, c: Int): Int =
        if (r in 0 until rows && c in 0 until cols) r * cols + c else -1

    /** Of two adjacent cells (one in-zone, one out), return the out one. */
    private fun pickOut(a: Int, b: Int, mask: BooleanArray): Int {
        val aIn = a >= 0 && mask[a]
        return if (aIn) b else a
    }

    // ── Ring processing ───────────────────────────────────────────────────────

    /** Returns the cleaned closed fill ring + the seaward (red-line) runs it yields.
     *  [rawSeaward] is the per-vertex seaward/landward classification (by out-cell). */
    private fun processRing(
        ring: List<LatLng>,
        rawSeaward: BooleanArray
    ): Pair<List<LatLng>, List<List<LatLng>>> {
        val n = ring.size
        // De-noise: a lone misclassified vertex must not split a run (red-line break).
        val seaward = denoiseLabels(rawSeaward, MIN_LABEL_RUN)

        // ── Fill ring ──────────────────────────────────────────────────────────
        // Keep seaward vertices on the 300 m contour; snap landward vertices flush
        // onto the coast — PER VERTEX, preserving ring order. No run re-stitching and
        // no coastline sub-path walks, so the loop stays a simple closed polygon (no
        // self-intersections / chords / overlapping fill). Then simplify + smooth.
        val fillPts = ArrayList<LatLng>(n)
        for (k in 0 until n) {
            fillPts.add(
                if (seaward[k]) ring[k]
                else index.query(ring[k].latitude, ring[k].longitude).closestPoint
            )
        }
        val fill = if (fillPts.size >= 4)
            SpatialOperations.chaikin(
                SpatialOperations.douglasPeucker(fillPts, dpEpsilonM), chaikinIters, closed = true
            )
        else fillPts

        // ── Red line: seaward runs only ──────────────────────────────────────────
        val seawardRuns = ArrayList<List<LatLng>>()
        if (seaward.all { it }) {
            val sm = smoothSeaward(ring, closed = true)
            if (sm.size >= 3) seawardRuns.add(sm + sm.first())
        } else if (seaward.any { it }) {
            var start = 0
            for (i in 0 until n) if (seaward[i] != seaward[(i - 1 + n) % n]) { start = i; break }
            var i = 0
            while (i < n) {
                val cls = seaward[(start + i) % n]
                val run = ArrayList<LatLng>()
                var j = i
                while (j < n && seaward[(start + j) % n] == cls) { run.add(ring[(start + j) % n]); j++ }
                if (cls) {
                    val sm = smoothSeaward(run, closed = false)
                    if (sm.size >= 2) seawardRuns.add(sm)
                }
                i = j
            }
        }
        return Pair(fill, seawardRuns)
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

    /**
     * Greedily joins red-line fragments whose endpoints are within [BRIDGE_M] into
     * continuous polylines, so the seaward line reads unbroken across small noise
     * gaps and marching-squares ring boundaries. Genuinely separate bands stay
     * separate (their endpoints are far apart).
     */
    private fun mergeLines(lines: List<List<LatLng>>): List<List<LatLng>> {
        val remaining = lines.filter { it.size >= 2 }.map { ArrayList(it) }.toMutableList()
        val result = ArrayList<List<LatLng>>()
        fun d(a: LatLng, b: LatLng) = SpatialOperations.haversine(a, b)

        while (remaining.isNotEmpty()) {
            val chain = remaining.removeAt(remaining.size - 1)
            var extended = true
            while (extended) {
                extended = false
                // Extend at the tail.
                run {
                    val tail = chain.last()
                    var bi = -1; var rev = false; var best = BRIDGE_M
                    for (i in remaining.indices) {
                        val ln = remaining[i]
                        val ds = d(tail, ln.first()); if (ds < best) { best = ds; bi = i; rev = false }
                        val de = d(tail, ln.last());  if (de < best) { best = de; bi = i; rev = true }
                    }
                    if (bi >= 0) {
                        val ln = remaining.removeAt(bi)
                        chain.addAll(if (rev) ln.asReversed() else ln)
                        extended = true
                    }
                }
                if (extended) continue
                // Extend at the head.
                val head = chain.first()
                var bi = -1; var rev = false; var best = BRIDGE_M
                for (i in remaining.indices) {
                    val ln = remaining[i]
                    val ds = d(head, ln.first()); if (ds < best) { best = ds; bi = i; rev = true }
                    val de = d(head, ln.last());  if (de < best) { best = de; bi = i; rev = false }
                }
                if (bi >= 0) {
                    val ln = remaining.removeAt(bi)
                    chain.addAll(0, if (rev) ln.asReversed() else ln)
                    extended = true
                }
            }
            result.add(chain)
        }
        return result
    }

    private fun smoothSeaward(run: List<LatLng>, closed: Boolean): List<LatLng> {
        if (run.size < 3) return run
        val dp = SpatialOperations.douglasPeucker(run, dpEpsilonM)
        return SpatialOperations.chaikin(dp, chaikinIters, closed)
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

        /** Max endpoint gap (m) to bridge two red-line fragments into one line. */
        const val BRIDGE_M = 45.0

        // Mask cell bit flags (packed into one ByteArray to save memory).
        const val CAND = 1
        const val NEAR = 2
        const val COAST = 4
        const val SEED = 8
        const val FLOOD = 16
        const val WATER = 32

        /** Min offshore distance (m) for a flood seed — far enough that the ray cast
         *  is reliable on the open coast. */
        const val SEED_MIN_M = 180.0

        // 4-connected neighbour offsets.
        val DR = intArrayOf(-1, 1, 0, 0)
        val DC = intArrayOf(0, 0, -1, 1)
    }
}
