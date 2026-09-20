package ykws.android.maro.data.route

import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import ykws.android.maro.config.AppConfig
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.operation.distance.DistanceOp
import org.locationtech.jts.operation.distance.IndexedFacetDistance
import ykws.android.maro.BuildConfig
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.RouteEngineDetails
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.spatial.RouteEngine
import ykws.android.maro.spatial.RouteTurnGeometry
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import ykws.android.maro.spatial.mesh.MeshRouteEngine
import ykws.android.maro.spatial.mesh.RouteMeshDetails
import ykws.android.maro.spatial.mesh.RouteSearch
import ykws.android.maro.spatial.mesh.meshDetailsOrEmpty
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * **A reading, never a gate — Round 0 of the trajectory-quality study.**
 *
 * It asserts nothing. It prints three things and is judged by eye, because two of the questions it
 * answers (is a whole bay missing from the routable water, and how jagged is the line the app draws)
 * have no pass/fail until someone has seen the numbers once:
 *
 * 1. **The gap scan** — the mesh box walked on a coarse grid; every cell the water oracle calls water
 *    is classified by whether a mesh node stands within the coarsest legitimate spacing. The cells
 *    that hold water and no node are clustered into areas and reported with their own depths, which
 *    is what names the cause per area: *all shallower than the gate* is the depth gate doing its job
 *    or a data artefact, *deep but unmeshed* is the erosion, the connectivity pass or a box edge.
 * 2. **The coverage line** — meshed water against water, so "a bay is missing" is an area.
 * 3. **The route metrics** — for one long pair and for a pair drawn from each of the largest zones:
 *    vertex count, total turning, the worst single turn, the minimum distance from the line to a zone
 *    boundary, length and ETA. These six numbers are the baseline every later round is judged on.
 *
 * It reads the world through [PrebakedRouteGeometry] and the mesh through [RouteMeshSerializer] — the
 * bake's own geometry and the app's own reader — so the numbers describe the shipped artefact. It
 * never opens a `.bin` by hand and it changes nothing it reads.
 *
 * Gated by `-Dmaro.prebake=true`, and skipping (rather than failing) when the inputs are absent: a
 * probe that refuses would make `gradlew test` depend on a bake.
 */
class RouteTrajectoryProbeTest {

    @Test
    fun probeTheBakedMeshAndTheLineItDraws() {
        Assume.assumeTrue(
            "set -Dmaro.prebake=true to run the route probe",
            System.getProperty("maro.prebake") == "true"
        )

        val region = BuildConfig.REGION_ID
        val repoDir = System.getProperty("maro.repoDir")?.let { File(it) } ?: File("..")
        val meshFile = File(repoDir, "data/app-assets/route/$region.bin")
        val missing = (PrebakedInputs.paths(repoDir, region) + meshFile).filterNot { it.exists() }
        Assume.assumeTrue(
            "probe skipped — no baked world to read: ${missing.joinToString { it.path }}",
            missing.isEmpty()
        )

        val inputs = PrebakedInputs.load(repoDir, region)
        val geometry = PrebakedRouteGeometry(inputs.coast, inputs.zones, inputs.depth)
        val mesh = RouteMeshSerializer.deserialize(meshFile.readBytes())

        val depthCellM = inputs.depth.cellSizeDegLat * M_PER_DEG_LAT
        println(
            "route probe [$region]: ${mesh.nodeCount} node(s), ${mesh.edgeCount} edge(s), " +
                "${mesh.triangleCount} triangle(s), ${mesh.nodeComponent.toSet().size} stretch(es); " +
                "longest edge ${"%.2f".format(mesh.edgeLengthM.max() / 1000.0)} km; " +
                "depth grid cell ${"%.0f".format(depthCellM)} m; shipped cap " +
                "${AppConfig.routeTurnLateralAccelMps2} m/s2, shipped berth " +
                "${AppConfig.routeZoneBerthM} m"
        )

        printCorridors(mesh, geometry, depthCellM)
        printAdmittedUnsoundedArea(mesh, geometry)

        val scan = scanForGaps(geometry, mesh)
        printGapReport(scan, geometry)

        printWindowProbe(geometry, mesh)

        val queries = PrebakedQueries(geometry, inputs.zones)
        val rings = geometry.zoneRings()
        // **The inshore pairs lead every reading**, and the corridor-long pair is the reference beside
        // them rather than the subject: the pass was chosen and judged on the corridor's own water, and
        // that is the substitution this reading exists to undo.
        val inshore = inshorePairs(mesh, geometry)
        val corridor = metricPairs(mesh, rings)
        val pairs = inshore + corridor
        val engine = MeshRouteEngine.overMesh(mesh, queries)
        val samples = metricRoutes(engine, pairs, rings)
        printRouteMetrics(samples)
        printShortcutEffect(mesh, queries, pairs, rings)
        printPassages(geometry, samples)
        printGeometryAllowance(mesh, queries, pairs)
        printCapSweep("corridor", mesh, queries, corridor, rings)
        printCapSweep("inshore", mesh, queries, inshore, rings)
        printBerthSweep(mesh, queries, pairs, rings)
    }

    // ── The long edges, which is what a corridor must never become ────────────

    /**
     * **Every candidate corridor, walked along its whole length rather than sampled once at its
     * middle.**
     *
     * A bridge through unsounded water is a corridor of triangles, and the worst shape one can take is
     * a sliver spanning many times the refinement's own spacing: the broad version of the rule left a
     * 71 km two-node edge across the corridor. One midpoint sample cannot tell that sliver from a long
     * edge along a simplified coastline — whose vertices stand kilometres apart by design, which is
     * the case the midpoint test was meant to let through — and an 11.95 km edge certified on a single
     * reading is exactly the sparse-sample evidence [`RouteSearch`] itself refuses to price a crossing
     * on, so it cannot certify a bake either.
     *
     * Every edge over [CORRIDOR_EDGE_M] is walked at the **depth grid's own cell size**, the finest
     * resolution the soundings have, and reported with its length, its longest unsounded run and the
     * shallowest sounding on it: a sliver through the unsounded sea reads as one long run with no
     * sounding at all, an edge along a sounded coast as the opposite.
     */
    private fun printCorridors(
        mesh: ykws.android.maro.data.model.RouteMeshArrays,
        geometry: PrebakedRouteGeometry,
        depthCellM: Double
    ) {
        var longestM = 0f
        val candidates = ArrayList<Int>()
        for (e in 0 until mesh.edgeCount) {
            val lengthM = mesh.edgeLengthM[e]
            if (lengthM > longestM) longestM = lengthM
            if (lengthM > CORRIDOR_EDGE_M) candidates.add(e)
        }
        candidates.sortByDescending { mesh.edgeLengthM[it] }
        println(
            "route probe corridors: ${candidates.size} edge(s) over ${CORRIDOR_EDGE_M.toInt()} m of " +
                "${mesh.edgeCount}, longest ${"%.2f".format(longestM / 1000f)} km — each read at the " +
                "depth grid's own ${"%.0f".format(depthCellM)} m cell"
        )
        for (e in candidates.take(MAX_REPORTED_CORRIDORS)) {
            val from = mesh.edgeFrom[e]
            val to = mesh.edgeTo[e]
            val lengthM = mesh.edgeLengthM[e].toDouble()
            val steps = max(2, kotlin.math.ceil(lengthM / depthCellM).toInt())
            val stepM = lengthM / steps
            var run = 0.0
            var longestRun = 0.0
            var unsounded = 0
            var minDepth = Double.NaN
            for (k in 0..steps) {
                val t = k.toDouble() / steps
                val lat = mesh.pointsLat[from] + (mesh.pointsLat[to] - mesh.pointsLat[from]) * t
                val lon = mesh.pointsLon[from] + (mesh.pointsLon[to] - mesh.pointsLon[from]) * t
                val depth = geometry.depthM(lat, lon)
                if (depth.isFinite()) {
                    if (!minDepth.isFinite() || depth < minDepth) minDepth = depth
                    run = 0.0
                } else {
                    unsounded++
                    run += stepM
                    if (run > longestRun) longestRun = run
                }
            }
            println(
                "route probe corridor ${"%.2f".format(lengthM / 1000.0)} km · " +
                    "${steps + 1} sample(s) at ${"%.0f".format(stepM)} m · longest unsounded run " +
                    "${"%.2f".format(longestRun / 1000.0)} km · $unsounded/${steps + 1} sample(s) " +
                    "unsounded · shallowest sounding " +
                    "${if (minDepth.isFinite()) "%.1f".format(minDepth) + " m" else "none"} · " +
                    "ends depth ${depthAt(geometry, mesh, from)} / ${depthAt(geometry, mesh, to)}"
            )
        }
        if (candidates.size > MAX_REPORTED_CORRIDORS) {
            println(
                "route probe corridors: ${candidates.size - MAX_REPORTED_CORRIDORS} shorter one(s) " +
                    "not printed"
            )
        }
    }

    // ── The D4 tripwire, as the area it actually names ────────────────────────

    /**
     * **The area of unsounded water the mesh admits, against the area it routes over.**
     *
     * The D4 tripwire is not a node count, a byte count or a stretch count: those are cheap to read
     * and a pass can multiply any of them while admitting almost no water at all — a corridor costs
     * nodes in proportion to the spacing it crosses, not to the sea it opens. What the decision is
     * about is **area**, so area is what the reading reports: the unsounded water the bridging pass
     * let into the mesh, as a fraction of the water that routes today.
     *
     * A triangle counts as admitted-unsounded when any one of its vertices has no sounding. That is
     * exact rather than a heuristic, because the strict gate keeps only triangles all of whose
     * vertices are sounded and deep enough: every triangle holding an unsounded vertex can only have
     * come from the bridging pass.
     */
    private fun printAdmittedUnsoundedArea(
        mesh: ykws.android.maro.data.model.RouteMeshArrays,
        geometry: PrebakedRouteGeometry
    ) {
        if (mesh.triangleCount == 0) {
            println("route probe D4 area: the mesh carries no triangles to measure")
            return
        }
        val frame = MetreFrame(mesh.box)
        var totalM2 = 0.0
        var unsoundedM2 = 0.0
        for (t in 0 until mesh.triangleCount) {
            val areaM2 = triangleAreaM2(frame, mesh, mesh.triA[t], mesh.triB[t], mesh.triC[t])
            totalM2 += areaM2
            if (!soundedAt(geometry, mesh, mesh.triA[t]) ||
                !soundedAt(geometry, mesh, mesh.triB[t]) ||
                !soundedAt(geometry, mesh, mesh.triC[t])
            ) {
                unsoundedM2 += areaM2
            }
        }
        println(
            "route probe D4 area: ${"%.1f".format(unsoundedM2 / 1_000_000.0)} km2 of unsounded water " +
                "admitted against ${"%.1f".format(totalM2 / 1_000_000.0)} km2 that routes today " +
                "(${"%.2f".format(100.0 * unsoundedM2 / totalM2.coerceAtLeast(1e-9))}%) — the area D4 " +
                "names, not a node, byte or stretch count"
        )
    }

