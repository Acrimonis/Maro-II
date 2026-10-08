package ykws.android.maro.spatial

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.multipass.DepthBandLaw
import ykws.android.maro.spatial.multipass.EvolutiveGridPlan
import ykws.android.maro.spatial.multipass.FineWater
import ykws.android.maro.spatial.multipass.FineWaterQuery
import ykws.android.maro.spatial.multipass.LatticeAnchor
import ykws.android.maro.spatial.multipass.MultipassWorld
import ykws.android.maro.spatial.multipass.RouteGridPlan
import ykws.android.maro.spatial.multipass.SelectiveGridPlan
import ykws.android.maro.spatial.multipass.SelectiveMaskCache
import ykws.android.maro.spatial.multipass.depthBlockedAtOf
import ykws.android.maro.spatial.multipass.speedZonesInBox
import ykws.android.maro.spatial.multipass.strictestLimitKnAt
import java.io.File
import java.lang.reflect.Field
import java.util.Collections
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos

/**
 * **The JVM comparison harness — the `selective` acquisition priced against Adaptive on one fixed
 * fixture world.**
 *
 * This is P0 of the selective-perf evaluation plan
 * ([`261008_FEAT_PLN_Route_selective-perf-eval.md`](../../../../../../xTrack/Route/261008_FEAT_PLN_Route_selective-perf-eval.md)):
 * the instrument that lets P1–P4 be read without the device, with the device pass left as the
 * acceptance rather than the instrument. The device measured one pair through four arms and named the
 * structural ranking; this harness reproduces that ranking on the JVM, from the **same tokens the
 * device trace prints**, and pins it in a `@Test` — no absolute wall-clock, so JVM noise cannot fail a
 * correct fixture.
 *
 * **The instrument is the injected sink.** [`RouteAvoidEngine`] now takes an optional `traceSink`
 * (P0.0) and routes every reading through it, so this harness captures the grid's `ms`, the pull's
 * `priceReads`/`marks`/`priceMs`, the fine pass's own figures and each rung's `DEVICE PASS …
 * expansions=`, none of which is returned on any value. The device's `Log.i` path is byte-for-byte
 * unmoved: the sink is present here, `Log` is inert on the JVM.
 *
 * **What the fixture is.** A coast running the corridor's length, a shallow depth belt beside it
 * (offshore of the pair, so the depth gate walls the route in), the 300 m priced band, and one speed
 * zone south of the belt, so the zone-rim collar — the third of `selective`'s four — is non-empty and
 * both plans' fine layers are non-trivial. `AppConfig` is loaded from the shipped `maro.properties`
 * (the same file-and-reflection pattern [`RouteAvoidEngineTest`] uses), so the `route.selective.*`
 * collar widths and the `route.*.grid.*` metres are the live, shipped values; only the corridor reach
 * is narrowed, to keep a JVM run bounded.
 *
 * **What the three tokens mean.** The fine build is `GRID layer=fine … ms=` — the plan's mask marking,
 * where `selective`'s depth dilation and zone rim live. The price density is `priceReads / marks` over
 * the coarse `PULL`/`FINAL` lines: the depth source declares a zero clearance, so `selective`'s price
 * walk cannot prove a span and reads roughly once per mark (`≈ 1`), while Adaptive's band clearance
 * proves its spans (`well below 1`). The expansions are the coarse A*'s `DEVICE PASS … expansions=`.
 *
 * **The run recipe.** `gradlew :app:testDebugUnitTest --tests
 * "ykws.android.maro.spatial.RouteSelectivePerfEvalTest" -Pmaro.testStdout=true` — the flag is what lets
 * the `PERF-*` lines reach the `test-*.log` (D25); without it the suite stays quiet and the record is read
 * from the JUnit XML's `system-out`.
 */
class RouteSelectivePerfEvalTest {

    private val metresPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0

    /** The coast runs east-west at this latitude; land is north. */
    private val coastLat = 43.5200

    /** The pair sits this far (m) off the coast — inside the 300 m band, so the band prices the line. */
    private val pairOffshoreM = 200.0

    /**
     * The shallow belt sits **offshore of the pair**, spanning the corridor's length, so the depth gate
     * walls the route in and the pair cannot detour out of the band. Its own edge is the depth collar.
     */
    private val beltNearM = 280.0
    private val beltFarM = 380.0
    private val beltLatNorth = coastLat - beltNearM / metresPerDegLat
    private val beltLatSouth = coastLat - beltFarM / metresPerDegLat

