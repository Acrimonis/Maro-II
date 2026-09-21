package ykws.android.maro.route

import ykws.android.maro.data.coastline.CoastlineSerializer
import ykws.android.maro.data.depth.DepthSerializer
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.DepthDatum
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.MutableDepthGrid
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.RegulatedZoneSerializer
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.data.regulation.SpeedZoneBuilder
import ykws.android.maro.data.regulation.SpeedZoneQuery
import ykws.android.maro.spatial.CoastlineSpatialIndex
import ykws.android.maro.spatial.SpeedZoneIndex
import java.io.File

/**
 * The read-only adapter over the app's spatial primitives — the Stage-0 twin of the epic's
 * `AppSpatialAdapter`, and the **only** file in this package that touches `spatial/` and
 * `data/regulation` (the epic's decoupling rule 2).
 *
 * Stage 0 runs in a JVM test with the real baked files instead of a `Context`, so the adapter takes
 * the deserialized objects directly; nothing here is Android-aware.
 */
class AssetRouteContext(
    private val grid: DepthGrid,
    private val coast: CoastlineSpatialIndex,
    private val zoneIndex: SpeedZoneIndex,
    private val exclusion: RouteExclusion,
) : RouteContext {

    override fun isWater(lat: Double, lon: Double): Boolean = coast.isWater(lat, lon)

    /**
     * With no coastline loaded — the synthetic regions of §13.3 — there is no coastal band, so the
     * answer is "infinitely far" rather than the empty index's zero, which would otherwise apply the
     * 300 m band's implied limit to every scenario and quietly drive the whole map at 5 kn.
     */
    override fun distanceToCoastM(lat: Double, lon: Double): Double =
        if (coast.hasData) coast.query(lat, lon).distanceMeters else Double.MAX_VALUE

    override fun zoneQuery(lat: Double, lon: Double) = zoneIndex.query(lat, lon)

    override fun depthM(lat: Double, lon: Double): Float = exclusion.depthAt(lat, lon)

    override fun isForbidden(lat: Double, lon: Double): Boolean = exclusion.isForbidden(lat, lon)

    override fun exclusionStandoffRatio(lat: Double, lon: Double): Double = exclusion.standoffRatio(lat, lon)

    override fun isBlocked(lat: Double, lon: Double): Boolean = exclusion.isBlocked(lat, lon)

    override fun isInsideCorridor(lat: Double, lon: Double): Boolean = exclusion.isInside(lat, lon)
}

/**
 * The pipeline of §13.3 steps 1–6, run end to end with the retries §10.5 states and nothing silent:
 * corridor → exclusion set (dilated by the standoff) → vertices → visibility edges → costs → A* →
 * fillets → speed profile → validation.
 *
 * Every attempt is recorded, so a `NoRoute` verdict arrives as a **reason** rather than a null
 * (§6), and a fallback standoff is visible in the output instead of blended into the result
 * (§11.2: "a silently relaxed route is not acceptable").
 */
object RouteEngine {

    data class RouteRun(
        val outcome: RouteOutcome,
        val validation: RouteValidator.Result?,
        /** One line per attempt, in order — margin, standoff, and what came of it. */
        val attempts: List<String>,
    )