    /** Whether a node has a sounding at all — the gate's own reading of "unknown". */
    private fun soundedAt(
        geometry: PrebakedRouteGeometry,
        mesh: ykws.android.maro.data.model.RouteMeshArrays,
        node: Int
    ): Boolean = geometry.depthM(mesh.pointsLat[node], mesh.pointsLon[node]).isFinite()

    /** One kept triangle's area (m²), in the bake's own metre frame so it is the area it was kept at. */
    private fun triangleAreaM2(
        frame: MetreFrame,
        mesh: ykws.android.maro.data.model.RouteMeshArrays,
        a: Int,
        b: Int,
        c: Int
    ): Double {
        val pa = frame.coordinate(mesh.point(a))
        val pb = frame.coordinate(mesh.point(b))
        val pc = frame.coordinate(mesh.point(c))
        return abs((pb.x - pa.x) * (pc.y - pa.y) - (pb.y - pa.y) * (pc.x - pa.x)) / 2.0
    }

    /** The depth reading at one node, spelled the way the gate reads it. */
    private fun depthAt(
        geometry: PrebakedRouteGeometry,
        mesh: ykws.android.maro.data.model.RouteMeshArrays,
        node: Int
    ): String {
        val depth = geometry.depthM(mesh.pointsLat[node], mesh.pointsLon[node])
        return if (depth.isNaN()) "none" else "%.1f".format(depth)
    }

    // ── The window probe ──────────────────────────────────────────────────────

    /**
     * A fine look at one small area, driven by `-Dmaro.probeWindow=lat,lon,halfSideM`.
     *
     * The global scan works at 200 m and samples one depth cell in sixty-four, which walks straight
     * past a narrow channel, a 25 m soundings cell or a rim — and a bay reported missing has to be
     * judged at the soundings' own resolution. One character per 25 m cell, so the shape is seen
     * rather than inferred.
     */
    private fun printWindowProbe(
        geometry: PrebakedRouteGeometry,
        mesh: ykws.android.maro.data.model.RouteMeshArrays
    ) {
        // The build always defines the property (an empty default), so "absent" is blank, not null:
        // reading it with `?: return` alone let the assume below skip the whole test — and with it
        // every metric printed after this point — on an ordinary run.
        val spec = System.getProperty(PROBE_WINDOW)?.takeIf { it.isNotBlank() } ?: return
        val parts = spec.split(",")
        Assume.assumeTrue("$PROBE_WINDOW must be lat,lon,halfSideM — got '$spec'", parts.size == 3)
        val centreLat = parts[0].trim().toDouble()
        val centreLon = parts[1].trim().toDouble()
        val halfSideM = parts[2].trim().toDouble()
        val stepM = WINDOW_CELL_M
        val steps = (halfSideM * 2 / stepM).toInt().coerceIn(8, MAX_WINDOW_STEPS)
        val dLat = stepM / M_PER_DEG_LAT
        val dLon = stepM / (M_PER_DEG_LAT * cos(centreLat * PI / 180.0))
        val latSouth = centreLat - halfSideM / M_PER_DEG_LAT
        val lonWest = centreLon - halfSideM / (M_PER_DEG_LAT * cos(centreLat * PI / 180.0))

        // Which window cells hold a node, so the picture can mark where the mesh actually reaches.
        val nodeCells = HashSet<Long>()
        for (n in 0 until mesh.nodeCount) {
            val row = ((mesh.pointsLat[n] - latSouth) / dLat).toInt()
            val col = ((mesh.pointsLon[n] - lonWest) / dLon).toInt()
            if (row in 0 until steps && col in 0 until steps) nodeCells.add(bucketKey(row, col))
        }

        val stretchIds = HashSet<Int>()
        var waterMeshed = 0
        var waterBare = 0
        var land = 0
        var landDeep = 0
        var unsounded = 0
        val waterDepths = ArrayList<Double>()
        val landDeepDepths = ArrayList<Double>()

        println(
            "route probe window [$centreLat, $centreLon +/-${halfSideM.toInt()} m]: " +
                "one cell per ${stepM.toInt()} m, ${steps}x$steps; 'o' a mesh node in the cell, " +
                "'#' water the soundings cover, '!' water they do not, '.' land shallower than the gate, " +
                "'?' land the soundings call deep, '-' no soundings at all"
        )
        for (row in 0 until steps) {
            val lat = latSouth + (row + 0.5) * dLat
            val line = StringBuilder(steps)
            for (col in 0 until steps) {
                val lon = lonWest + (col + 0.5) * dLon
                val depth = geometry.depthM(lat, lon)
                val water = geometry.isWater(lat, lon)
                val node = bucketKey(row, col) in nodeCells
                when {
                    node -> {
                        line.append('o')
                        nearestNodeIndex(mesh, lat, lon)?.let { stretchIds.add(mesh.nodeComponent[it]) }
                    }
                    water && depth.isNaN() -> {
                        line.append('!')
                        waterBare++
                        unsounded++
                    }
                    water -> {
                        line.append('#')
                        waterMeshed++
                        waterDepths.add(depth)
                    }
                    depth.isNaN() -> {
                        line.append('-')
                        land++
                        unsounded++
                    }
                    depth >= DEPTH_GATE_M -> {
                        line.append('?')
                        waterBare++
                        landDeep++
                        landDeepDepths.add(depth)
                    }
                    else -> {
                        line.append('.')
                        land++
                    }
                }
            }
            println(line.toString())
        }
        println(
            "route probe window totals: ${waterMeshed} water cell(s) the soundings cover, " +
                "$waterBare water cell(s) they do not, $land land cell(s) of which $landDeep hold a depth " +
                "at or over the gate, $unsounded cell(s) unsounded in all; ${stretchIds.size} stretch(es) reach in"
        )
        if (waterDepths.isNotEmpty()) {
            val sorted = waterDepths.sorted()
            println(
                "route probe window water depth (m): min ${"%.1f".format(sorted.first())}, " +
                    "median ${"%.1f".format(sorted[sorted.size / 2])}, max ${"%.1f".format(sorted.last())}, " +
                    "${sorted.count { it >= DEPTH_GATE_M }}/${sorted.size} cell(s) over the gate"
            )
        }
        if (landDeepDepths.size > 0) {
            val sorted = landDeepDepths.sorted()
            println(
                "route probe window land the soundings call water (m): min ${"%.1f".format(sorted.first())}, " +
                    "median ${"%.1f".format(sorted[sorted.size / 2])}, max ${"%.1f".format(sorted.last())}"
            )
        }
        printWindowPoints(geometry, mesh, centreLat, centreLon)
    }

    /** The index of the node nearest a point, or null when none is closer than [NODE_REACH_M]. */
    private fun nearestNodeIndex(
        mesh: ykws.android.maro.data.model.RouteMeshArrays,
        lat: Double,
        lon: Double
    ): Int? {
        var best: Int? = null
        var bestM = Double.MAX_VALUE
        val mPerDegLon = M_PER_DEG_LAT * cos(lat * PI / 180.0)
        for (n in 0 until mesh.nodeCount) {
            val dy = (mesh.pointsLat[n] - lat) * M_PER_DEG_LAT
            val dx = (mesh.pointsLon[n] - lon) * mPerDegLon
            val d = dx * dx + dy * dy
            if (d < bestM) {
                bestM = d
                best = n
            }
        }
        val index = best ?: return null
        return if (kotlin.math.sqrt(bestM) <= NODE_REACH_M) index else null
    }

    /**
     * What the mesh answers at nine points of the window — the centre and the eight around it.
     *
     * This is the reading that decides whether a bay is **absent** (no node, no stretch) or merely
     * **unreachable**: a bay the mesh holds but labels as a stretch of its own means the search can
     * never route into it from the open sea, which from a boat looks exactly like a bay that is not
     * water at all.
     */
    private fun printWindowPoints(
        geometry: PrebakedRouteGeometry,
        mesh: ykws.android.maro.data.model.RouteMeshArrays,
        centreLat: Double,
        centreLon: Double
    ) {
        val offsetM = 750.0
        val dLat = offsetM / M_PER_DEG_LAT
        val dLon = offsetM / (M_PER_DEG_LAT * cos(centreLat * PI / 180.0))
        val points = listOf(
            "centre" to (centreLat to centreLon),
            "N" to (centreLat + dLat to centreLon),
            "S" to (centreLat - dLat to centreLon),
            "E" to (centreLat to centreLon + dLon),
            "W" to (centreLat to centreLon - dLon),
            "NE" to (centreLat + dLat to centreLon + dLon),
            "NW" to (centreLat + dLat to centreLon - dLon),
            "SE" to (centreLat - dLat to centreLon + dLon),
            "SW" to (centreLat - dLat to centreLon - dLon)
        )
        for ((label, point) in points) {
            val (lat, lon) = point
            val depth = geometry.depthM(lat, lon)
            val water = geometry.isWater(lat, lon)
            val index = nearestNodeIndex(mesh, lat, lon)
            val distance = if (index == null) Double.NaN else metresTo(mesh, index, lat, lon)
            println(
                "route probe window point $label (${"%.5f".format(lat)}, ${"%.5f".format(lon)}): " +
                    "water=$water, depth=${if (depth.isNaN()) "none" else "%.1f".format(depth) + " m"}, " +
                    (if (index == null) "no node within ${NODE_REACH_M.toInt()} m"
                    else "node ${"%.0f".format(distance)} m away in stretch ${mesh.nodeComponent[index]}")
            )
        }
    }