    private val shallowDepthM = 2.0
    private val deepDepthM = 20.0

    private val origin = RoutePoint(coastLat - pairOffshoreM / metresPerDegLat, 7.0200)
    private val aim = RoutePoint(coastLat - pairOffshoreM / metresPerDegLat, 7.0450)

    /** Above the band's own 5 kn limit, so the band and the zone are priced, not just clocked. */
    private val paceKn = 25.0

    private val bandWidthM = 300.0

    /** One speed zone in open water south of the belt: it makes `selective`'s zone-rim collar mark. */
    private val zone = SpeedZone("z", "Cap", 4.0, squareRing(LatLng(43.5120, 7.0300), 400.0), emptyList())

    private val coastPoints = listOf(
        LatLng(coastLat, 6.9500),
        LatLng(coastLat, 7.0000),
        LatLng(coastLat, 7.0500),
        LatLng(coastLat, 7.1500)
    )
    private val coastSegments = coastPoints.zipWithNext()

    // ── The shipped properties, and the reflection load/restore ────────────────────

    /** The shipped `maro.properties`: the `app` module's CWD by default, `maro.repoDir` honoured first. */
    private val propertiesFile: File = System.getProperty("maro.repoDir")
        ?.let { File(it, "app/src/main/assets/maro.properties") }
        ?.takeIf { it.isFile }
        ?: File("src/main/assets/maro.properties")

    private val savedRouteFields = LinkedHashMap<Field, Any?>()

    /**
     * **Load the shipped `route.*` values into `AppConfig`** — the same file-and-reflection pattern the
     * engine's own tests use, generalised over the `route.` family (the field name is the key with its
     * dots removed), so the collar widths and the grid metres are the file's, not the code's fallbacks.
     * Every touched field is snapshotted first and restored in [restoreRouteConfig], so no other test
     * inherits this fixture's configuration. The corridor reach alone is narrowed: the shipped 3704 m
     * rasterises a kilometres-wide slice the JVM run has no use for.
     */
    @Before
    fun loadShippedRouteConfig() {
        val fields = AppConfig::class.java.declaredFields.filter { it.name.startsWith("route") }
        for (field in fields) {
            field.isAccessible = true
            savedRouteFields[field] = field.get(AppConfig)
        }
        if (propertiesFile.isFile) {
            val props = Properties().apply { propertiesFile.inputStream().use { load(it) } }
            for (key in props.stringPropertyNames()) {
                if (!key.startsWith("route.")) continue
                val field = fields.firstOrNull { it.name == key.replace(".", "") } ?: continue
                apply(field, props.getProperty(key)!!.trim())
            }
        }
        setDouble("routeAvoidCorridorReachM", 1500.0)
    }

    @After
    fun restoreRouteConfig() {
        for ((field, value) in savedRouteFields) {
            field.isAccessible = true
            when (field.type) {
                java.lang.Double.TYPE -> field.setDouble(AppConfig, value as Double)
                java.lang.Boolean.TYPE -> field.setBoolean(AppConfig, value as Boolean)
                java.lang.Integer.TYPE -> field.setInt(AppConfig, value as Int)
                java.lang.Float.TYPE -> field.setFloat(AppConfig, value as Float)
                else -> {}
            }
        }
        savedRouteFields.clear()
        SelectiveMaskCache.clear()
    }

    private fun apply(field: Field, raw: String) {
        when (field.type) {
            java.lang.Double.TYPE -> raw.toDoubleOrNull()?.let { field.setDouble(AppConfig, it) }
            java.lang.Boolean.TYPE -> raw.toBooleanStrictOrNull()?.let { field.setBoolean(AppConfig, it) }
            java.lang.Integer.TYPE -> raw.toIntOrNull()?.let { field.setInt(AppConfig, it) }
            java.lang.Float.TYPE -> raw.toFloatOrNull()?.let { field.setFloat(AppConfig, it) }
            else -> {}
        }
    }

    private fun setDouble(name: String, value: Double) {
        AppConfig::class.java.getDeclaredField(name).apply {
            isAccessible = true
            setDouble(AppConfig, value)
        }
    }

    // ── The gate ───────────────────────────────────────────────────────────────────