    fun compute(
        start: LatLng,
        end: LatLng,
        config: RouteConfig,
        grid: DepthGrid,
        coast: CoastlineSpatialIndex,
        zones: List<SpeedZone>,
        zoneIndex: SpeedZoneIndex,
    ): RouteRun {
        val attempts = ArrayList<String>()
        val standoffTiers = listOf(config.standoffM) + listOfNotNull(config.fallbackStandoffM)
        var margin = config.corridorMarginM
        var lastReason = "no attempt ran"

        for (round in 0..config.corridorRetries) {
            for (standoff in standoffTiers) {
                val started = System.nanoTime()
                val label = "margin ${margin.toInt()} m · standoff ${"%.0f".format(standoff)} m"

                val exclusion = try {
                    RouteExclusion.build(grid, coast, config, RouteCorridor.bboxOf(start, end, margin), standoff)
                } catch (e: IllegalArgumentException) {
                    attempts.add("$label → ${e.message}")
                    lastReason = "the corridor does not overlap the depth grid"
                    continue
                }

                val context = AssetRouteContext(grid, coast, zoneIndex, exclusion)
                val cost = RouteCostModel(config, context)
                val obstacles = RouteObstacles.harvest(exclusion, zones, config)

                if (context.isForbidden(start.latitude, start.longitude) ||
                    context.isForbidden(end.latitude, end.longitude)
                ) {
                    val which = if (context.isForbidden(start.latitude, start.longitude)) "start" else "end"
                    attempts.add(
                        "$label → the $which sits in the exclusion set (${describe(context, start, end, which)})"
                    )
                    lastReason = "an endpoint is on land, too shallow or inside the standoff"
                    continue
                }

                val graph = RouteGraphBuilder(config, context, obstacles, cost).build(start, end)
                if (graph.outEdges[0].isEmpty()) {
                    attempts.add("$label → the start has no visible leg (${graph.vertexCount} vertices)")
                    lastReason = "the start is walled in at this standoff"
                    continue
                }

                val path = RouteSearch.search(graph, 0, 1, config, context, obstacles)
                if (path == null) {
                    attempts.add("$label → no visible chain (${graph.vertexCount} vertices, ${graph.edgeCount} edges)")
                    lastReason = "no chain of visible vertices joins the endpoints"
                    continue
                }

                val elapsedMs = (System.nanoTime() - started) / 1_000_000
                val measure = RouteMeasure(
                    vertices = graph.vertexCount,
                    edges = graph.edgeCount,
                    states = graph.directedCount,
                    nodesExpanded = path.nodesExpanded,
                    elapsedMs = elapsedMs,
                    corridorMarginM = margin,
                    standoffM = standoff,
                    harvestEpsilonM = exclusion.epsilonM,
                    corridorRetries = round,
                )
                val plan = RouteProfileBuilder(config, cost, context, obstacles).build(path, graph, measure)
                val validation = RouteValidator.validate(plan.points, config, context, obstacles)
                attempts.add(
                    "$label → found: ${graph.vertexCount} vertices, ${graph.edgeCount} edges, " +
                        "${path.nodesExpanded} expanded, ${elapsedMs} ms, ${"%.0f".format(plan.distanceM)} m, " +
                        "${"%.1f".format(plan.timeS)} s, ${plan.cornerCount} corners, ${validation.summary}"
                )
                return RouteRun(RouteOutcome.Found(plan), validation, attempts)
            }
            margin *= 2.0
        }
        attempts.add("every corridor and standoff tried; giving up with: $lastReason")
        return RouteRun(RouteOutcome.NoRoute(lastReason), null, attempts)
    }

    /**
     * Why an endpoint was refused, in the numbers D3 needs: a destination the *hard* gate rejects is
     * a different problem from one only the standoff margin rejects, and the two call for different
     * answers (a lower gate value, or a priced standoff rather than a wall).
     */
    private fun describe(context: RouteContext, start: LatLng, end: LatLng, which: String): String {
        val p = if (which == "start") start else end
        val depth = context.depthM(p.latitude, p.longitude)
        val depthText = if (depth.isNaN()) "no depth" else "${"%.2f".format(depth)} m deep"
        return when {
            context.isBlocked(p.latitude, p.longitude) ->
                "$depthText, blocked by the gate (land, NoData or under the minimum)"
            else ->
                "$depthText, clear of the gate but ${"%.0f".format(context.exclusionStandoffRatio(p.latitude, p.longitude) * 100)} % " +
                    "of the standoff, ${"%.0f".format(context.distanceToCoastM(p.latitude, p.longitude))} m to the coast"
        }
    }
}