    /** The exact distance from a point to one mesh node, in metres. */
    private fun metresTo(
        mesh: ykws.android.maro.data.model.RouteMeshArrays,
        index: Int,
        lat: Double,
        lon: Double
    ): Double {
        val mPerDegLon = M_PER_DEG_LAT * cos(lat * PI / 180.0)
        val dy = (mesh.pointsLat[index] - lat) * M_PER_DEG_LAT
        val dx = (mesh.pointsLon[index] - lon) * mPerDegLon
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    // ── The gap scan ──────────────────────────────────────────────────────────

    /** One grid cell of the scan: its centre, whether it is water, its depth, and its node distance. */
    private class Cell(val row: Int, val col: Int, val lat: Double, val lon: Double, val depth: Double)

    /** The scan's tallies and its gap cells, kept so the report can cluster them. */
    private class Scan(
        val rows: Int,
        val cols: Int,
        val cellAreaKm2: Double,
        val water: Int,
        val covered: Int,
        val land: Int,
        /** Water cells the depth grid does not reach — the gate closes those by design. */
        val noDepth: Int,
        /** Covered water the soundings never reached — what a keener gate would have closed. */
        val coveredUnsound: Int,
        val gaps: List<Cell>,
        /** Land cells the soundings call deep water: a bay the oracle has swallowed. */
        val suspectLand: List<Cell>,
        val depthOfWaterCells: List<Double>,
        val nodeDistanceOfWaterCells: List<Double>
    )

    /**
     * Walks the box and classifies every cell the water oracle calls water.
     *
     * A cell counts as **covered** when a mesh node stands within [GAP_RADIUS_M] — the coarsest
     * spacing the refinement ever uses offshore, so anything beyond it is a hole rather than sparse
     * water. A cell that is water and uncovered is a gap, and carries the depth that decides whether
     * the gate or the geometry is to blame.
     */
    private fun scanForGaps(geometry: PrebakedRouteGeometry, mesh: ykws.android.maro.data.model.RouteMeshArrays): Scan {
        val box = geometry.box
        val cellLat = GAP_CELL_M / M_PER_DEG_LAT
        val cellLon = GAP_CELL_M / (M_PER_DEG_LAT * cos(box.centerLat * PI / 180.0))
        val rows = ((box.latNorth - box.latSouth) / cellLat).toInt().coerceAtLeast(1)
        val cols = ((box.lonEast - box.lonWest) / cellLon).toInt().coerceAtLeast(1)

        // A uniform bucket per node, so a cell's nearest node is a neighbourhood walk rather than a
        // sweep over forty thousand nodes.
        val buckets = HashMap<Long, MutableList<Int>>()
        for (n in 0 until mesh.nodeCount) {
            val r = ((mesh.pointsLat[n] - box.latSouth) / cellLat).toInt()
            val c = ((mesh.pointsLon[n] - box.lonWest) / cellLon).toInt()
            buckets.getOrPut(bucketKey(r, c)) { ArrayList() }.add(n)
        }

        val reach = (GAP_RADIUS_M / GAP_CELL_M).toInt() + 1
        var water = 0
        var covered = 0
        var land = 0
        var noDepth = 0
        var coveredUnsound = 0
        val gaps = ArrayList<Cell>()
        val suspectLand = ArrayList<Cell>()
        val waterDepths = ArrayList<Double>()
        val waterNodeDistances = ArrayList<Double>()

        for (row in 0 until rows) {
            val lat = box.latSouth + (row + 0.5) * cellLat
            for (col in 0 until cols) {
                val lon = box.lonWest + (col + 0.5) * cellLon
                val depth = geometry.depthM(lat, lon)
                if (!geometry.isWater(lat, lon)) {
                    land++
                    // Land the soundings call deep water is the signature of an oracle that has
                    // swallowed a bay: the depth grid has sea there and the route can never reach it.
                    if (!depth.isNaN() && depth >= DEPTH_GATE_M) {
                        suspectLand.add(Cell(row, col, lat, lon, depth))
                    }
                    continue
                }
                water++
                waterDepths.add(depth)
                if (depth.isNaN()) noDepth++
                val nearest = nearestNodeM(mesh, buckets, row, col, lat, lon, reach, cellLat, cellLon)
                // Capped, so a distribution can be printed: the cap is the gap threshold itself.
                waterNodeDistances.add(if (nearest > GAP_RADIUS_M) GAP_RADIUS_M else nearest)
                if (nearest <= GAP_RADIUS_M) {
                    covered++
                    if (depth.isNaN()) coveredUnsound++
                } else {
                    gaps.add(Cell(row, col, lat, lon, depth))
                }
            }
        }

        return Scan(
            rows = rows,
            cols = cols,
            cellAreaKm2 = (GAP_CELL_M / 1000.0) * (GAP_CELL_M / 1000.0),
            water = water,
            covered = covered,
            land = land,
            noDepth = noDepth,
            coveredUnsound = coveredUnsound,
            gaps = gaps,
            suspectLand = suspectLand,
            depthOfWaterCells = waterDepths,
            nodeDistanceOfWaterCells = waterNodeDistances
        )
    }

    /** The exact distance to the nearest mesh node, searching only the bucket neighbourhood. */
    private fun nearestNodeM(
        mesh: ykws.android.maro.data.model.RouteMeshArrays,
        buckets: Map<Long, List<Int>>,
        row: Int,
        col: Int,
        lat: Double,
        lon: Double,
        reach: Int,
        cellLat: Double,
        cellLon: Double
    ): Double {
        var best = Double.MAX_VALUE
        val mPerDegLon = M_PER_DEG_LAT * cos(lat * PI / 180.0)
        for (r in (row - reach)..(row + reach)) {
            for (c in (col - reach)..(col + reach)) {
                for (n in buckets[bucketKey(r, c)] ?: continue) {
                    val dy = (mesh.pointsLat[n] - lat) * M_PER_DEG_LAT
                    val dx = (mesh.pointsLon[n] - lon) * mPerDegLon
                    val d = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (d < best) best = d
                }
            }
        }
        return best
    }

    private fun bucketKey(row: Int, col: Int): Long =
        (row.toLong() shl 32) xor (col.toLong() and 0xffffffffL)

    private fun printGapReport(scan: Scan, geometry: PrebakedRouteGeometry) {
        val gapAreaKm2 = scan.gaps.size * scan.cellAreaKm2
        val waterAreaKm2 = scan.water * scan.cellAreaKm2
        val sounded = (scan.water - scan.noDepth).coerceAtLeast(1)
        println(
            "route probe gap scan @ ${GAP_CELL_M.toInt()} m: ${scan.water} water cell(s) " +
                "(${"%.1f".format(waterAreaKm2)} km2), ${scan.noDepth} of them with no depth reading " +
                "(${"%.1f".format(100.0 * scan.noDepth / scan.water.coerceAtLeast(1))}% — the gate closes those by design)"
        )
        println(
            "route probe coverage: ${scan.covered} water cell(s) hold a node within ${GAP_RADIUS_M.toInt()} m — " +
                "${"%.1f".format(100.0 * scan.covered / scan.water.coerceAtLeast(1))}% of all water, " +
                "${"%.1f".format(100.0 * scan.covered / sounded)}% of the water the soundings cover, and " +
                "${"%.1f".format(scan.coveredUnsound * scan.cellAreaKm2)} km2 of it is unsounded — " +
                "${scan.gaps.size} gap cell(s) (${"%.1f".format(gapAreaKm2)} km2)"
        )
        if (scan.nodeDistanceOfWaterCells.isNotEmpty()) {
            val sorted = scan.nodeDistanceOfWaterCells.sorted()
            println(
                "route probe node distance over water (m): median ${"%.0f".format(sorted[sorted.size / 2])}, " +
                    "p90 ${"%.0f".format(sorted[(sorted.size * 9) / 10])}, max ${"%.0f".format(sorted.last())}"
            )
        }
        if (scan.gaps.isEmpty()) return

        val clusters = cluster(scan.gaps)
        println("route probe gaps: ${clusters.size} area(s), largest first —")
        for (area in clusters.take(MAX_REPORTED_GAPS)) {
            val depths = area.cells.map { it.depth }.filter { !it.isNaN() }.sorted()
            val unsounded = area.cells.count { it.depth.isNaN() }
            val deepest = depths.lastOrNull() ?: Double.NaN
            val shallowest = depths.firstOrNull() ?: Double.NaN
            val median = if (depths.isEmpty()) Double.NaN else depths[depths.size / 2]
            val overGate = depths.count { it >= DEPTH_GATE_M }
            val verdict = when {
                unsounded * 2 > area.cells.size ->
                    "outside the depth grid (${unsounded}/${area.cells.size} cell(s) unsounded) — the gate closes it by design, so nothing here is a defect"
                depths.isEmpty() -> "no depth reading at all"
                deepest < DEPTH_GATE_M -> "closed by the ${DEPTH_GATE_M} m gate — nothing here is deep enough"
                median >= DEPTH_GATE_M ->
                    "deep enough but unmeshed (${overGate}/${depths.size} cell(s) over the gate) — erosion, connectivity or a box edge"
                else ->
                    "mixed: rim shallow, ${overGate}/${depths.size} cell(s) over the gate — the gate closes it from its edges in"
            }
            println(
                "route probe gap area ${"%.2f".format(area.cells.size * scan.cellAreaKm2)} km2 " +
                    "at ${"%.4f".format(area.latSouth)}…${"%.4f".format(area.latNorth)} N, " +
                    "${"%.4f".format(area.lonWest)}…${"%.4f".format(area.lonEast)} E · " +
                    "depth ${"%.1f".format(shallowest)}…${"%.1f".format(deepest)} m, median ${"%.1f".format(median)} · $verdict"
            )
        }
        if (clusters.size > MAX_REPORTED_GAPS) {
            println("route probe gaps: ${clusters.size - MAX_REPORTED_GAPS} smaller area(s) not printed")
        }

        printSuspectLand(scan)
    }

    /**
     * Land the soundings call deep water — the shape a swallowed bay takes.
     *
     * This is the reading that separates an oracle fault from a gate fault or an erosion fault: the
     * depth grid holds sea where the water test says land, and no amount of gate tuning would bring
     * that back, because the mesh never had a face there to keep. A bay reported missing that does
     * **not** appear here is water the oracle accepts, and belongs to the gate or the erosion.
     */
    private fun printSuspectLand(scan: Scan) {
        if (scan.suspectLand.isEmpty()) {
            println("route probe soundings-vs-oracle: no land cell holds a depth at or over the gate")
            return
        }
        val areas = cluster(scan.suspectLand)
        println(
            "route probe soundings-vs-oracle: ${scan.suspectLand.size} land cell(s) " +
                "(${"%.2f".format(scan.suspectLand.size * scan.cellAreaKm2)} km2) that the soundings call water — " +
                "${areas.size} area(s), largest first —"
        )
        for (area in areas.take(MAX_REPORTED_GAPS)) {
            val depths = area.cells.map { it.depth }.sorted()
            println(
                "route probe suspect land ${"%.2f".format(area.cells.size * scan.cellAreaKm2)} km2 " +
                    "at ${"%.4f".format(area.latSouth)}…${"%.4f".format(area.latNorth)} N, " +
                    "${"%.4f".format(area.lonWest)}…${"%.4f".format(area.lonEast)} E · " +
                    "depth ${"%.1f".format(depths.first())}…${"%.1f".format(depths.last())} m, " +
                    "median ${"%.1f".format(depths[depths.size / 2])}"
            )
        }
    }

    /** One contiguous gap area: its bounding box and the cells inside it. */
    private class GapArea(
        val latSouth: Double,
        val latNorth: Double,
        val lonWest: Double,
        val lonEast: Double,
        val cells: List<Cell>
    )

    /** Flood-fills the gap cells into areas, eight-connected, then orders them by area. */
    private fun cluster(cells: List<Cell>): List<GapArea> {
        val byKey = HashMap<Long, Cell>()
        for (cell in cells) byKey[bucketKey(cell.row, cell.col)] = cell
        val taken = HashSet<Long>()
        val areas = ArrayList<GapArea>()
        for (cell in cells) {
            if (!taken.add(bucketKey(cell.row, cell.col))) continue
            val stack = ArrayDeque<Cell>()
            val members = ArrayList<Cell>()
            stack.add(cell)
            while (stack.isNotEmpty()) {
                val current = stack.removeLast()
                members.add(current)
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        if (dr == 0 && dc == 0) continue
                        val key = bucketKey(current.row + dr, current.col + dc)
                        val next = byKey[key] ?: continue
                        if (taken.add(key)) stack.add(next)
                    }
                }
            }
            areas.add(
                GapArea(
                    latSouth = members.minOf { it.lat },
                    latNorth = members.maxOf { it.lat },
                    lonWest = members.minOf { it.lon },
                    lonEast = members.maxOf { it.lon },
                    cells = members
                )
            )
        }
        return areas.sortedByDescending { it.cells.size }
    }

    // ── The route metrics ─────────────────────────────────────────────────────

    /**
     * The pairs the metrics are taken on: the largest stretch's own extremes — as long as the corridor
     * offers — and, for each of the largest zone boundaries, the two nodes nearest its west and east
     * ends, so a route that has to live beside a zone is measured too.
     */
    private fun metricPairs(
        mesh: ykws.android.maro.data.model.RouteMeshArrays,
        rings: List<List<RoutePoint>>
    ): List<Pair<String, Pair<RoutePoint, RoutePoint>>> {
        val pairs = ArrayList<Pair<String, Pair<RoutePoint, RoutePoint>>>()
        val ofLargest = mesh.nodeComponent.indices
            .groupBy { mesh.nodeComponent[it] }
            .maxBy { it.value.size }
            .value
        val south = ofLargest.minBy { mesh.pointsLat[it] }
        val north = ofLargest.maxBy { mesh.pointsLat[it] }
        pairs.add("largest stretch, south to north" to (mesh.point(south) to mesh.point(north)))

        val largestRings = rings
            .filter { it.size >= 3 }
            .sortedByDescending { ring ->
                val lats = ring.map { it.latitude }
                val lons = ring.map { it.longitude }
                (lats.max() - lats.min()) * (lons.max() - lons.min())
            }
            .take(METRIC_ZONES)
        for ((index, ring) in largestRings.withIndex()) {
            val west = ring.minBy { it.longitude }
            val east = ring.maxBy { it.longitude }
            pairs.add(
                "zone ${index + 1} (${ring.size} vertices, ${"%.4f".format(west.longitude)}…${"%.4f".format(east.longitude)} E)" to
                    (nearestNode(mesh, west) to nearestNode(mesh, east))
            )
        }
        return pairs
    }

    private fun nearestNode(
        mesh: ykws.android.maro.data.model.RouteMeshArrays,
        point: RoutePoint
    ): RoutePoint {
        var best = 0
        var bestM = Double.MAX_VALUE
        val mPerDegLon = M_PER_DEG_LAT * cos(point.latitude * PI / 180.0)
        for (n in 0 until mesh.nodeCount) {
            val dy = (mesh.pointsLat[n] - point.latitude) * M_PER_DEG_LAT
            val dx = (mesh.pointsLon[n] - point.longitude) * mPerDegLon
            val d = dx * dx + dy * dy
            if (d < bestM) {
                bestM = d
                best = n
            }
        }
        return mesh.point(best)
    }

    /**
     * The mesh engine's own readings off a result — the counters this probe prints for the incumbent
     * are its dossier, and an engine that answered none leaves them at their empty defaults. The rule
     * is not restated here: it lives once, at
     * [`meshDetailsOrEmpty`][ykws.android.maro.spatial.mesh.meshDetailsOrEmpty].
     */
    private fun meshDetails(result: RouteResult.Success): RouteMeshDetails = result.meshDetailsOrEmpty

    /** One route the probe measured: its line, its six numbers, the answer it came from, and its cost. */
    private class RouteSample(
        val label: String,
        val points: List<RoutePoint>,
        val metrics: LineMetrics,
        val result: RouteResult.Success,
        val elapsedMs: Double
    )

    /**
     * **The instrument, and the abstraction's whole payoff: it measures any [RouteEngine].**
     *
     * It takes the engine, not the mesh search, so the same pairs and the same six metrics describe
     * whichever engine is handed in — the incumbent, or the one the parked taut design would build.
     * The two costs beside them are what make a comparison a reading rather than a preference: the
     * wall clock per pair, and the engine's own expansion count
     * ([RouteEngineDetails.nodesExpanded]), which says whether a difference in milliseconds is a
     * difference in work.
     *
     * Keeping the lines — rather than searching, printing and forgetting — is what lets the passage
     * reading say *which* route uses a gap, which is the whole question a berth's tripwire asks.
     */
    private fun metricRoutes(
        engine: RouteEngine,
        pairs: List<Pair<String, Pair<RoutePoint, RoutePoint>>>,
        rings: List<List<RoutePoint>>
    ): List<RouteSample> {
        val samples = ArrayList<RouteSample>()
        var lost = 0
        for ((label, pair) in pairs) {
            val beganNs = System.nanoTime()
            val result = runBlocking { engine.route(pair.first, pair.second, PROBE_CRUISE_KN) }
            val elapsedMs = (System.nanoTime() - beganNs) / 1_000_000.0
            if (result !is RouteResult.Success) {
                lost++
                println("route probe [$label]: no route ($result) in ${"%.0f".format(elapsedMs)} ms")
                continue
            }
            samples.add(
                RouteSample(
                    label,
                    result.points,
                    measure(result.points, rings, PROBE_BERTH_M),
                    result,
                    elapsedMs
                )
            )
        }
        // **The P2 adoption gate, as the one number it is:** the traversal rule costs a route wherever
        // the only way through is a zone's interior, so a pair that routes today and is lost here is
        // the reading that sends the rule back to a swept factor instead.
        println(
            "route probe adoption gate: ${samples.size} of ${pairs.size} metric pair(s) routed, " +
                "$lost lost — none may be lost for the rule to stand"
        )
        return samples
    }

    private fun printRouteMetrics(samples: List<RouteSample>) {
        println(
            "route probe metrics: vertices · total turn · worst turn · min zone clearance · length · " +
                "ETA · drawn seconds against priced seconds · forced crossing"
        )
        for (sample in samples) {
            val result = sample.result
            val readings = meshDetails(result)
            // **The two clocks, side by side.** `durationSec` is the drawn line's own time and
            // `pricedSec` is the seconds the search accumulated along its own chain; the pair is what
            // says whether a pass over the chain changed the line without re-pricing it, which the
            // search asserts internally and this prints.
            val drawnShare = if (readings.pricedSec > 0.0) {
                100.0 * (result.durationSec - readings.pricedSec) / readings.pricedSec
            } else {
                Double.NaN
            }
            val crossing = if (result.forcedCrossingZoneNames.isEmpty()) {
                "not forced"
            } else {
                "FORCED CROSSING of ${result.forcedCrossingZoneNames.joinToString(", ")}"
            }
            // **The invariant's own reading, per pair.** A `rawChainFallback` here would say the
            // admission rules let a pass change the line without re-pricing it, and that the plan
            // answered the raw node chain instead of the smoothed one — the defect visible as a
            // number rather than as a lost route.
            val fallback = if (readings.rawChainFallback) " · RAW CHAIN FALLBACK" else ""
            println(
                "route probe [${sample.label}]: ${result.points.size} vertices · " +
                    "total turn ${"%.0f".format(sample.metrics.totalTurnDeg)}° · " +
                    "worst turn ${"%.0f".format(sample.metrics.worstTurnDeg)}° · " +
                    "min zone clearance ${"%.0f".format(sample.metrics.minClearanceM)} m · " +
                    "${"%.2f".format(result.distanceM / 1000.0)} km · " +
                    "${"%.1f".format(result.durationSec / 60.0)} min · " +
                    "drawn vs priced ${"%.1f".format(result.durationSec / 60.0)} / " +
                    "${"%.1f".format(readings.pricedSec / 60.0)} min " +
                    "(${"%+.2f".format(drawnShare)}%) · $crossing · " +
                    // The berth's own two numbers: a crossing shows as a short exposure in two short
                    // runs, a line that hugs an edge as one long run.
                    "in berth ${"%.2f".format(sample.metrics.berthExposureM / 1000.0)} km " +
                    "(longest run ${"%.2f".format(sample.metrics.longestBerthRunM / 1000.0)} km) · " +
                    "turns smoothed ${readings.smoothedVertices} / reduced ${readings.reducedVertices} / " +
                    "sharp ${readings.sharpVertices} (no leg room ${readings.sharpNoRoomVertices} / " +
                    "no water ${readings.sharpNoWaterVertices} / " +
                    "no arc ${readings.sharpNoGeometryVertices} / " +
                    "no price ${readings.sharpNoPriceVertices}) · " +
                    "largest radius ${"%.0f".format(readings.largestTurnRadiusM)} m · " +
                    "A* ${"%.0f".format(sample.elapsedMs)} ms over " +
                    "${result.details?.nodesExpanded ?: 0} node(s) expanded$fallback"
            )
        }
        // **And the two questions the corrected pass has to answer about itself**: whether the fallback
        // ever fired, and which pairs report a crossing. The crossing report now walks every drawn leg
        // at half the step it used to, so a leg shorter than that step is still sampled; a name printed
        // here is a crossing this report found, and the geometry beside it says whether the old,
        // coarser walk could have seen it.
        val fallbacks = samples.filter { meshDetails(it.result).rawChainFallback }
        val crossings = samples.filter { it.result.forcedCrossingZoneNames.isNotEmpty() }
        println(
            "route probe invariant: ${fallbacks.size} of ${samples.size} pair(s) fell back to the raw " +
                "node chain (" +
                if (fallbacks.isEmpty()) {
                    "none — every drawn line fitted the seconds the search accumulated"
                } else {
                    fallbacks.joinToString(", ") { it.label }
                } +
                "); forced crossings reported: " +
                if (crossings.isEmpty()) {
                    "none of ${samples.size}"
                } else {
                    crossings.joinToString(", ") {
                        "${it.label} → ${it.result.forcedCrossingZoneNames.joinToString(", ")}"
                    }
                }
        )
    }

    // ── The comfort cap, swept ────────────────────────────────────────────────

    /**
     * The cap swept over every value the property accepts, on the pairs [title] names.
     *
     * A lower cap means a **wider** radius, `R = v² / a`, so it should fit *less* often and leave
     * more corners sharp — the counter-intuition this table exists to confirm or refute. Per value
     * the reading is the sharp count against the corners the fillet actually looked at, the six
     * metrics, and the largest radius the water let through, which is what separates "the cap
     * refused" from "the mesh had no room".
     *
     * **It is run twice on purpose**, once on the corridor's pairs and once on the inshore ones: the
     * row that chooses the shipped default has to come off the water the boat asks about. A 0.5 m/s²
     * cap asks a 415 m radius at 28 kn, which is at home in open sea and almost never in a 60 m-spaced
     * coastal mesh, so a table taken offshore is a decision about the wrong water — and the previous
     * default was chosen from exactly that table.
     */
    private fun printCapSweep(
        title: String,
        mesh: ykws.android.maro.data.model.RouteMeshArrays,
        queries: PrebakedQueries,
        pairs: List<Pair<String, Pair<RoutePoint, RoutePoint>>>,
        rings: List<List<RoutePoint>>
    ) {
        println(
            "route probe cap sweep [$title]: the fillet re-run at each cap on the same ${pairs.size} " +
                "$title pair(s), berth off"
        )
        for (cap in CAP_SWEEP_MPS2) {
            val search = RouteSearch(
                mesh,
                queries,
                zoneBerthM = { 0.0 },
                turnLateralAccelMps2 = { cap }
            )
            var corners = 0
            var sharp = 0
            var noRoom = 0
            var noWater = 0
            var noGeometry = 0
            var largest = 0.0
            for ((label, pair) in pairs) {
                val result = search.search(pair.first, pair.second, PROBE_CRUISE_KN)
                if (result !is RouteResult.Success) {
                    println("route probe cap $cap [$label]: no route ($result)")
                    continue
                }
                val readings = meshDetails(result)
                val metrics = measure(result.points, rings, PROBE_BERTH_M)
                corners += readings.smoothedVertices + readings.reducedVertices + readings.sharpVertices
                sharp += readings.sharpVertices
                noRoom += readings.sharpNoRoomVertices
                noWater += readings.sharpNoWaterVertices
                noGeometry += readings.sharpNoGeometryVertices
                if (readings.largestTurnRadiusM > largest) largest = readings.largestTurnRadiusM
                println(
                    "route probe cap $cap [$label]: ${result.points.size} vertices · " +
                        "total turn ${"%.0f".format(metrics.totalTurnDeg)}° · " +
                        "worst turn ${"%.0f".format(metrics.worstTurnDeg)}° · " +
                        "min zone clearance ${"%.0f".format(metrics.minClearanceM)} m · " +
                        "${"%.2f".format(result.distanceM / 1000.0)} km · " +
                        "${"%.1f".format(result.durationSec / 60.0)} min · " +
                        "in berth ${"%.2f".format(metrics.berthExposureM / 1000.0)} km " +
                        "(longest run ${"%.2f".format(metrics.longestBerthRunM / 1000.0)} km) · " +
                        "turns smoothed ${readings.smoothedVertices} / reduced ${readings.reducedVertices} / " +
                        "sharp ${readings.sharpVertices} · " +
                        "largest radius ${"%.0f".format(readings.largestTurnRadiusM)} m"
                )
            }
            println(
                "route probe cap $cap totals [$title]: $sharp of $corners corner(s) stay sharp · " +
                    "no leg room $noRoom / water refused $noWater / no arc $noGeometry · " +
                    "largest radius accepted ${"%.0f".format(largest)} m of " +
                    "${"%.0f".format(radiusAtM(cap, PROBE_CRUISE_KN))} m ideal at " +
                    "${PROBE_CRUISE_KN.toInt()} kn"
            )
        }
    }

    /**
     * The radius `R = v² / a` a cap implies at a speed, for the sweep's own reference column — read
     * from [`RouteTurnGeometry`], so the probe cannot disagree with the pass about the radius.
     */
    private fun radiusAtM(capMps2: Double, knots: Double): Double =
        RouteTurnGeometry.radiusM(knots * Units.MPS_PER_KNOT, capMps2)

    // ── What the geometry allows, the cap aside ───────────────────────────────

    /**
     * The largest radius the mesh's own legs let any corner take, measured without the fillet.
     *
     * A tangent point may not eat more than the fillet's cutback share of either leg, so a corner
     * turning `θ` between legs whose shorter one is `L` can never take a radius above
     * `MAX_CUTBACK_FRACTION · L / tan(θ / 2)` — whatever the cap asks for. Printing that ceiling is
     * what separates "the cap is the limit" from "the mesh's spacing is the limit": the sweep's own
     * counts say which corners came out sharp, this says why, with none of the fillet's bookkeeping
     * in the way.
     *
     * The angle, the cutback and the inversion are all read from [`RouteTurnGeometry`], the fillet's
     * own one home, because the first version of this reading wrote the rule **backwards** — as
     * `L · tan(θ / 2)`, which is not a radius at all — and took its angle to be the corner's interior
     * one rather than the deflection, so every ceiling it printed was wrong twice over.
     *
     * The chain read is the search's own before the fillet — `nodeIndices` is the merged chain the
     * fillet is handed — with the berth off, so it is the chain behind the metrics above. Per cap the
     * last column counts the corners whose ceiling reaches that cap's own ideal radius at
     * [PROBE_CRUISE_KN]: zero there means the cap can never be met on this mesh, whatever it is set to.
     */
    private fun printGeometryAllowance(
        mesh: ykws.android.maro.data.model.RouteMeshArrays,
        queries: PrebakedQueries,
        pairs: List<Pair<String, Pair<RoutePoint, RoutePoint>>>
    ) {
        val search = RouteSearch(mesh, queries, zoneBerthM = { 0.0 })
        println(
            "route probe geometry allowance: the radius the legs allow per corner, cap aside — " +
                "${RouteTurnGeometry.MAX_CUTBACK_FRACTION} of the shorter leg over tan of the " +
                "half-turn, the fillet's own cutback"
        )
        for ((label, pair) in pairs) {
            val result = search.search(pair.first, pair.second, PROBE_CRUISE_KN)
            if (result !is RouteResult.Success) {
                println("route probe allowance [$label]: no route ($result)")
                continue
            }
            val chain = meshDetails(result).nodeIndices.map { mesh.point(it) }
            if (chain.size < 3) {
                println("route probe allowance [$label]: ${chain.size} vertex(es), no corner to allow")
                continue
            }
            val legs = ArrayList<Double>(chain.size - 1)
            for (i in 0 until chain.size - 1) legs.add(metresBetween(chain[i], chain[i + 1]))
            val ceilings = ArrayList<Double>(chain.size - 2)
            for (i in 1 until chain.size - 1) {
                val turn = RouteTurnGeometry.turnRadians(chain[i - 1], chain[i], chain[i + 1])
                if (turn < RouteTurnGeometry.MIN_TURN_RAD ||
                    turn > RouteTurnGeometry.MAX_TURN_RAD
                ) {
                    continue
                }
                ceilings.add(
                    RouteTurnGeometry.radiusWithinCutbackM(min(legs[i - 1], legs[i]), turn)
                )
            }
            val sortedLegs = legs.sorted()
            val sortedCeilings = ceilings.sorted()
            val admitted = CAP_SWEEP_MPS2.joinToString(" · ") { cap ->
                val ideal = radiusAtM(cap, PROBE_CRUISE_KN)
                "${"%.1f".format(cap)} → ${ceilings.count { it >= ideal }}"
            }
            println(
                "route probe allowance [$label]: ${chain.size} vertices, ${ceilings.size} corner(s) the " +
                    "fillet would look at · legs (m) min ${"%.0f".format(sortedLegs.first())} / " +
                    "median ${"%.0f".format(sortedLegs[sortedLegs.size / 2])} / " +
                    "max ${"%.0f".format(sortedLegs.last())} · radius the legs allow (m) min " +
                    "${"%.0f".format(sortedCeilings.first())} / " +
                    "median ${"%.0f".format(sortedCeilings[sortedCeilings.size / 2])} / " +
                    "p90 ${"%.0f".format(sortedCeilings[(sortedCeilings.size * 9) / 10])} / " +
                    "max ${"%.0f".format(sortedCeilings.last())} · corners whose ceiling reaches each " +
                    "cap's ideal: $admitted"
            )
        }
    }

    // ── The berth as a price, swept ───────────────────────────────────────────

    /**
     * The berth swept as a price, every price against the berth-off line.
     *
     * The same 25 m band, entered freely at the price of one, then at a rising ceiling: a leg is
     * priced as its own time multiplied by `1 + (price − 1) × depth`, so nothing is deleted and no
     * passage closes. Per pair the reading is the minimum clearance the line keeps, its length and
     * ETA against the berth-off line, and the exposure the probe already reads — in-berth kilometres
     * and the longest single run inside, which is what separates a crossing from a hug.
     */
    private fun printBerthSweep(
        mesh: ykws.android.maro.data.model.RouteMeshArrays,
        queries: PrebakedQueries,
        pairs: List<Pair<String, Pair<RoutePoint, RoutePoint>>>,
        rings: List<List<RoutePoint>>
    ) {
        val berthM = PROBE_BERTH_M
        val off = RouteSearch(mesh, queries, zoneBerthM = { 0.0 })
        val baseline = LinkedHashMap<String, RouteResult.Success>()
        for ((label, pair) in pairs) {
            val result = off.search(pair.first, pair.second, PROBE_CRUISE_KN)
            if (result is RouteResult.Success) baseline[label] = result
        }
        println("route probe berth off: no berth at all, the line every price is compared against")
        for ((label, result) in baseline) {
            val metrics = measure(result.points, rings, berthM)
            println(
                "route probe berth off [$label]: ${"%.2f".format(result.distanceM / 1000.0)} km · " +
                    "${"%.1f".format(result.durationSec / 60.0)} min · " +
                    "min zone clearance ${"%.0f".format(metrics.minClearanceM)} m · " +
                    "in berth ${"%.2f".format(metrics.berthExposureM / 1000.0)} km " +
                    "(longest run ${"%.2f".format(metrics.longestBerthRunM / 1000.0)} km)"
            )
        }
        for (price in BERTH_PRICE_SWEEP) {
            val search = RouteSearch(
                mesh,
                queries,
                zoneBerthM = { berthM },
                berthMaxPrice = price
            )
            for ((label, pair) in pairs) {
                val result = search.search(pair.first, pair.second, PROBE_CRUISE_KN)
                if (result !is RouteResult.Success) {
                    println("route probe berth x$price [$label]: no route ($result)")
                    continue
                }
                val metrics = measure(result.points, rings, berthM)
                val base = baseline[label]
                val lengthDelta = if (base != null) {
                    100.0 * (result.distanceM - base.distanceM) / base.distanceM
                } else {
                    Double.NaN
                }
                val etaDelta = if (base != null) {
                    100.0 * (result.durationSec - base.durationSec) / base.durationSec
                } else {
                    Double.NaN
                }
                println(
                    "route probe berth x$price [$label]: ${result.points.size} vertices · " +
                        "min zone clearance ${"%.0f".format(metrics.minClearanceM)} m · " +
                        "${"%.2f".format(result.distanceM / 1000.0)} km " +
                        "(${"%+.2f".format(lengthDelta)}%) · " +
                        "${"%.1f".format(result.durationSec / 60.0)} min " +
                        "(${"%+.2f".format(etaDelta)}%) · " +
                        "in berth ${"%.2f".format(metrics.berthExposureM / 1000.0)} km " +
                        "(longest run ${"%.2f".format(metrics.longestBerthRunM / 1000.0)} km)"
                )
            }
        }
    }

    // ── The inshore water the complaint comes from ────────────────────────────

    /**
     * **Short inshore pairs — the water the complaint comes from, and the mesh's own answer about
     * where it is.**
     *
     * Every reading this study had taken came from the corridor's extremes: the largest stretch end
     * to end, 104 km of it, and one pair per large zone. The boat that reported a wiggly line and
     * corners taken sharply does not sail that; it asks for a few kilometres inside the 300 m band,
     * between regulated zones, in the Antibes / Golfe-Juan / Cap d'Antibes water where the bay was
     * reported. A default chosen on the corridor is therefore a default chosen on the wrong water,
     * and this is the reading that puts the choice back where the complaint came from.
     *
     * The pairs are **derived from the mesh rather than typed in**: the boat's own stretch, the nodes
     * inside the window and within [BAND_M] of the coast, ordered west to east and then chained —
     * each pair runs from one node to the farthest node within [INSHORE_PAIR_MAX_M] of it, so it is
     * as long as the window allows and still a couple of kilometres. A coordinate typed in by hand
     * would be a claim about where the water is; this is the mesh's own answer, and the shape reading
     * printed beside each pair is what says it has the shape that was asked for.
     */
    private fun inshorePairs(
        mesh: ykws.android.maro.data.model.RouteMeshArrays,
        geometry: PrebakedRouteGeometry
    ): List<Pair<String, Pair<RoutePoint, RoutePoint>>> {
        val coast = CoastBuckets(geometry.coastlineLines(), geometry.box)
        // The boat's own stretch, taken as the largest — the same one the corridor pair is drawn from.
        val boatStretch = mesh.nodeComponent.toList().groupingBy { it }.eachCount().maxBy { it.value }.key

        val inshore = ArrayList<RoutePoint>()
        for (n in 0 until mesh.nodeCount) {
            if (mesh.nodeComponent[n] != boatStretch) continue
            val latitude = mesh.pointsLat[n]
            val longitude = mesh.pointsLon[n]
            if (latitude < INSHORE_LAT_SOUTH || latitude > INSHORE_LAT_NORTH) continue
            if (longitude < INSHORE_LON_WEST || longitude > INSHORE_LON_EAST) continue
            if (coast.distanceM(latitude, longitude) > BAND_M) continue
            inshore.add(mesh.point(n))
        }
        println(
            "route probe inshore: ${inshore.size} node(s) of stretch $boatStretch inside " +
                "${"%.3f".format(INSHORE_LAT_SOUTH)}…${"%.3f".format(INSHORE_LAT_NORTH)} N, " +
                "${"%.3f".format(INSHORE_LON_WEST)}…${"%.3f".format(INSHORE_LON_EAST)} E and within " +
                "${BAND_M.toInt()} m of the coast — the Antibes / Golfe-Juan / Cap d'Antibes water"
        )
        // **The window is hand-chosen, and it says so.** Everything else here — the pairs, the band
        // share, the passages — is derived from the mesh, the soundings and the coastline; this box is
        // not, and it is the one claim in this reading about where the water is. It is not a finding
        // about the coast: it says which stretch of it the inshore pairs are drawn from, and the pairs
        // themselves remain the mesh's own answer about the nodes inside it.
        println(
            "route probe inshore window: hand-chosen, not derived — Antibes, Golfe-Juan and Cap " +
                "d'Antibes, ${"%.3f".format(INSHORE_LAT_SOUTH)}…${"%.3f".format(INSHORE_LAT_NORTH)} N " +
                "by ${"%.3f".format(INSHORE_LON_WEST)}…${"%.3f".format(INSHORE_LON_EAST)} E, " +
                "nodes of the boat's own stretch within ${BAND_M.toInt()} m of the coast"
        )

        val ordered = inshore.sortedBy { it.longitude }
        val pairs = ArrayList<Pair<String, Pair<RoutePoint, RoutePoint>>>()
        var from = 0
        while (from < ordered.size - 1 && pairs.size < INSHORE_PAIRS) {
            // The farthest node within the cap, and **none** when the very next one already exceeds
            // it: the endpoints are ordered by longitude, not along the coast, so a neighbour in that
            // order can be the far side of a headland kilometres away, and seeding the candidate with
            // it would put a pair past the cap it is supposed to respect.
            var to = -1
            for (j in from + 1 until ordered.size) {
                if (metresBetween(ordered[from], ordered[j]) > INSHORE_PAIR_MAX_M) break
                to = j
            }
            if (to <= from) {
                from++
                continue
            }
            val lengthM = metresBetween(ordered[from], ordered[to])
            // A hop too short to be a pair is not the end of the chain: the walk advances past it and
            // the next pair starts from there, so one tight corner in the coast cannot stop the
            // reading short. Only the window's east end does that.
            if (lengthM >= INSHORE_PAIR_MIN_M) {
                val label =
                    "inshore ${pairs.size + 1} ${"%.2f".format(lengthM / 1000.0)} km " +
                        "${"%.4f".format(ordered[from].latitude)}/${"%.4f".format(ordered[from].longitude)} " +
                        "to ${"%.4f".format(ordered[to].latitude)}/${"%.4f".format(ordered[to].longitude)}"
                pairs.add(label to (ordered[from] to ordered[to]))
                println(
                    "route probe inshore pair [$label]: ${"%.2f".format(lengthM / 1000.0)} km · " +
                        "${"%.0f".format(100.0 * bandShare(ordered[from], ordered[to], coast))}% of its " +
                        "own straight line within ${BAND_M.toInt()} m of the coast · ends " +
                        "${"%.0f".format(coast.distanceM(ordered[from].latitude, ordered[from].longitude))} / " +
                        "${"%.0f".format(coast.distanceM(ordered[to].latitude, ordered[to].longitude))} m off it"
                )
            }
            from = to
        }
        if (pairs.isEmpty()) {
            println("route probe inshore: no pair of inshore nodes a couple of kilometres apart in the window")
        }
        return pairs
    }

    /** The share of the straight line between two points that lies within the band of the coast. */
    private fun bandShare(from: RoutePoint, to: RoutePoint, coast: CoastBuckets): Double {
        val steps = (metresBetween(from, to) / BAND_SAMPLE_M).toInt().coerceAtLeast(2)
        var inside = 0
        for (k in 0..steps) {
            val t = k.toDouble() / steps
            if (coast.distanceM(
                    from.latitude + (to.latitude - from.latitude) * t,
                    from.longitude + (to.longitude - from.longitude) * t
                ) <= BAND_M
            ) {
                inside++
            }
        }
        return inside.toDouble() / (steps + 1)
    }

    /**
     * The coastline's segments in a uniform bucket grid, so "how far is this point from the coast"
     * costs a neighbourhood walk rather than a sweep over every coastline vertex of a 90 km corridor.
     *
     * A bucket is [COAST_CELL_M] wide and a query reads the nine around the point's own, which is
     * exact for every distance under the cell's own side: a segment within 300 m of a point that
     * stands anywhere in its cell is within 300 m of that cell, so it lies in one of the nine. Past
     * that the answer is only "farther than the cell", which is all any caller here asks — every
     * caller's own threshold is the band.
     */
    private class CoastBuckets(coast: List<List<RoutePoint>>, box: BoundingBox) {
        private val latSouth = box.latSouth
        private val lonWest = box.lonWest
        private val cellLat = COAST_CELL_M / M_PER_DEG_LAT
        private val cellLon = COAST_CELL_M / (M_PER_DEG_LAT * cos(box.centerLat * PI / 180.0))
        private val payload = HashMap<Long, MutableList<Pair<RoutePoint, RoutePoint>>>()

        init {
            for (line in coast) {
                for (i in 0 until line.size - 1) {
                    val a = line[i]
                    val b = line[i + 1]
                    for (row in rowOf(minOf(a.latitude, b.latitude))..rowOf(maxOf(a.latitude, b.latitude))) {
                        for (col in colOf(minOf(a.longitude, b.longitude))..colOf(maxOf(a.longitude, b.longitude))) {
                            payload.getOrPut(key(row, col)) { ArrayList(2) }.add(a to b)
                        }
                    }
                }
            }
        }

        /** The distance (m) to the nearest coastline segment — "farther than a bucket" past a cell. */
        fun distanceM(latitude: Double, longitude: Double): Double {
            val point = RoutePoint(latitude, longitude)
            val row = rowOf(latitude)
            val col = colOf(longitude)
            var best = Double.MAX_VALUE
            for (r in row - 1..row + 1) {
                for (c in col - 1..col + 1) {
                    for (segment in payload[key(r, c)] ?: continue) {
                        val distance = pointToSegmentM(point, segment.first, segment.second)
                        if (distance < best) best = distance
                    }
                }
            }
            return best
        }

        private fun rowOf(latitude: Double): Int = ((latitude - latSouth) / cellLat).toInt().coerceAtLeast(0)
        private fun colOf(longitude: Double): Int = ((longitude - lonWest) / cellLon).toInt().coerceAtLeast(0)

        private fun key(row: Int, col: Int): Long =
            (row.toLong() shl 32) xor (col.toLong() and 0xffffffffL)
    }

    /**
     * **The shortcut pass measured on itself: the same search with the pass off, then on.**
     *
     * Off is the line the device draws today — this is the correction the fourth round's reading could
     * not make, because the pass's own effect on the drawn line has to be read from one search and one
     * world, never from two bakes or two revisions. Both runs take the shipped cap and the shipped
     * berth, so the difference between the two lines is the pass and nothing else, and the six metrics
     * are printed for each of them beside the count the pass itself reports.
     */
    private fun printShortcutEffect(
        mesh: ykws.android.maro.data.model.RouteMeshArrays,
        queries: PrebakedQueries,
        pairs: List<Pair<String, Pair<RoutePoint, RoutePoint>>>,
        rings: List<List<RoutePoint>>
    ) {
        val before = RouteSearch(mesh, queries, shortcutPass = false)
        val after = RouteSearch(mesh, queries)
        println(
            "route probe shortcut: the same search with the pass off and on — shipped cap " +
                "${AppConfig.routeTurnLateralAccelMps2} m/s2, shipped berth ${AppConfig.routeZoneBerthM} m"
        )
        println("route probe shortcut metrics: vertices (drawn) · total turn · worst turn · min zone clearance · length · ETA")
        for ((label, pair) in pairs) {
            val off = before.search(pair.first, pair.second, PROBE_CRUISE_KN)
            val on = after.search(pair.first, pair.second, PROBE_CRUISE_KN)
            if (off !is RouteResult.Success || on !is RouteResult.Success) {
                println("route probe shortcut [$label]: no route to compare ($off / $on)")
                continue
            }
            val offMetrics = measure(off.points, rings, PROBE_BERTH_M)
            val onMetrics = measure(on.points, rings, PROBE_BERTH_M)
            println(
                "route probe shortcut off [$label]: ${meshDetails(off).verticesBeforeShortcut} " +
                    "chain vertices · " +
                    "${off.points.size} drawn · total turn ${"%.0f".format(offMetrics.totalTurnDeg)}° · " +
                    "worst turn ${"%.0f".format(offMetrics.worstTurnDeg)}° · min zone clearance " +
                    "${"%.0f".format(offMetrics.minClearanceM)} m · " +
                    "${"%.2f".format(off.distanceM / 1000.0)} km · " +
                    "${"%.1f".format(off.durationSec / 60.0)} min"
            )
            println(
                "route probe shortcut on  [$label]: ${meshDetails(on).verticesBeforeShortcut} → " +
                    "${meshDetails(on).nodeIndices.size} chain vertices " +
                    "(${meshDetails(on).shortcutRefusedSegments} candidate(s) refused) · " +
                    "${on.points.size} drawn · total turn " +
                    "${"%.0f".format(onMetrics.totalTurnDeg)}° · worst turn " +
                    "${"%.0f".format(onMetrics.worstTurnDeg)}° · min zone clearance " +
                    "${"%.0f".format(onMetrics.minClearanceM)} m · " +
                    "${"%.2f".format(on.distanceM / 1000.0)} km · " +
                    "${"%.1f".format(on.durationSec / 60.0)} min"
            )
            println(
                "route probe shortcut gain[$label]: drawn vertices " +
                    "${"%.2f".format(off.points.size.toDouble() / on.points.size)}x · total turn " +
                    "${"%.2f".format(offMetrics.totalTurnDeg / onMetrics.totalTurnDeg)}x · worst turn " +
                    "${"%.2f".format(offMetrics.worstTurnDeg / onMetrics.worstTurnDeg)}x · length " +
                    "${"%.2f".format(off.distanceM / on.distanceM)}x · ETA " +
                    "${"%.2f".format(off.durationSec / on.durationSec)}x (the clock is the line's own — " +
                    "P3 reads it off the drawn polyline, not off the search's key, so this ratio is a " +
                    "ratio of two drawn lines)"
            )
        }
    }

    // ── The passages a berth would squeeze ────────────────────────────────────

    /**
     * Every passage a hard 25 m zone berth could **newly close**, named before the berth is trusted.
     *
     * A berth is an outward buffer on **both** faces of a gap, so it costs a passage 50 m of its
     * width: two zones standing less than 50 m apart stop being two zones and become one obstacle. A
     * zone less than 55 m off the coastline is the same story one-sided — the berth takes 25 m and
     * the bake's own 30 m channel floor does the rest. So both are measured, and the reading is
     * bounded below by that same 30 m floor: a narrower gap carries no route today, because the mesh
     * has no water in it, so it is not a passage any berth can take away. Every candidate is asked
     * the one question that decides the tripwire — **does a route that exists today pass through
     * it** — by testing the measured lines against the segment joining the two faces.
     */
    private fun printPassages(geometry: PrebakedRouteGeometry, samples: List<RouteSample>) {
        val gf = GeometryFactory()
        val frame = MetreFrame(geometry.box)
        val zones = geometry.zoneRings()
            .filter { it.size >= 3 }
            .mapNotNull { ring -> frame.line(ring, gf, close = true) }
        if (zones.isEmpty()) {
            println("route probe passages: no zone ring to measure")
            return
        }
        val zoneDistance = zones.map { IndexedFacetDistance(it) }

        println(
            "route probe passages: gaps a 25 m berth could newly close — zone to zone in " +
                "${FLOOR_M.toInt()}…${PASSAGE_M.toInt()} m, zone to coast in " +
                "${FLOOR_M.toInt()}…${COAST_PASSAGE_M.toInt()} m"
        )
        var zonePairs = 0
        var zoneNarrow = 0
        var zoneCrossed = 0
        for (i in zones.indices) {
            for (j in i + 1 until zones.size) {
                val gap = zoneDistance[i].distance(zones[j])
                if (gap >= PASSAGE_M) continue
                // At or below the floor there is no passage to lose: the mesh keeps no water there.
                if (gap < FLOOR_M) {
                    zoneNarrow++
                    continue
                }
                if (reportPassage(
                        "zone $i against zone $j", gap, DistanceOp.nearestPoints(zones[i], zones[j]), frame, samples
                    )
                ) {
                    zoneCrossed++
                }
                zonePairs++
            }
        }

        var coastPairs = 0
        var coastNarrow = 0
        var coastCrossed = 0
        val coastParts = geometry.coastlineLines().mapNotNull { line -> frame.line(line, gf, close = false) }
        if (coastParts.isNotEmpty()) {
            val coastShape = gf.createMultiLineString(coastParts.toTypedArray())
            val coast = IndexedFacetDistance(coastShape)
            for (i in zones.indices) {
                val gap = coast.distance(zones[i])
                if (gap >= COAST_PASSAGE_M) continue
                if (gap < FLOOR_M) {
                    coastNarrow++
                    continue
                }
                if (reportPassage(
                        "zone $i against the coastline",
                        gap,
                        DistanceOp.nearestPoints(zones[i], coastShape),
                        frame,
                        samples
                    )
                ) {
                    coastCrossed++
                }
                coastPairs++
            }
        }

        println(
            "route probe passages: $zonePairs zone-to-zone passage(s) and $coastPairs zone-to-coast " +
                "passage(s) a berth could close, of which $zoneCrossed and $coastCrossed carry a route " +
                "today; $zoneNarrow + $coastNarrow narrower gap(s) are already sealed by the " +
                "${FLOOR_M.toInt()} m floor; ${zones.size} zone ring(s) measured"
        )
    }

    /**
     * One gap, with the one answer that decides whether the berth is safe: does a route use it?
     *
     * @return true when a measured route passes through the gap.
     */
    private fun reportPassage(
        label: String,
        gapM: Double,
        nearest: Array<Coordinate>,
        frame: MetreFrame,
        samples: List<RouteSample>
    ): Boolean {
        val first = nearest[0]
        val last = nearest[1]
        val middle = frame.point((first.x + last.x) / 2.0, (first.y + last.y) / 2.0)
        val crossers = samples.filter { crosses(it.points, first, last, frame) }.map { it.label }
        val verdict = if (crossers.isEmpty()) {
            "no measured route passes through it"
        } else {
            "A ROUTE PASSES THROUGH IT: ${crossers.joinToString(", ")}"
        }
        println(
            "route probe passage [$label]: gap ${"%.0f".format(gapM)} m at " +
                "${"%.4f".format(middle.latitude)}, ${"%.4f".format(middle.longitude)} — $verdict"
        )
        return crossers.isNotEmpty()
    }

    /** Whether the line runs between the two faces — one of its legs cutting the gap's own segment. */
    private fun crosses(
        points: List<RoutePoint>,
        first: Coordinate,
        last: Coordinate,
        frame: MetreFrame
    ): Boolean {
        for (i in 0 until points.size - 1) {
            if (segmentsCross(frame.coordinate(points[i]), frame.coordinate(points[i + 1]), first, last)) {
                return true
            }
        }
        return false
    }

    /** A proper crossing only: a line that merely touches a face is not passing through the gap. */
    private fun segmentsCross(a: Coordinate, b: Coordinate, c: Coordinate, d: Coordinate): Boolean {
        val d1 = side(c, d, a)
        val d2 = side(c, d, b)
        val d3 = side(a, b, c)
        val d4 = side(a, b, d)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }

    private fun side(a: Coordinate, b: Coordinate, c: Coordinate): Double =
        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    /**
     * The bake's own local metre frame, so a 25 m buffer and a 25 m gap are the same 25 m the mesh
     * was built with: everything here is metres about the box centre, exactly as the builder does it.
     */
    private class MetreFrame(private val box: BoundingBox) {
        private val mPerDegLat = M_PER_DEG_LAT
        private val mPerDegLon = M_PER_DEG_LAT * cos(box.centerLat * PI / 180.0)

        fun coordinate(point: RoutePoint): Coordinate = Coordinate(
            (point.longitude - box.centerLon) * mPerDegLon,
            (point.latitude - box.centerLat) * mPerDegLat
        )

        fun point(x: Double, y: Double): RoutePoint =
            RoutePoint(box.centerLat + y / mPerDegLat, box.centerLon + x / mPerDegLon)

        /** A ring or a coastline line in metre coordinates, closed when a ring is wanted. */
        fun line(points: List<RoutePoint>, gf: GeometryFactory, close: Boolean): LineString? {
            val coordinates = ArrayList<Coordinate>(points.size + 1)
            for (point in points) coordinates.add(coordinate(point))
            if (close && coordinates.size >= 3 && coordinates.first() != coordinates.last()) {
                coordinates.add(coordinates.first())
            }
            return if (coordinates.size < 2) null else gf.createLineString(coordinates.toTypedArray())
        }
    }

    /**
     * What the eye calls jagged, as numbers, plus what the berth costs the line: how many vertices,
     * how much turning, how sharp, how close to a zone the line comes, how much of it lies inside the
     * berth, and its longest single run inside — the last being what separates a clean crossing from
     * a line that hugs an edge.
     */
    private class LineMetrics(
        val totalTurnDeg: Double,
        val worstTurnDeg: Double,
        val minClearanceM: Double,
        val berthExposureM: Double,
        val longestBerthRunM: Double
    )

    private fun measure(
        points: List<RoutePoint>,
        rings: List<List<RoutePoint>>,
        berthM: Double
    ): LineMetrics {
        var total = 0.0
        var worst = 0.0
        for (i in 1 until points.size - 1) {
            val incoming = headingDeg(points[i - 1], points[i])
            val outgoing = headingDeg(points[i], points[i + 1])
            val turn = abs(normaliseDeg(outgoing - incoming))
            total += turn
            if (turn > worst) worst = turn
        }
        val clearances = DoubleArray(points.size) { Double.MAX_VALUE }
        for ((index, point) in points.withIndex()) {
            for (ring in rings) {
                if (!ringNear(point, ring)) continue
                for (j in 0 until ring.size - 1) {
                    val d = pointToSegmentM(point, ring[j], ring[j + 1])
                    if (d < clearances[index]) clearances[index] = d
                }
            }
        }
        var clearance = Double.MAX_VALUE
        for (d in clearances) if (d < clearance) clearance = d

        // The berth's own readings, at the distance the caller names: a leg wholly inside the berth
        // counts its whole length, a leg that straddles the edge counts half of it, so a crossing
        // reads as two short runs and a hug as one long one.
        var exposure = 0.0
        var run = 0.0
        var longest = 0.0
        for (i in 0 until points.size - 1) {
            val legM = metresBetween(points[i], points[i + 1])
            val fromInside = clearances[i] < berthM
            val toInside = clearances[i + 1] < berthM
            when {
                fromInside && toInside -> {
                    exposure += legM
                    run += legM
                    if (run > longest) longest = run
                }
                fromInside != toInside -> {
                    exposure += legM / 2.0
                    run = 0.0
                }
                else -> run = 0.0
            }
        }
        return LineMetrics(
            totalTurnDeg = total,
            worstTurnDeg = worst,
            minClearanceM = if (clearance == Double.MAX_VALUE) Double.NaN else clearance,
            berthExposureM = exposure,
            longestBerthRunM = longest
        )
    }

    /** Geodesic metres between two route points, for the berth's exposure reading. */
    private fun metresBetween(from: RoutePoint, to: RoutePoint): Double {
        val dLat = (to.latitude - from.latitude) * PI / 180.0
        val dLon = (to.longitude - from.longitude) * PI / 180.0
        val sinHalfLat = kotlin.math.sin(dLat / 2)
        val sinHalfLon = kotlin.math.sin(dLon / 2)
        val a = sinHalfLat * sinHalfLat +
            cos(from.latitude * PI / 180.0) * cos(to.latitude * PI / 180.0) * sinHalfLon * sinHalfLon
        return 2.0 * SpatialOperations.EARTH_RADIUS_M * kotlin.math.asin(kotlin.math.sqrt(a))
    }

    /** A cheap bbox test before the segment walk, so the clearance costs nothing on a long route. */
    private fun ringNear(point: RoutePoint, ring: List<RoutePoint>): Boolean {
        val margin = CLEARANCE_PREFILTER_DEG
        return point.latitude > ring.minOf { it.latitude } - margin &&
            point.latitude < ring.maxOf { it.latitude } + margin &&
            point.longitude > ring.minOf { it.longitude } - margin &&
            point.longitude < ring.maxOf { it.longitude } + margin
    }

    private fun headingDeg(from: RoutePoint, to: RoutePoint): Double {
        val dLat = to.latitude - from.latitude
        val dLon = (to.longitude - from.longitude) * cos(from.latitude * PI / 180.0)
        return atan2(dLon, dLat) * 180.0 / PI
    }

    private fun normaliseDeg(angle: Double): Double {
        var a = angle
        while (a > 180.0) a -= 360.0
        while (a < -180.0) a += 360.0
        return a
    }

    private companion object {
        /** The scan's own resolution — fine enough to bound a bay, coarse enough to finish. */
        const val GAP_CELL_M = 200.0

        /**
         * Beyond this a water cell is a hole rather than sparse water: it is the coarsest spacing the
         * refinement ever targets offshore, so nothing legitimate is farther from a node than this.
         */
        const val GAP_RADIUS_M = 500.0

        /** The bake's own closing depth, repeated here as the verdict's own threshold. */
        const val DEPTH_GATE_M = 2.5

        /** How many gap areas the report prints, largest first. */
        const val MAX_REPORTED_GAPS = 10

        /** How many of the longest edges the corridor reading prints, longest first. */
        const val MAX_REPORTED_CORRIDORS = 10

        /**
         * An edge over this (m) is a corridor candidate and gets the full walk — well past any
         * legitimate refinement spacing, the offshore target being the coarsest at 500 m.
         */
        const val CORRIDOR_EDGE_M = 2_000.0

        /** How many of the largest zone boundaries get their own metric pair. */
        const val METRIC_ZONES = 3

        /** The pace the metrics are taken at. */
        const val PROBE_CRUISE_KN = 28.0

        /**
         * The comfort caps the sweep re-runs the pairs at (m/s²) — **0.5 to 4.0, the whole range the
         * loader accepts** (`ROUTE_TURN_LATERAL_ACCEL_MIN_MPS2` … `MAX_MPS2`). The row that chooses the
         * shipped default comes off this table, so it starts at the clamp's own floor: a sweep that
         * began at 1.0 never saw the widest setting a user can ask for.
         */
        val CAP_SWEEP_MPS2 = listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 4.0)

        /** What a leg on a zone's boundary may pay, as a multiple of its own time, in the berth sweep. */
        val BERTH_PRICE_SWEEP = listOf(1.0, 2.0, 3.0, 5.0)

        /** The berth distance the sweep prices at (m) — the distance the study asks for. */
        const val PROBE_BERTH_M = 25.0

        /** The window the inshore water is named by: Antibes, Golfe-Juan and Cap d'Antibes. */
        const val INSHORE_LAT_SOUTH = 43.530
        const val INSHORE_LAT_NORTH = 43.600
        const val INSHORE_LON_WEST = 7.050
        const val INSHORE_LON_EAST = 7.150

        /** The 300 m band itself: what makes a pair inshore rather than merely coastal. */
        const val BAND_M = 300.0

        /** How short an inshore pair is — a few kilometres, the shape the boat actually asks for. */
        const val INSHORE_PAIR_MIN_M = 1_200.0
        const val INSHORE_PAIR_MAX_M = 4_000.0

        /** How many inshore pairs the reading takes, and the step the band share is sampled at (m). */
        const val INSHORE_PAIRS = 4
        const val BAND_SAMPLE_M = 50.0

        /** The bucket grid the coastline is put in (m), so a distance to the coast stays local. */
        const val COAST_CELL_M = 500.0

        /** A degree of latitude in metres — the local frame's own scale. */
        val M_PER_DEG_LAT: Double = SpatialOperations.EARTH_RADIUS_M * PI / 180.0

        /** The system property that asks for the fine probe, and its spelling in the report. */
        const val PROBE_WINDOW = "maro.probeWindow"

        /** The window probe's own resolution — the depth grid's cell size, so nothing is sampled past. */
        const val WINDOW_CELL_M = 25.0

        /** A cap on the picture's size, so a mistyped window cannot print a novel. */
        const val MAX_WINDOW_STEPS = 240

        /** Two zones closer than this would become one obstacle under a 25 m berth on each face (m). */
        const val PASSAGE_M = 50.0

        /** A zone this close to the coast loses 25 m to the berth and 30 m to the channel floor (m). */
        const val COAST_PASSAGE_M = 55.0

        /** The bake's own 30 m channel floor: narrower gaps carry no route today, berth or not (m). */
        const val FLOOR_M = 30.0

        /** How far a node may be from a probed point and still count as the point's own stretch. */
        const val NODE_REACH_M = 2_000.0

        /** The bbox margin, in degrees, inside which a ring is worth measuring against. */
        const val CLEARANCE_PREFILTER_DEG = 0.02

        /**
         * The distance from a point to a segment, in metres, in the box's own local frame.
         *
         * In the companion rather than on the test, so the coastline buckets — a nested class, which
         * has no outer instance to reach through — measure a distance the same way every other
         * reading here does.
         */
        fun pointToSegmentM(point: RoutePoint, a: RoutePoint, b: RoutePoint): Double {
            val mPerDegLon = M_PER_DEG_LAT * cos(point.latitude * PI / 180.0)
            val px = (point.longitude - a.longitude) * mPerDegLon
            val py = (point.latitude - a.latitude) * M_PER_DEG_LAT
            val bx = (b.longitude - a.longitude) * mPerDegLon
            val by = (b.latitude - a.latitude) * M_PER_DEG_LAT
            val lengthSq = bx * bx + by * by
            if (lengthSq <= 0.0) return kotlin.math.sqrt(px * px + py * py)
            val t = ((px * bx + py * by) / lengthSq).coerceIn(0.0, 1.0)
            val dx = px - t * bx
            val dy = py - t * by
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }
    }
}