    /**
     * **The structural ranking, measured and asserted.**
     *
     * Three concurrent rungs share the **one build**: `sharedGrid` is single-flighted per arm, so the
     * arm's first rung rasterises the fine layer and the other two await that same build under the arm's
     * own lock. The count is **1** for both plans and no longer turns on `Dispatchers.Default`'s width,
     * because the arm's holder serialises the build's start rather than the dispatcher's scheduling
     * deciding it — the pre-P1.1 gate pinned `3`, and the device's "Adaptive once" was that same race.
     *
     * The plan-**dependent** ranking is the **price density**. `selective`'s depth source declares a zero
     * clearance, so its price walk proves no span and reads ≈ once per mark; Adaptive's band clearance
     * proves its spans, so its `priceReads / marks` sits at ≈ 0.5.
     *
     * **The fine build's cost is now split, and the split is reported, not asserted.** Each selective-only
     * pass — the depth collar, the depth-band write, the zone rim — is disabled in turn, so the pass that
     * carries the build's cost is measured rather than guessed. P2's levers bound exactly that pass, so the
     * fine build's own ms is the one figure they are meant to move: it is printed and never asserted, and
     * neither is the split's **relative** ms (D53) — JVM noise must not fail a correct fixture. What is
     * asserted instead is the **value the bound must leave unmoved** — the price reads, the marks and the
     * expansions, the walk's cells under each toggle, and the tile reuse (D54) — never a timing.
     *
     * **The fine layer is tiled since P4.2, so the ms readings are split cold/warm.** The first arm of a
     * fresh engine rasterises the fine tiles — the **cold** reading — and the second serves them from the
     * [`ykws.android.maro.spatial.multipass.FineTileMap`] and only assembles the windows. The counts and
     * the line are read from the **warm** run (the tile is the same water, so they are unmoved), while the
     * attribution uses the **cold** run, where the marking the split is about runs.
     *
     * **The P4.1 re-baseline — why the pinned counts moved, and that they moved on purpose.** The anchored
     * family (P4.1) draws both layers from the fixture's **fixed depth-raster anchor** ([`LatticeAnchor`])
     * instead of the corridor's south-west corner, so every cell centre shifts by up to half a fine cell and
     * the shoreline and the shallow belt re-sample. The three counts below therefore moved **deliberately** —
     * `priceReads` **7916 → 3135**, `marks` **7925 → 3141**, `expansions` **2650 → 2820** — and this is the
     * one phase whose re-sampling may legitimately move them, so the pin is re-baselined rather than
     * loosened.
     *
     * **The before-figure is the reproduced corridor anchor, not the recorded one.** `7916 / 7925 / 2650` is
     * what a corridor-anchor build reads on this fixture now — reproduced byte-for-byte on two runs, and the
     * figure this KDoc's pair is taken from. The `8217` this KDoc used to carry was the **P2/P3 recorded**
     * pin, and the **301-read gap** between that record and the reproduction (`8217 − 7916`) is
     * **unattributed** — fixture drift or harness run-sensitivity (three concurrent rungs behind a
     * process-static [`SelectiveMaskCache`]) — and is never folded into the anchor's reason.
     *
     * **The pin is a tight band, not an exact value (D23).** The counts reproduce across runs on this
     * machine, but they ride three concurrent rungs behind a process-static [`SelectiveMaskCache`], so a
     * different JVM, worker count or cell ordering may legitimately move them by a cell. Each is therefore
     * asserted within **±1 %** of its anchored value — `priceReads 3135`, `marks 3141`, `expansions 2820`
     * — loose enough that a cross-environment run cannot redden. The band's teeth are `priceReads` and
     * `expansions`, which move with the walk and the search, so a real regression there still fails (D27);
     * `marks` is line-driven and shared by both plans, so a mask regression that cannot bend the straight
     * fixture slips past it at any width — it is pinned as a shape, never as a guard. The printed `PERF-*`
     * record stays exact, and only the wall-clock `ms` is printed and never asserted.
     *
     * **The line's own reading is device-only (D3).** The **published line is unmoved** (**2015.8 m /
     * 783.7 s** before and after the anchor), but the fixture's pair is a straight run inside the priced
     * band, so the re-sampled lattice changes the search's cell path and its price reads without bending the
     * line — a shape the fixture **cannot** make bend, so the claim is true-by-fixture only and the harness
     * does **not** assert it. The line comparison belongs to the owed **device** pass (P4.6), where a real
     * pair can.
     */
    @Test
    fun theSelectiveArmingReproducesTheStructuralRanking() = runBlocking {
        val evolutive = measure(EvolutiveGridPlan)
        val selective = measure(SelectiveGridPlan)

        val noZoneRim = fineSplit(SelectiveNoZoneRimPlan, "no-zone-rim")
        val noDepthCollar = fineSplit(SelectiveNoDepthCollarPlan, "no-depth-collar")
        val noDepthBand = fineSplit(SelectiveNoDepthBandPlan, "no-depth-band")

        printRecord(evolutive)
        printRecord(selective)
        printSummary(evolutive)
        printSummary(selective)
        printSplit(selective, noZoneRim, noDepthCollar, noDepthBand)

        assertEquals(
            "one arming builds its fine layer once — the single-flight shares the one build across the rungs",
            1,
            selective.fineBuilds
        )
        assertEquals(
            "Adaptive shares the one build too: the single-flight is the shared sharedGrid path, not a plan property",
            1,
            evolutive.fineBuilds
        )

        // D54: a second arming reuses the first's cached tiles and marks none anew — the signal the dropped
        // warm-vs-cold check used to carry, now a **count** of `TILE built` lines rather than a timing.
        assertEquals(
            "the warm arm reuses the cold arm's tiles and builds none anew (D54)",
            0,
            selective.tileBuilds
        )
        assertTrue(
            "and the cold arm built them in the first place (${selective.coldTileBuilds} tiles)",
            selective.coldTileBuilds > 0
        )

        assertTrue(
            "selective's price reads are ungrouped, ≈ 1 per mark (measured ${selective.priceDensity})",
            selective.priceDensity >= 0.90
        )
        assertTrue(
            "Adaptive's price reads group, ≈ 0.5 per mark (measured ${evolutive.priceDensity})",
            evolutive.priceDensity <= 0.80
        )
        assertTrue(
            "selective's price density exceeds Adaptive's",
            selective.priceDensity > evolutive.priceDensity
        )

        // The ms readings are printed and never asserted (D46) — JVM noise must not fail a correct
        // fixture — so the cold/warm split is reported by printSplit alone, below. The plan-dependent
        // structural ranking the harness pins is the price density above and the counts below.

        // The bound is conservative under a wall at least a fine cell thick, so the depth collar and the
        // band write leave no cell's membership and no coefficient changed **on the measured fixture** — a
        // wall thinner than a fine cell is the one shape the bound does not cover. On it the search the
        // mask hands the walk expands the same cells and pulls the same line; the three counts are the
        // **anchored fixture's own** (re-baselined for P4.1, above), and a shift in any is the line or the
        // lattice moving, not a lever.
        assertWithinOnePercent(
            "the anchored fixture's price reads — the pull prices this line (corridor anchor read 7916)",
            3135L,
            selective.pullPriceReads
        )
        assertWithinOnePercent(
            "nor a mark — the walk reaches these cells (corridor anchor read 7925)",
            3141L,
            selective.pullMarks
        )
        assertWithinOnePercent(
            "nor an expansion — the search is unmoved (corridor anchor read 2650)",
            2820L,
            selective.expansions.toLong()
        )

        // D53: the split's ms — absolute **or relative** — is printed and never asserted, so JVM noise
        // cannot fail a correct fixture. The attribution the P2 levers rest on (the depth collar's marking
        // carrying the selective-only cost, well above the zone rim's) is read from `printSplit`'s own
        // PERF-SPLIT lines; what is asserted of the split is **structural** — each toggle changes the mask,
        // never the cut — and the counts above pin the value the bound must leave unmoved.
        assertEquals(
            "the depth-collar toggle changes the mask, never the cut — no fine window cell moves",
            selective.fineCells,
            noDepthCollar.fineCells
        )
        assertEquals(
            "the zone-rim toggle moves no fine window cell either",
            selective.fineCells,
            noZoneRim.fineCells
        )
        assertEquals(
            "the depth-band write prices, it never masks — no fine window cell moves",
            selective.fineCells,
            noDepthBand.fineCells
        )
    }