/**
 * The Stage-0 harness of plan §13.3: it loads the **real baked region files** and prints the
 * measurements the feasibility question is answered with, rather than an opinion.
 *
 * Scenario 1 — the acceptance case — is read from the repository's own track files instead of being
 * typed here: the start is the first fix of [`🤿Le Rascoui.gpx`](🤿Le%20Rascoui.gpx:1), whose first
 * stop entry is Port de La Salis, and the destination is the fix furthest from that start inside
 * [`1ab42664-3665-4191-9081-be044b13b701.gpx`](1ab42664-3665-4191-9081-be044b13b701.gpx:1), the track
 * titled *La baie des Milliardaires* — the far end of the excursion whose name the destination
 * carries. `-Dmaro.route.from=lat,lon` and `-Dmaro.route.to=lat,lon` pin the pair exactly instead.
 *
 * Scenarios 2 and 3 need no assets at all: a synthetic region whose answer is known by hand, and a
 * boxed-in case whose only correct output is a stated reason.
 */
object RouteHarness {

    const val REGION_DEFAULT = "nice-menton"

    data class Assets(
        val regionId: String,
        val grid: DepthGrid,
        val coast: CoastlineSpatialIndex,
        val zones: List<SpeedZone>,
        val zoneIndex: SpeedZoneIndex,
    )

    data class Scenario(val name: String, val start: LatLng, val end: LatLng, val note: String = "")

    /** Repository root, as `app/build.gradle.kts` hands it to every test. */
    fun repoRoot(): File = System.getProperty("maro.repoDir")?.let { File(it) } ?: File("..")

    /** The three baked files the app ships, or null when any of them is missing (the test assumes). */
    fun loadAssets(regionId: String = REGION_DEFAULT, repoDir: File = repoRoot()): Assets? {
        val coastBin = File(repoDir, "data/app-assets/coastlines/$regionId.bin")
        val zoneBin = File(repoDir, "data/app-assets/regulated-zones/$regionId.bin")
        val depthBin = File(repoDir, "data/app-assets/depth/$regionId.bin")
        if (!coastBin.exists() || !zoneBin.exists() || !depthBin.exists()) return null

        val coastData = CoastlineSerializer.deserialize(coastBin.readBytes())
        val zones = SpeedZoneBuilder.build(RegulatedZoneSerializer.deserialize(zoneBin.readBytes()))
        return Assets(
            regionId = regionId,
            grid = DepthSerializer.deserialize(depthBin.readBytes()),
            coast = CoastlineSpatialIndex(coastData.allSegments),
            zones = zones,
            zoneIndex = SpeedZoneIndex(zones),
        )
    }

    /**
     * The acceptance scenario, or null when the two track files are not in the tree.
     *
     * @param accept which fixes may serve as an endpoint. The first fix of a track is a *berth*, and
     *        a berth is routinely under the depth gate — the Salis track's own first point reads
     *        0.30 m — so the harness takes the first fix the caller accepts rather than pretending
     *        the raw one is water. [`depthGate`] is the predicate to pass when a region is loaded.
     */
    fun acceptanceScenario(repoDir: File = repoRoot(), accept: (LatLng) -> Boolean = { true }): Scenario? {
        pinned("from")?.let { from ->
            pinned("to")?.let { to ->
                return Scenario("acceptance (pinned)", from, to, "endpoints pinned by -Dmaro.route.*")
            }
        }

        val salisTrack = File(repoDir, "🤿Le Rascoui.gpx")
        val bayTrack = File(repoDir, "1ab42664-3665-4191-9081-be044b13b701.gpx")
        if (!salisTrack.exists() || !bayTrack.exists()) return null

        val start = gpxPoints(salisTrack).firstOrNull(accept) ?: return null
        val bayPoints = gpxPoints(bayTrack).filter(accept)
        if (bayPoints.isEmpty()) return null
        val end = bayPoints.maxByOrNull {
            ykws.android.maro.spatial.SpatialOperations.haversine(start, it)
        } ?: return null
        return Scenario(
            name = "Baie des Milliardaires → Port de La Salis (from the track files)",
            start = start,
            end = end,
            note = "first and furthest accepted fix of each track, see the KDoc",
        )
    }