    /**
     * **D10 — the fixture keeps its three collars non-trivial.** The harness prices a mask the fixture's own
     * geography feeds — a coast collar, a zone rim and a depth collar — and nothing asserted that each still
     * **keeps** members, so a future change that emptied one (the belt moved out of the corridor, the zone
     * abandoned, the gate no longer blocking) would leave every downstream pin standing while the collar it
     * was meant to prove had quietly gone degenerate. This samples the fixture on its own fine cell and counts
     * each collar's members through the very laws the mask applies: the coast measure the sweep reads, the
     * zone's own ring distance, and [`DepthBandLaw.wallDistanceM`] on the gate's own predicate.
     */
    @Test
    fun theFixtureKeepsItsThreeCollarsNonTrivial() {
        val world = BeltWorld()
        val box = BBox(43.5000, 43.5300, 7.0000, 7.0600)
        val water = SelectiveGridPlan.fineWater(
            FineWaterQuery(
                world = world, box = box,
                edges = world.segmentsIn(box), openCoast = world.openCoastIn(box),
                marginM = AppConfig.routeAvoidObstacleMarginM, baseCellM = AppConfig.routeAvoidGridCellM
            )
        )
        val probe = depthBlockedAtOf(world, AppConfig.routeAvoidDepthGateMinM)
        val stepM = DepthBandLaw.stepM(AppConfig.routeSelectiveGridFineCellM)
        val stepDeg = AppConfig.routeSelectiveGridFineCellM / metresPerDegLat
        val zoneEdges = zone.outerRing.zipWithNext()

        var coastMembers = 0
        var zoneRimMembers = 0
        var depthCollarMembers = 0
        var lat = box.latSouth
        while (lat <= box.latNorth) {
            var lon = box.lonWest
            while (lon <= box.lonEast) {
                val at = LatLng(lat, lon)
                val coastM = world.distanceToCoastM(lat, lon)
                if (water.coastBandsM.any { coastM in it }) coastMembers++
                val rimM = zoneEdges.minOf { (a, b) -> SpatialOperations.pointToSegmentDistance(at, a, b) }
                if (rimM <= water.zoneRimM) zoneRimMembers++
                if (DepthBandLaw.wallDistanceM(at, probe, stepM, water.depthCollarM) <= water.depthCollarM) {
                    depthCollarMembers++
                }
                lon += stepDeg
            }
            lat += stepDeg
        }

        assertTrue("the coast collar keeps members ($coastMembers cells)", coastMembers > 0)
        assertTrue("the zone rim keeps members ($zoneRimMembers cells)", zoneRimMembers > 0)
        assertTrue("the depth collar keeps members ($depthCollarMembers cells)", depthCollarMembers > 0)
    }

    // ── The ladder, twice, warm run recorded ────────────────────────────────────────

    /**
     * One plan, one arm, run **twice** on a fresh engine — the second (warm) run is the record, because
     * `SelectiveMaskCache` is process-static and the JVM needs a warm-up before its numbers mean
     * anything. The captured sink of the warm run is summarized into a [PlanPerf].
     */
    private suspend fun measure(plan: RouteGridPlan): PlanPerf {
        val captured = Collections.synchronizedList(ArrayList<String>())
        val engine = RouteAvoidEngine(
            paceKn = { paceKn },
            aversionKn = { AppConfig.routeAvoidSpeedZoneSoftCostAversion },
            slowWaterBudgetPct = { AppConfig.routeAvoidSpeedZoneTimeBudgetPct },
            worldProvider = { BeltWorld() },
            plan = plan,
            traceSink = { line -> captured += line }
        )
        var cold = emptyList<String>()
        var warm = emptyList<String>()
        repeat(2) { run ->
            captured.clear()
            driveLadder(engine)
            if (run == 0) cold = captured.toList()
            if (run == 1) warm = captured.toList()
        }
        // Paying the tile map: the first arm builds the tiles, the second serves them, so the cold arm
        // is the marking work and the warm arm is the assembly. Both are summarised and the counts (the
        // line's own reads) are read from the warm run, where they are unmoved.
        val coldPerf = summarize(plan.name, cold)
        return summarize(plan.name, warm)
            .copy(
                coldFineBuildMs = coldPerf.fineBuildMs,
                coldFineCells = coldPerf.fineCells,
                coldTileBuilds = coldPerf.tileBuilds
            )
    }