    /**
     * True when the depth grid carries a reading at this point that clears the minimum-depth gate —
     * the cheapest way for a caller to say "this fix is water" before the corridor exists.
     */
    fun depthGate(assets: Assets, config: RouteConfig, p: LatLng): Boolean {
        val grid = assets.grid
        val r = Math.floor((p.latitude - grid.boundingBox.latSouth) / grid.cellSizeDegLat).toInt()
        val c = Math.floor((p.longitude - grid.boundingBox.lonWest) / grid.cellSizeDegLon).toInt()
        if (r < 0 || r >= grid.rows || c < 0 || c >= grid.cols) return false
        val depth = grid.depthGated(r, c, config.emodnetCutoffM)
        return !depth.isNaN() && depth >= config.minDepthM.toFloat()
    }

    private fun pinned(key: String): LatLng? {
        val raw = System.getProperty("maro.route.$key") ?: return null
        val parts = raw.split(',')
        if (parts.size != 2) return null
        val lat = parts[0].trim().toDoubleOrNull() ?: return null
        val lon = parts[1].trim().toDoubleOrNull() ?: return null
        return LatLng(lat, lon)
    }

    /** Every `lat`/`lon` pair in a GPX file, in document order. */
    fun gpxPoints(file: File): List<LatLng> {
        val pattern = Regex("""lat="(-?[\d.]+)"\s+lon="(-?[\d.]+)"""")
        return pattern.findAll(file.readText()).mapNotNull { m ->
            val lat = m.groupValues[1].toDoubleOrNull()
            val lon = m.groupValues[2].toDoubleOrNull()
            if (lat == null || lon == null) null else LatLng(lat, lon)
        }.toList()
    }

    fun run(scenario: Scenario, config: RouteConfig, assets: Assets): RouteEngine.RouteRun =
        RouteEngine.compute(
            start = scenario.start,
            end = scenario.end,
            config = config,
            grid = assets.grid,
            coast = assets.coast,
            zones = assets.zones,
            zoneIndex = assets.zoneIndex,
        )

    /** The measurement block §13.3 asks for: nodes, milliseconds, length, time, zones, ratio. */
    fun report(scenario: Scenario, config: RouteConfig, run: RouteEngine.RouteRun): String {
        val header = buildString {
            appendLine("── ${scenario.name}")
            appendLine("   from ${fmt(scenario.start)} → ${fmt(scenario.end)}  (${scenario.note})")
            appendLine(
                "   config: standoff ${config.standoffM} m, cruise ${config.cruiseSpeedKn} kn, " +
                    "lateral ${config.maxLateralG} g, hardStandoff=${config.hardStandoff}, band300=${config.band300IsZone}"
            )
            run.attempts.forEach { appendLine("   · $it") }
        }
        return when (val outcome = run.outcome) {
            is RouteOutcome.NoRoute -> header + "   NO ROUTE — ${outcome.reason}"
            is RouteOutcome.Found -> {
                val plan = outcome.plan
                val zones = if (plan.zoneDistanceM.isEmpty()) {
                    "none"
                } else {
                    plan.zoneDistanceM.entries.sortedByDescending { it.value }
                        .joinToString(", ") { "${it.key} ${"%.0f".format(it.value)} m" }
                }
                header + buildString {
                    appendLine(
                        "   length ${"%.0f".format(plan.distanceM)} m " +
                            "(straight-line ratio ${"%.3f".format(plan.straightLineRatio)})"
                    )
                    appendLine(
                        "   time ${"%.1f".format(plan.timeS)} s = ETA ${fmtEta(plan.timeS)}, " +
                            "mean ${"%.1f".format(plan.averageSpeedKn)} kn"
                    )
                    appendLine(
                        "   corners ${plan.cornerCount} (${plan.measure.unfittedCorners} unfitted), " +
                            "vertices ${plan.measure.vertices}, edges ${plan.measure.edges}, " +
                            "states ${plan.measure.states}, expanded ${plan.measure.nodesExpanded}, " +
                            "${plan.measure.elapsedMs} ms"
                    )
                    appendLine("   in zones: $zones")
                    appendLine("   validation: ${run.validation?.summary ?: "not run"}")
                    appendLine("   corridor margin ${plan.measure.corridorMarginM.toInt()} m, " +
                        "harvest epsilon ${"%.2f".format(plan.measure.harvestEpsilonM)} m")
                }
            }
        }
    }

    fun fmt(p: LatLng): String = "${"%.5f".format(p.latitude)}, ${"%.5f".format(p.longitude)}"

    private fun fmtEta(seconds: Double): String {
        val s = seconds.toLong()
        return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    /**
     * A synthetic region: a rectangular grid of water whose depth comes from [depthAt], optionally
     * with speed zones. The answer is then known by hand, which is what scenario 2 of §13.3 asserts
     * against — and it needs no baked asset, so it runs on any machine.
     */
    fun syntheticAssets(
        bbox: BBox,
        depthAt: (lat: Double, lon: Double) -> Float,
        zones: List<SpeedZone> = emptyList(),
        coastSegments: List<CoastlineSegment> = emptyList(),
        resM: Double = 25.0,
    ): Assets {
        val grids = MutableDepthGrid.empty(
            "synthetic",
            BoundingBox(bbox.latSouth, bbox.latNorth, bbox.lonWest, bbox.lonEast),
            resM,
            DepthDatum.LAT,
        )
        for (r in 0 until grids.rows) {
            for (c in 0 until grids.cols) {
                val depth = depthAt(grids.cellCenterLat(r), grids.cellCenterLon(c))
                if (depth.isNaN()) continue
                grids.set(r, c, depth, DepthSource.LITTO3D, DepthSource.LITTO3D.seedConfidence)
            }
        }
        return Assets(
            regionId = "synthetic",
            grid = grids.toImmutable(null, 0L, "synthetic"),
            coast = CoastlineSpatialIndex(coastSegments),
            zones = zones,
            zoneIndex = SpeedZoneIndex(zones),
        )
    }

    /**
     * A `RouteContext` with no assets behind it — the seam the interface exists for, and what lets
     * the cost and geometry tests run without a baked region.
     */
    fun fakeContext(
        forbidden: (Double, Double) -> Boolean = { _, _ -> false },
        blocked: (Double, Double) -> Boolean = { _, _ -> false },
        inCorridor: (Double, Double) -> Boolean = { _, _ -> true },
        standoffRatio: (Double, Double) -> Double = { _, _ -> 1.0 },
        depth: (Double, Double) -> Float = { _, _ -> 20f },
        water: (Double, Double) -> Boolean = { _, _ -> true },
        coastDistanceM: (Double, Double) -> Double = { _, _ -> 5_000.0 },
        zoneQueryFn: (Double, Double) -> SpeedZoneQuery = { _, _ -> SpeedZoneQuery() },
    ): RouteContext = object : RouteContext {

        override fun isWater(lat: Double, lon: Double): Boolean = water(lat, lon)

        override fun distanceToCoastM(lat: Double, lon: Double): Double = coastDistanceM(lat, lon)

        override fun zoneQuery(lat: Double, lon: Double): SpeedZoneQuery = zoneQueryFn(lat, lon)

        override fun depthM(lat: Double, lon: Double): Float = depth(lat, lon)

        override fun isForbidden(lat: Double, lon: Double): Boolean = forbidden(lat, lon)

        override fun exclusionStandoffRatio(lat: Double, lon: Double): Double = standoffRatio(lat, lon)

        override fun isBlocked(lat: Double, lon: Double): Boolean = blocked(lat, lon)

        override fun isInsideCorridor(lat: Double, lon: Double): Boolean = inCorridor(lat, lon)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val config = RouteConfig()
        println("Route Stage-0 harness — region ${REGION_DEFAULT}, standoff ${config.standoffM} m")

        val assets = loadAssets()
        if (assets == null) {
            println("assets missing under ${repoRoot()}/data/app-assets — nothing to measure")
        } else {
            val scenario = acceptanceScenario(accept = { depthGate(assets, config, it) })
            if (scenario == null) {
                println("acceptance track files missing — pin the pair with -Dmaro.route.from/to")
            } else {
                val hard = run(scenario, config, assets)
                println(report(scenario, config, hard))
                val soft = run(scenario, config.copy(hardStandoff = false, band300IsZone = false), assets)
                println(report(scenario.copy(name = "${scenario.name} [priced standoff, no band]"), config, soft))
            }
        }
    }
}