    /**
     * **The full three-rung ladder** — `routesToCompute`, then `startLookup` for **all three** declared
     * computations without awaiting any, so all three rungs are in flight against the one shared grid,
     * then every terminal collected by its lookup id. Driving all three concurrently is what makes the
     * arm's single-flight observable: one build serves all three.
     */
    private suspend fun driveLadder(engine: RouteAvoidEngine) = coroutineScope {
        val declared = engine.routesToCompute(origin, aim) as? RouteDeclarations.Available
            ?: error("the fixture pair must declare its three rungs")
        val terminals = ConcurrentHashMap<RouteId, RouteUpdate>()
        val subscribed = CompletableDeferred<Unit>()
        val allDone = CompletableDeferred<Unit>()
        val collector = launch(Dispatchers.Default) {
            engine.updates
                .onStart { subscribed.complete(Unit) }
                .collect { update ->
                    if (update.nextStage == null) {
                        terminals[update.routeId] = update
                        if (terminals.size == declared.computations.size) allDone.complete(Unit)
                    }
                }
        }
        subscribed.await()
        declared.computations.forEach { engine.startLookup(it.id) }
        withTimeout(300_000) { allDone.await() }
        collector.cancel()
    }

    // ── The baseline record ─────────────────────────────────────────────────────────

    /** One engine's warm-run readings, parsed from the trace tokens the device itself prints. */
    private data class PlanPerf(
        val plan: String,
        val fineBuilds: Int,
        val fineBuildMs: Double,
        val fineCells: Int,
        val pullPriceReads: Long,
        val pullMarks: Long,
        val pullPriceMs: Double,
        val pullMs: Double,
        val expansions: Int,
        /** The published line's own length (m) — the shortest-clock rung, the one the preference keeps. */
        val lineDistanceM: Double,
        /** That same line's clocked duration (s). */
        val lineDurationSec: Double,
        val record: List<String>,
        /**
         * The **first** arm's fine build (ms) — a fresh engine, so the tile map holds nothing and the
         * reading is the marking work itself, the pre-P4 figure the split is measured on. The warm run
         * above serves cached tiles, so its fine build is the assembly alone and is not comparable with
         * the pre-P4 split's own.
         */
        val coldFineBuildMs: Double = 0.0,
        /** That first arm's fine window cells. */
        val coldFineCells: Int = 0,
        /** How many fine tiles the warm run built — 0 when the arm reused the cold run's tiles (D54). */
        val tileBuilds: Int = 0,
        /** How many fine tiles the cold (first) arm built. */
        val coldTileBuilds: Int = 0
    ) {
        /** `priceReads / marks` over the coarse pull — the density the memo leaves behind. */
        val priceDensity: Double
            get() = if (pullMarks == 0L) 0.0 else pullPriceReads.toDouble() / pullMarks.toDouble()
    }

    private fun summarize(plan: String, lines: List<String>): PlanPerf {
        val fineLines = lines.filter { it.startsWith("GRID layer=fine") }
        val tileLines = lines.filter { it.startsWith("TILE built") }
        val pullLines = lines.filter { it.startsWith("PULL ") || it.startsWith("FINAL ") }
        val deviceLines = lines.filter { it.startsWith("DEVICE PASS") }
        // Each rung emits its own settled `LINE`; the published answer is the rung the preference keeps,
        // the FAST default's own choice — the shortest clocked line — read here without touching the flow.
        val lineLines = lines.filter { it.startsWith("LINE ") }
        val published = lineLines.minByOrNull { metric(it)["duration"] ?: Double.MAX_VALUE }
        val record = lines.filter {
            it.startsWith("GRID ") || it.startsWith("PULL ") || it.startsWith("FINAL ") ||
                it.startsWith("FINE ") || it.startsWith("DEVICE PASS") || it.startsWith("LINE ")
        }
        return PlanPerf(
            plan = plan,
            fineBuilds = fineLines.size,
            fineBuildMs = fineLines.sumOf { kv(it)["ms"]?.toDoubleOrNull() ?: 0.0 },
            fineCells = fineLines.sumOf { kv(it)["cells"]?.toIntOrNull() ?: 0 },
            pullPriceReads = pullLines.sumOf { kv(it)["priceReads"]?.toLongOrNull() ?: 0L },
            pullMarks = pullLines.sumOf { kv(it)["marks"]?.toLongOrNull() ?: 0L },
            pullPriceMs = pullLines.sumOf { kv(it)["priceMs"]?.toDoubleOrNull() ?: 0.0 },
            pullMs = pullLines.sumOf { kv(it)["ms"]?.toDoubleOrNull() ?: 0.0 },
            expansions = deviceLines.sumOf { kv(it)["expansions"]?.toIntOrNull() ?: 0 },
            lineDistanceM = published?.let { metric(it)["distance"] } ?: 0.0,
            lineDurationSec = published?.let { metric(it)["duration"] } ?: 0.0,
            tileBuilds = tileLines.size,
            record = record
        )
    }

    /** The numeric `key=value` tokens of a `LINE`/`DEVICE PASS` line, unit suffixes (`m`, `s`) stripped. */
    private fun metric(line: String): Map<String, Double> = line
        .split(' ')
        .mapNotNull { token ->
            val eq = token.indexOf('=')
            if (eq <= 0) null else token.substring(0, eq) to token.substring(eq + 1).trimEnd('s', 'm').toDoubleOrNull()
        }
        .filter { it.second != null }
        .associate { it.first to it.second!! }

    private fun kv(line: String): Map<String, String> = line
        .split(' ')
        .mapNotNull { token ->
            val eq = token.indexOf('=')
            if (eq <= 0) null else token.substring(0, eq) to token.substring(eq + 1)
        }
        .toMap()

    private fun printRecord(perf: PlanPerf) {
        println("PERF-RECORD plan=${perf.plan} fineBuilds=${perf.fineBuilds}")
        for (line in perf.record) println("  [${perf.plan}] $line")
    }

    private fun printSummary(perf: PlanPerf) {
        println(
            "PERF-BASELINE plan=${perf.plan} fineBuilds=${perf.fineBuilds} " +
                "fineBuildMs=${perf.fineBuildMs} fineCells=${perf.fineCells} " +
                "priceReads=${perf.pullPriceReads} marks=${perf.pullMarks} " +
                "priceReadsPerMark=${perf.priceDensity} priceMs=${perf.pullPriceMs} pullMs=${perf.pullMs} " +
                "expansions=${perf.expansions} tileBuilds=${perf.tileBuilds} " +
                "lineDistanceM=${perf.lineDistanceM} lineDurationSec=${perf.lineDurationSec}"
        )
    }

    /**
     * **The D23 band** — a count within **± 1 %** of its anchored value, at least one cell wide. It cannot
     * redden on a cell's drift across environments, but its teeth are only the counts that move with the
     * walk and the search; the line-driven `marks` is pinned as a shape at any width (D27). The printed
     * record beside it stays exact.
     */
    private fun assertWithinOnePercent(name: String, anchored: Long, actual: Long) {
        val tolerance = maxOf(1L, ceil(anchored * 0.01).toLong())
        assertTrue(
            "$name: $actual outside ±1 % of the anchored $anchored (tolerance ±$tolerance)",
            actual in (anchored - tolerance)..(anchored + tolerance)
        )
    }

    // ── The cost split ──────────────────────────────────────────────────────────────

    /** One selective-only pass's marginal reading: the fine build with that pass disabled. */
    private data class FineSplit(val pass: String, val fineBuildMs: Double, val fineCells: Int)

    /**
     * **The selective plan with one of its fine-only marking passes disabled** — the P2.1 split's own
     * knobs, and nothing else moved. The plan is the seam P0 named, so the toggles ride it rather than any
     * config: a delegating plan reproduces `selective` cell for cell and answers one collar off (the mask's
     * own widths, read back through `toMask`) or the depth-band write off (the plan's own
     * `pricesDepthBand`). Each variant therefore prices the very same water minus one selective-only pass,
     * so the difference in the fine build is that pass's own cost.
     */
    private object SelectiveNoZoneRimPlan : RouteGridPlan by SelectiveGridPlan {
        override val name: String = "selective-no-zone-rim"
        override fun fineWater(query: FineWaterQuery): FineWater =
            SelectiveGridPlan.fineWater(query).copy(zoneRimM = 0.0)
    }

    private object SelectiveNoDepthCollarPlan : RouteGridPlan by SelectiveGridPlan {
        override val name: String = "selective-no-depth-collar"
        override fun fineWater(query: FineWaterQuery): FineWater =
            SelectiveGridPlan.fineWater(query).copy(depthCollarM = 0.0)
    }

    private object SelectiveNoDepthBandPlan : RouteGridPlan by SelectiveGridPlan {
        override val name: String = "selective-no-depth-band"
        override val pricesDepthBand: Boolean get() = false
    }

    /**
     * The warm-run fine build for one variant — the same two-run ladder [measure] drives, so the figure is
     * comparable with the baseline's own `fineBuildMs`.
     */
    private suspend fun fineSplit(plan: RouteGridPlan, pass: String): FineSplit {
        val perf = measure(plan)
        // The split is the marking the pass costs, so it is read from the cold arm where those passes run.
        return FineSplit(pass, perf.coldFineBuildMs, perf.fineCells)
    }

    /**
     * The split, one line per disabled pass: its fine build, its cells, and the marginal cost against the
     * full arm. Both sides are the **cold** arm — `fineSplit` reads the cold fine build, where the
     * selective-only passes actually run — so `costMs` is like for like and never mixes the warm assembly
     * with a cold marking (D47).
     */
    private fun printSplit(baseline: PlanPerf, vararg splits: FineSplit) {
        for (split in splits) {
            println(
                "PERF-SPLIT plan=selective pass=${split.pass} fineBuildMs=${split.fineBuildMs} " +
                    "fineCells=${split.fineCells} costMs=${baseline.coldFineBuildMs - split.fineBuildMs}"
            )
        }
    }

    // ── The fixture world ───────────────────────────────────────────────────────────

    /**
     * **The belt world** — a coast running east-west with land north, a shallow depth belt offshore of
     * the pair, and one priced speed zone. Sized so both plans' fine layers are non-trivial: `evolutive`
     * gets its coastal ribbon, `selective` its shore and band-outer coast collars, the zone rim and the
     * depth dilation.
     */
    private inner class BeltWorld : MultipassWorld {
        override val coastlineReady: Boolean get() = true
        override val depthReady: Boolean get() = true
        override val bandWidthM: Double get() = this@RouteSelectivePerfEvalTest.bandWidthM
        override val regionBounds: BBox? get() = null

        /**
         * The fixture's own **fixed** anchor — a stand-in for the depth raster's origin the live world
         * reports, and corridor-free, so P4.1's anchored family lays the same lines on every arm. The
         * longitude is the shipped region's west edge (`maro.region.lonWest`), the latitude a whole degree
         * south of the pair, and the reference latitude the corridor's own so the metre scale is unmoved.
         */
        override val latticeAnchor: LatticeAnchor get() = LatticeAnchor(43.0, 6.70, 43.5)

        override fun segmentsIn(box: BBox): List<ykws.android.maro.spatial.multipass.MultipassEdge> =
            emptyList()

        override fun openCoastIn(box: BBox): List<List<LatLng>> = listOf(coastPoints)

        override fun isWater(latitude: Double, longitude: Double): Boolean = latitude < coastLat

        override fun distanceToCoastM(latitude: Double, longitude: Double): Double {
            val p = LatLng(latitude, longitude)
            return coastSegments.minOf { (a, b) -> SpatialOperations.pointToSegmentDistance(p, a, b) }
        }

        override fun depthAt(latitude: Double, longitude: Double): DepthSample {
            val sounding = if (latitude in beltLatSouth..beltLatNorth) shallowDepthM else deepDepthM
            return DepthSample(sounding.toFloat(), DepthSource.LITTO3D, 100, true)
        }

        override fun speedZonesIn(box: BBox): List<SpeedZone> =
            speedZonesInBox(listOf(zone), box, emptySet())

        override fun zoneLimitKnAt(latitude: Double, longitude: Double): Double? =
            strictestLimitKnAt(listOf(zone), emptySet(), latitude, longitude)
    }

    /** A closed square of half-side [halfM] (m) centred on [centre], the speed zone's own ring. */
    private fun squareRing(centre: LatLng, halfM: Double): List<LatLng> {
        val mPerDegLon = metresPerDegLat * cos(Math.toRadians(centre.latitude))
        val dLat = halfM / metresPerDegLat
        val dLon = halfM / mPerDegLon
        return listOf(
            LatLng(centre.latitude - dLat, centre.longitude - dLon),
            LatLng(centre.latitude - dLat, centre.longitude + dLon),
            LatLng(centre.latitude + dLat, centre.longitude + dLon),
            LatLng(centre.latitude + dLat, centre.longitude - dLon),
            LatLng(centre.latitude - dLat, centre.longitude - dLon)
        )
    }
}
