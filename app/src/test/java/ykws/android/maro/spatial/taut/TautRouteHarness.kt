package ykws.android.maro.spatial.taut

import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import ykws.android.maro.BuildConfig
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.coastline.CoastlineRepository
import ykws.android.maro.data.depth.DepthIsobaths
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.CoastlineData
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.RegulatedZoneSet
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.data.regulation.SpeedZoneBuilder
import ykws.android.maro.data.route.PrebakedInputs
import ykws.android.maro.spatial.CoastlineSpatialIndex
import ykws.android.maro.spatial.PricedZoneRef
import ykws.android.maro.spatial.RoutePlanTiming
import ykws.android.maro.spatial.RouteTurnGeometry
import ykws.android.maro.spatial.SpeedZoneIndex
import ykws.android.maro.spatial.SpatialOperations

/**
 * **The tracer measured on the world the app ships.**
 *
 * The design's own entry point was a Stage 0 harness, and this is it: the shipped `.bin` files read
 * through the app's own serializers, the engine run on the acceptance pair the trajectory study named —
 * **Baie des Milliardaires → Port de la Salis** — and the six metrics the incumbent's probe already
 * prints, so the two engines' lines can be read side by side rather than argued about.
 *
 * **Its cost is printed in three parts, never as one** (§16 step 1): the harvest, the graph build and
 * the search, each with the pair and node counts beside it, because the three causes were one number
 * before and 3 653 ms could not be attributed. Beside them stand the figures this engine alone owes: the
 * corridor's vertex and edge counts, the area its abstraction deletes, the distance it spends inside
 * each priced zone, the metres it spends **inside a zone's margin**, the seconds the berth's price and
 * §12.3's brake-and-accelerate pair came to, and the straight-line ratio — every one of them read off
 * the answer rather than asserted.
 *
 * It **measures, it does not gate**: the only assertion is that the acceptance pair is answered, and
 * that assertion is what the switch to this engine rests on. Gated by `-Dmaro.prebake=true` and skipping
 * when the baked world is absent, exactly like the incumbent's probe, so `gradlew test` never depends on
 * a bake.
 */
class TautRouteHarness {

    @Test
    fun measureTheTracerOnTheAcceptancePair() {
        Assume.assumeTrue(
            "set -Dmaro.prebake=true to run the route harness",
            System.getProperty("maro.prebake") == "true"
        )
        val region = BuildConfig.REGION_ID
        val repoDir = System.getProperty("maro.repoDir")?.let { File(it) } ?: File("..")
        val missing = PrebakedInputs.paths(repoDir, region).filterNot { it.exists() }
        Assume.assumeTrue(
            "harness skipped — no baked world to read: ${missing.joinToString { it.path }}",
            missing.isEmpty()
        )

        val inputs = PrebakedInputs.load(repoDir, region)
        val world = PrebakedTautWorld(inputs.coast, inputs.zones, inputs.depth)
        val refusals = mutableListOf<String>()
        val engine = TautRouteEngine(
            world = world,
            prepareWorld = null,
            // The shipped values come from the one home, read through the same accessors the app uses.
            warn = { message -> refusals.add(message) }
        )
        val paceKn = AppConfig.routeFreeWaterPaceKn.toDouble()

        println(
            "route harness [$region]: shipped cap ${AppConfig.routeTurnLateralAccelMps2} m/s2, " +
                "longitudinal ${AppConfig.routeTurnLongitudinalAccelMps2} m/s2, " +
                "shipped berth ${AppConfig.routeZoneBerthM} m, shore offset " +
                "${AppConfig.routeShoreOffsetM} m, pace $paceKn kn"
        )

        // **Two of the three are the end-off-water specimens** (item 5): their own coordinates resolve
        // onto land, so no way exists from them and the engine names *that* rather than blaming the
        // corridor for a route on water there is none of — each end's own readings are printed with the
        // refusal, which is what makes the name a reading rather than a claim.
        val pairs = listOf(
            "Baie des Milliardaires → Port de la Salis" to
                (RoutePoint(43.5458, 7.1242) to RoutePoint(43.5755, 7.1345)),
            "Cap d'Antibes → Golfe-Juan" to
                (RoutePoint(43.5600, 7.1300) to RoutePoint(43.5650, 7.0700)),
            "Antibes → Nice" to
                (RoutePoint(43.5700, 7.1250) to RoutePoint(43.6900, 7.2900))
        )

        for ((name, pair) in pairs) {
            refusals.clear()
            val startedAt = System.nanoTime()
            val answer = runBlocking { engine.route(pair.first, pair.second, paceKn) }
            val wallMillis = (System.nanoTime() - startedAt) / 1_000_000
            when (answer) {
                is RouteResult.Success -> printReading(name, pair, answer, world, paceKn, wallMillis)
                else -> {
                    println(
                        "route harness · $name: refused by name — $answer (${wallMillis} ms)"
                    )
                    for (line in refusals) println("    · ${line.substringAfter(": ")}")
                    // **Why the water refused**, read at the two ends rather than left to the reader's
                    // imagination: a refusal is a claim about the water, and whether an end is standing on
                    // land, inside the 2 m gate or inside the shore offset is the first thing to know.
                    for ((label, end) in listOf("start" to pair.first, "aim" to pair.second)) {
                        val sounding = world.depthSampleAt(end.latitude, end.longitude)
                        println(
                            "    · $label on water ${world.isWater(end.latitude, end.longitude)}, " +
                                "sounding ${sounding.depthM} m (data ${sounding.hasData}, source res " +
                                "${sounding.source.nominalResM} m), coast " +
                                "${"%.0f".format(world.distanceToCoastM(end.latitude, end.longitude))} m, " +
                                "zone ${world.pricedZoneAt(end.latitude, end.longitude)?.name ?: "none"}"
                        )
                    }
                }
            }
        }

        // **The traversal rule's own close condition** (§16 step 3): an answer that came out of run two is
        // a crossing only if no way around exists — and the corridor is the one assumption that could hide
        // one. So the acceptance pair is asked once more on a corridor grown to its cap, directly, and the
        // reading is printed: run one answering there would mean the crossing taken at 1 NM was not the
        // only way through.
        println(
            "route harness · Baie des Milliardaires → Port de la Salis: grown-corridor probe — " +
                relaxationProbe(world, pairs.first().second.first, pairs.first().second.second, paceKn)
        )

        val acceptance = runBlocking {
            engine.route(pairs.first().second.first, pairs.first().second.second, paceKn)
        }
        assertTrue(
            "the acceptance pair is answered: $acceptance",
            acceptance is RouteResult.Success
        )

        // **b1's own reading** (§19.4): the terrain's keeping changes nothing a reader of the line can see,
        // so what proves it *happened* is a **pair** — the dossier's `terrainReused` beside the harvest's
        // milliseconds on a second search of one engine — with the two lines printed beside each other, so a
        // line that moved cannot hide behind a search that got faster.
        println(
            "route harness · Baie des Milliardaires → Port de la Salis: " +
                reuseReading(world, pairs.first().second.first, pairs.first().second.second, paceKn)
        )

        // **The moved aim, warm against cold** (§19.5 C3) — the reading that decides the rest of the step,
        // and the one place where a reuse's divergence from a cold search is visible rather than argued.
        movedAimReading(world, pairs.first().second.first, pairs.first().second.second, paceKn)
    }

    /**
     * **Two searches of one fresh engine, the second of them on the terrain the first kept.**
     *
     * A fresh engine rather than the one above on purpose: this engine's first search is therefore genuinely
     * cold — nothing was kept before it — so the pair is the reading §19.4 asks for rather than a second
     * reuse measured against a first. **The reuse's own answer is compared with that first by price, not by
     * identity** (§19.5 C3's re-scope, taken at the user's word on 2026-09-21): the second search must be
     * **never dearer** than the first within the reading's own band ([TautReuseGuard]), and the two lines'
     * vertex counts are printed beside the verdict.
     *
     * **And it carries the graph's half of the story** (§19.6): the graph's own phase and pair count on
     * both searches, beside the harvest's and the wall clock's, because a keeping is only visible as a
     * **pair** — a phase of null milliseconds with a cold build's pair count would be a machine that was
     * merely fast, and the count is what tells the two apart without believing a stopwatch. The engine's
     * own log line is printed last, since that is the format a device drag is read in and the two readings
     * should be of one shape.
     */
    private fun reuseReading(
        world: TautWorld,
        start: RoutePoint,
        aim: RoutePoint,
        paceKn: Double
    ): String {
        val lines = mutableListOf<String>()
        val engine = TautRouteEngine(world = world, prepareWorld = null, warn = { lines.add(it) })
        fun ask(): Pair<RouteResult, Long> {
            val at = System.nanoTime()
            val answer = runBlocking { engine.route(start, aim, paceKn) }
            return answer to (System.nanoTime() - at) / 1_000_000
        }
        val (first, firstMs) = ask()
        val (second, secondMs) = ask()
        val firstSuccess = first as? RouteResult.Success
        val secondSuccess = second as? RouteResult.Success
        val firstDetails = firstSuccess?.details as? TautRouteDetails
        val secondDetails = secondSuccess?.details as? TautRouteDetails
        return "the terrain and the graph kept across two searches — first: terrain reused " +
            "${firstDetails?.terrainReused}, graph kept ${firstDetails?.graphReused}, harvest " +
            "${firstDetails?.harvestMillis} ms, graph " +
            "${firstDetails?.graphMillis} ms over ${firstDetails?.candidatePairs} pair(s), wall " +
            "$firstMs ms · second: terrain reused ${secondDetails?.terrainReused}, graph kept " +
            "${secondDetails?.graphReused}, harvest " +
            "${secondDetails?.harvestMillis} ms, graph ${secondDetails?.graphMillis} ms over " +
            "${secondDetails?.candidatePairs} pair(s), wall $secondMs ms · the reuse's own answer " +
            "against the cold one: ${reuseVerdict(firstSuccess, secondSuccess)} " +
            "(${firstSuccess?.points?.size} against ${secondSuccess?.points?.size} vertices) · the " +
            "second search's own line: ${lines.lastOrNull()?.substringAfter(": ") ?: "none"}"
    }

    /**
     * **The reuse's own answer against the cold one, as a price verdict** (§19.5 C3's re-scope): the guard
     * is that a reused answer is **never dearer** than a cold build's, within the reading's own band
     * ([TautReuseGuard]), rather than that the two lines are the same — which the drag's own reading
     * refuted, the warm line standing 19.42 m from the cold one on two of six aims and dearer by 0.01 s on
     * three.
     */
    private fun reuseVerdict(cold: RouteResult.Success?, warm: RouteResult.Success?): String {
        val coldSec = cold?.durationSec
        val warmSec = warm?.durationSec
        if (coldSec == null || warmSec == null) return "not comparable — one search did not answer"
        return "never dearer ${TautReuseGuard.neverDearer(warmSec, coldSec)} (gap " +
            "${"%.3f".format(warmSec - coldSec)} s of ${"%.2f".format(coldSec)} s, band " +
            "${"%.3f".format(TautReuseGuard.bandSec(coldSec))} s)"
    }

    /**
     * The same pair, asked on the corridor grown to the engine's own cap — the relaxation the design
     * allows, read from outside the engine so the reading is the harness's own rather than a claim.
     */
    private fun relaxationProbe(
        world: TautWorld,
        start: RoutePoint,
        aim: RoutePoint,
        paceKn: Double
    ): String {
        val berth = AppConfig.routeZoneBerthM.toDouble()
        val box = TautGraph.corridorBox(
            world, start, aim, TautRouteEngine.DEFAULT_CORRIDOR_GROWTH, berth
        )
        val terrain = TautTerrain.of(
            world, box, berth, AppConfig.routeShoreOffsetM.toDouble()
        )
        val obstacles = terrain.obstacles
        val graph = TautGraph.build(
            world, terrain, start, aim, paceKn
        )
        val outcome = TautSearch(
            world = world,
            obstacles = obstacles,
            graph = graph,
            cruiseSpeedKn = paceKn,
            lateralAccelMps2 = AppConfig.routeTurnLateralAccelMps2.toDouble(),
            warn = {},
            longitudinalAccelMps2 = AppConfig.routeTurnLongitudinalAccelMps2.toDouble()
        ).run()
        return when {
            outcome == null -> "no route even on the grown corridor (${graph.vertices.size} vertices)"
            outcome.zonePricedRun ->
                "still only a priced crossing (${outcome.crossings} leg(s), ${graph.vertices.size} vertices)"
            else ->
                "run one answers on the grown corridor — the 1 NM crossing was not the only way " +
                    "(${graph.vertices.size} vertices)"
        }
    }

    private fun printReading(
        name: String,
        pair: Pair<RoutePoint, RoutePoint>,
        success: RouteResult.Success,
        world: TautWorld,
        paceKn: Double,
        wallMillis: Long
    ) {
        val details = success.details as? TautRouteDetails
        val points = success.points
        val line = metricsOf(world, success)

        var unsoundedM = 0.0
        val perZoneM = LinkedHashMap<String, Double>()
        for (i in 0 until points.size - 1) {
            val legM = SpatialOperations.haversine(
                LatLng(points[i].latitude, points[i].longitude),
                LatLng(points[i + 1].latitude, points[i + 1].longitude)
            )
            val steps = max(2, kotlin.math.ceil(legM / DEPTH_CELL_M).toInt())
            for (step in 0 until steps) {
                val t = (step + 0.5) / steps
                val lat = points[i].latitude + (points[i + 1].latitude - points[i].latitude) * t
                val lon = points[i].longitude + (points[i + 1].longitude - points[i].longitude) * t
                val sample = world.depthSampleAt(lat, lon)
                if (!sample.hasData) unsoundedM += legM / steps
                val zone = world.pricedZoneAt(lat, lon)
                if (zone != null) {
                    perZoneM[zone.name] = (perZoneM[zone.name] ?: 0.0) + legM / steps
                }
            }
        }

        val straightM = SpatialOperations.haversine(
            LatLng(pair.first.latitude, pair.first.longitude),
            LatLng(pair.second.latitude, pair.second.longitude)
        )
        println("route harness · $name: $line")
        println(
            "route harness · $name: map ${details?.vertexCount} vertex(es), ${details?.edgeCount} edge(s), " +
                "${details?.candidatePairs} pair(s), ${details?.wallSegmentCount} wall segment(s), " +
                "band ${details?.bandSegmentCount} segment(s), corners ${details?.cornerCount} · deleted " +
                "${"%.0f".format((details?.deletedAreaM2 ?: 0.0) / 1_000_000.0)} km² · corners " +
                "${details?.spiralCorners} eased / ${details?.arcCorners} arc / " +
                "${details?.sharpCorners} sharp · largest radius " +
                "${"%.0f".format(details?.largestTurnRadiusM ?: 0.0)} m · chord deviation " +
                "${"%.2f".format(details?.chordDeviationM ?: 0.0)} m · sampling " +
                "${"%.3f".format(details?.samplingSec ?: 0.0)} s"
        )
        // The three figures §16 step 1 asks for, with the pair and node counts beside them — and the
        // wall clock kept apart, since it is the harness's own frame rather than the engine's work.
        println(
            "route harness · $name: harvest ${details?.harvestMillis} ms · graph " +
                "${details?.graphMillis} ms · search ${details?.searchMillis} ms · stations " +
                "${details?.nodesExpanded} expanded · wall clock ${wallMillis} ms"
        )
        println(
            "route harness · $name: corridor grown ${details?.corridorGrowth}· · sized from its " +
                "obstacles ${if (details?.corridorExpanded == true) "yes" else "no"} · zones " +
                "${if (details?.zonePricedRun == true) "priced" else "forbidden"} · crossings " +
                "${details?.crossings} · in-margin " +
                "${"%.2f".format((details?.inMarginM ?: 0.0) / 1000.0)} km · berth price " +
                "${"%.2f".format(details?.berthPriceSec ?: 0.0)} s · longitudinal " +
                "${"%.2f".format(details?.longitudinalSec ?: 0.0)} s · crossing " +
                "${success.forcedCrossingZoneNames.ifEmpty { "none" }} · unsounded " +
                "${"%.2f".format(unsoundedM / 1000.0)} km · straight-line ratio " +
                "${"%.3f".format(success.distanceM / straightM)}" +
                (perZoneM.entries.joinToString(separator = "") { " · ${it.key} ${"%.2f".format(it.value / 1000.0)} km" })
        )
        // §17 item 2's counts, per cause, and the charge they carry: they are not one number, and the
        // seconds beside them are the largest single term item 4's re-read has to attribute. The rule's
        // own count stands apart from the water's (item 2 of the level above), because a rule refusal
        // carries no charge at all.
        println(
            "route harness · $name: sharp corners ${details?.sharpCorners} — guard " +
                "${details?.sharpByGuard} · build ${details?.sharpByBuild} · cutback " +
                "${details?.sharpByCutback} · water ${details?.sharpByWater} · rule " +
                "${details?.sharpByRule} · charge " +
                "${"%.2f".format(details?.sharpChargeSec ?: 0.0)} s of the ${"%.2f".format(details?.longitudinalSec ?: 0.0)} s " +
                "the brake-and-accelerate pair cost"
        )
        // **The phantom's own quantity, readable where it mattered** (§17 item 5's first half). The
        // seconds the search accumulated, the berth's courtesy set aside, the drawn clock's own sum and
        // the gap between them: without these a recurrence of item 1's phantom on this pair would show
        // only as the seconds a *different* line moves, and the two would be indistinguishable.
        val pricedSeconds = (details?.pricedSec ?: 0.0) - (details?.berthPriceSec ?: 0.0)
        println(
            "route harness · $name: priced ${"%.2f".format(pricedSeconds)} s (berth " +
                "${"%.2f".format(details?.berthPriceSec ?: 0.0)} s set aside) · drawn " +
                "${"%.2f".format(success.durationSec)} s · gap " +
                "${"%.2f".format(pricedSeconds - success.durationSec)} s of " +
                "${"%.2f".format(pricedSeconds)} s priced"
        )
        // **The inside of a station, priced** (§19.2 item 1): with the stations and the search's own
        // milliseconds beside them, these four say whether the seconds are in *doing less work* or in
        // *making the work cheaper* — which is the question the vertex levers and the per-corner lever are
        // chosen on. A phase figure cannot answer it, and assuming it is how the tolerance lever looked
        // strong before it was measured.
        println(
            "route harness · $name: station inside — fits ${details?.fitsBuilt} " +
                "(${details?.fitMillis} ms) · clock ${details?.clockCalls} call(s) " +
                "(${details?.clockMillis} ms) · stations ${details?.nodesExpanded} expanded"
        )
        // And the fit's own inside (§19.2 item 2), because the two halves lead to different fixes: a dear
        // draw wants a cheaper curve, a dear judge wants a cheaper water test. The memo's own pair closes
        // it: **a hit rate near zero says the water's milliseconds are new work per candidate**, so the
        // lever is a cheaper question or a shorter ladder rather than a cache.
        //
        // **What this count carries that the candidate doors do not** (the revision's own should-fix):
        // `judgeAsked` is the **obstacles instance's** total — every `traversable` and `curveBlocked` asked
        // of it — so beside the search's candidate questions it also carries the **build's** own, asked
        // before the search opens: one water question per harvested corner (the filter deciding which
        // corners become vertices), the midpoint question behind every pair that survives the wall test, and
        // the ends' own gates. Those are counted and never timed (the clock sits in the search), which is
        // why `points + chords` falls short of this total by a constant the sampling levers cannot move.
        //
        // **The judge's own split, by kind** (walk item 5): the point questions against the chord
        // questions, beside the water's own milliseconds. They are the counters the built lever is read
        // through — the early exit on the first blocked chord is a *point* count, so a pair of runs that
        // does not print the two apart cannot show it — and they are **not** what the change is accepted
        // on: the guard is the six metrics and the drawn line, a cheaper walk cutting the memo entries
        // with the questions.
        println(
            "route harness · $name: fit inside — ${details?.candidatesBuilt} candidate(s) built · " +
                "draw ${details?.candidateBuildMillis} ms against water " +
                "${details?.candidateWaterMillis} ms · judge ${details?.judgeAsked} question(s) — " +
                "points ${details?.pointsAsked} · chords ${details?.chordsAsked} — " +
                "${details?.judgeMemoHits} from the memo (the judge is the instance's, so it carries the " +
                "build's own corner, midpoint and end questions, counted but not timed)"
        )
        // §17 item 5's lever, both halves: what the abstraction buys (the seconds the build spends) and
        // what it costs (the water it deletes, and the line quality it leaves the user to judge).
        // The tolerance is read from **one home** ([ABSTRACTION_TOLERANCE_M]) rather than from a product
        // spelled again here, which is how two copies of it could drift apart.
        println(
            "route harness · $name: tolerance ${"%.1f".format(ABSTRACTION_TOLERANCE_M)} m " +
                "(${TautObstacles.TOLERANCE_FRACTION} of the berth) — deletes " +
                "${"%.2f".format((details?.deletedAreaM2 ?: 0.0) / 1_000_000.0)} km² of water and leaves " +
                "${details?.sharpCorners} sharp corner(s) of " +
                "${(details?.spiralCorners ?: 0) + (details?.arcCorners ?: 0) + (details?.sharpCorners ?: 0)} " +
                "in the line, against ${"%.0f".format(line.turnDeg)}° of turn"
        )
        // **The band's own share, at fixed geometry** (item 4), and the band's span ends checked against
        // the world's own distance (item 5). One line, priced twice.
        println("route harness · $name: " + bandReading(world, pair.first, pair.second, paceKn, points))
    }

    /**
     * **The probe's own six figures for one answered line, read in one home** — vertices, total turn, worst
     * turn, zone clearance, kilometres and the ETA.
     *
     * One home because §19.5 C3's moved-aim reading sets a warm line beside a cold one and must read the
     * same six quantities the pair reading does: a second copy would drift, and the comparison would then
     * be between two different measurements.
     */
    private fun metricsOf(world: TautWorld, success: RouteResult.Success): LineMetrics {
        val points = success.points
        var totalTurnDeg = 0.0
        var worstTurnDeg = 0.0
        var minZoneClearanceM = Double.POSITIVE_INFINITY
        for (i in 1 until points.size - 1) {
            val turn = RouteTurnGeometry.turnRadians(points[i - 1], points[i], points[i + 1]) * 180.0 /
                Math.PI
            totalTurnDeg += turn
            worstTurnDeg = max(worstTurnDeg, turn)
        }
        for (point in points) {
            minZoneClearanceM = minOf(
                minZoneClearanceM,
                world.distanceToZoneM(point.latitude, point.longitude)
            )
        }
        return LineMetrics(
            vertices = points.size,
            turnDeg = totalTurnDeg,
            worstTurnDeg = worstTurnDeg,
            zoneClearanceM = minZoneClearanceM,
            distanceKm = success.distanceM / 1000.0,
            minutes = success.durationSec / 60.0
        )
    }

    /** The six figures above, as the line the harness prints — one spelling, so the two readings agree. */
    private data class LineMetrics(
        val vertices: Int,
        val turnDeg: Double,
        val worstTurnDeg: Double,
        val zoneClearanceM: Double,
        val distanceKm: Double,
        val minutes: Double
    ) {
        override fun toString(): String =
            "$vertices vertices · ${"%.0f".format(turnDeg)}° turn · worst " +
                "${"%.0f".format(worstTurnDeg)}° · zone clearance ${"%.0f".format(zoneClearanceM)} m · " +
                "${"%.2f".format(distanceKm)} km · ETA ${"%.2f".format(minutes)} min"
    }

    /**
     * **The moved aim, warm against cold, printed on every aim** (§19.5 C3) — the reading that decides the
     * rest of the step.
     *
     * One drag and one start. The **warm** side is the engine a drag would really use: the first aim
     * harvests and every aim after it reuses the terrain that first search kept. The **cold** side is a
     * fresh engine per aim, so every search harvests its own corridor and builds its own graph. Each aim
     * prints both lines' `points`, `legTimesSec`, `distanceM`, `durationSec`, the probe's six figures and
     * the reuse flag, beside the verdict the table is read for: **whether the warm line is dearer than the
     * cold one**, within the reading's own band ([TautReuseGuard]). The line and leg-time comparisons stay
     * printed per aim as the observations they are rather than as the verdict.
     *
     * A warm line may be **never dearer and still different** — the graph behind it is a superset of cold's,
     * so a taut line over it can bend elsewhere for no more seconds. That divergence was **accepted at the
     * user's word on 2026-09-21** (§19.5 C3's closing choice), which is what re-scoped the guard from *the
     * same line* to *never dearer*; it is still measured and printed here rather than streamlined away: the
     * licence's own hit rate, the two lines' six figures, and the leg times are the whole of the answer.
     */
    private fun movedAimReading(
        world: TautWorld,
        start: RoutePoint,
        aim: RoutePoint,
        paceKn: Double
    ) {
        val warm = TautRouteEngine(world = world, prepareWorld = null, warn = {})
        var answered = 0
        var reuses = 0
        var different = 0
        var legTimesDiffer = 0
        var dearer = 0
        var dearestGapSec = 0.0
        var worstApartM = 0.0
        var worstApartAt = 0
        var pastPolicy = 0
        var pastAnalogue = 0
        var worstBeyondM = 0.0
        var worstBeyondAt = 0
        for (step in 0..MOVED_AIM_STEPS) {
            val fraction = 1.0 - step * MOVED_AIM_FRACTION
            val moved = RoutePoint(
                start.latitude + (aim.latitude - start.latitude) * fraction,
                start.longitude + (aim.longitude - start.longitude) * fraction
            )
            val warmAnswer = runBlocking { warm.route(start, moved, paceKn) }
            val coldAnswer = runBlocking {
                TautRouteEngine(world = world, prepareWorld = null, warn = {})
                    .route(start, moved, paceKn)
            }
            val warmSuccess = warmAnswer as? RouteResult.Success
            val coldSuccess = coldAnswer as? RouteResult.Success
            if (warmSuccess == null || coldSuccess == null) {
                println(
                    "route harness · moved aim $step/$MOVED_AIM_STEPS " +
                        "[${"%.4f".format(moved.latitude)}, ${"%.4f".format(moved.longitude)}]: not " +
                        "answered — warm $warmAnswer, cold $coldAnswer"
                )
                continue
            }
            answered++
            val warmReused = (warmSuccess.details as? TautRouteDetails)?.terrainReused == true
            if (warmReused) reuses++
            val sameLine = warmSuccess.points == coldSuccess.points
            if (!sameLine) different++
            val sameLegTimes = warmSuccess.legTimesSec == coldSuccess.legTimesSec
            if (!sameLegTimes) legTimesDiffer++
            val gapSec = warmSuccess.durationSec - coldSuccess.durationSec
            if (gapSec > dearestGapSec) dearestGapSec = gapSec
            // **Dearer means dearer than the band, not dearer than float noise** — the verdict stands on
            // the same one home every other reading of the reuse guard uses (and it was 1e-6 before the
            // re-scope, which counted the drag's own +0.01 s as a divergence). The gap the dearest aim
            // carries is printed beside the count, since the count alone cannot say whether the band and
            // the reading are at the same edge.
            if (!TautReuseGuard.neverDearer(warmSuccess.durationSec, coldSuccess.durationSec)) dearer++
            // **The number §19.5's C3 left open** — how far apart the two lines stand in **metres**, which
            // is the quantity the choice between accepting the cache and building the slice turns on. The
            // two lines are each read against the other's own polyline, so the figure is the far side of
            // the divergence rather than a difference of two vertex counts, and the leg times' own gap is
            // printed beside it for the same reason: two lines can differ in shape and still cost the same.
            // **Its limit is stated where it is defined** ([lineApartM]): it samples each line's own
            // vertices, so a leg whose ends sit on the model and whose middle bows away reads 0.00 m while
            // its own seconds still move — which is what the leg-time gap beside it is there to catch.
            val apart = lineApartM(warmSuccess.points, coldSuccess.points)
            if (apart.first > worstApartM) {
                worstApartM = apart.first
                worstApartAt = step
            }
            // **The quantity a user comparing two drawn lines actually sees** (the revision's own
            // should-fix): the *metres of line* standing past the bound, not the worst single offset. A
            // hundred metres of a hundred-metre-wide excursion and one vertex a hundred metres off share a
            // maximum and are a very different thing on the screen, so both are printed.
            val beyondM = lineBeyondM(warmSuccess.points, coldSuccess.points, DIVERGENCE_POLICY_M)
            if (beyondM > worstBeyondM) {
                worstBeyondM = beyondM
                worstBeyondAt = step
            }
            if (apart.first > DIVERGENCE_POLICY_M) pastPolicy++
            if (apart.first > DIVERGENCE_ANALOGUE_M) pastAnalogue++
            val legGapMs = legTimeGapMs(warmSuccess.legTimesSec, coldSuccess.legTimesSec)
            val where = "[${"%.4f".format(moved.latitude)}, ${"%.4f".format(moved.longitude)}]"
            println(
                "route harness · moved aim $step/$MOVED_AIM_STEPS $where warm: " +
                    "${metricsOf(world, warmSuccess)} · ${warmSuccess.points.size} points · " +
                    "${warmSuccess.legTimesSec.size} leg time(s) · " +
                    "${"%.1f".format(warmSuccess.distanceM)} m · " +
                    "${"%.2f".format(warmSuccess.durationSec)} s · reuse $warmReused · the lines stand " +
                    "${"%.2f".format(apart.first)} m apart at most (mean ${"%.2f".format(apart.second)} m) · " +
                    "${"%.0f".format(beyondM)} m of the warm line stand past the " +
                    "${"%.1f".format(DIVERGENCE_POLICY_M)} m policy bound · " +
                    "leg times " + (legGapMs?.let { "apart by at most ${"%.1f".format(it)} ms" }
                    ?: "not comparable — the two lines carry different leg counts " +
                        "(${warmSuccess.legTimesSec.size} against ${coldSuccess.legTimesSec.size})")
            )
            println(
                "route harness · moved aim $step/$MOVED_AIM_STEPS $where cold: " +
                    "${metricsOf(world, coldSuccess)} · ${coldSuccess.points.size} points · " +
                    "${coldSuccess.legTimesSec.size} leg time(s) · " +
                    "${"%.1f".format(coldSuccess.distanceM)} m · " +
                    "${"%.2f".format(coldSuccess.durationSec)} s · reuse false · the same line $sameLine · " +
                    "the same leg times $sameLegTimes · warm ${"%.3f".format(gapSec)} s against cold " +
                    "(its own band ${"%.4f".format(TautReuseGuard.bandSec(coldSuccess.durationSec))} s)"
            )
        }
        println(
            "route harness · moved aim, the reading: $answered aim(s) answered · the licence held on " +
                "$reuses · the lines differed on $different · the leg times differed on $legTimesDiffer · " +
                "the warm line was dearer on $dearer (band ${TautReuseGuard.BAND_SEC} s absolute, " +
                "${TautReuseGuard.BAND_RELATIVE} of the cold line's own seconds) · the dearest warm line " +
                "stood ${"%.3f".format(dearestGapSec)} s above its cold build"
        )
        // **The divergence, stated with what each number is** (§19.5 C3's closing choice, corrected by the
        // revision). The bound is **a policy chosen at one tolerance, not a claim about two lines**:
        // `TOLERANCE_FRACTION` bounds **one** answer's offset from the truth, so two lines each inside it
        // can stand **twice** it apart. The policy — the error a single drawn line is already allowed — and
        // the honest analogue that twice gives are both printed, and the measured worst is reported against
        // both, so a figure past one and inside the other is read as what it is rather than as a verdict.
        println(
            "route harness · moved aim, the two lines' own divergence: within " +
                "${"%.2f".format(worstApartM)} m on every aim (worst at aim $worstApartAt of " +
                "$MOVED_AIM_STEPS) · ${"%.0f".format(worstBeyondM)} m of line stand past the policy bound " +
                "(most at aim $worstBeyondAt) · $pastPolicy aim(s) past the " +
                "${"%.1f".format(DIVERGENCE_POLICY_M)} m policy bound (one tolerance — the error a " +
                "single line is allowed) and $pastAnalogue past the honest analogue " +
                "${"%.1f".format(DIVERGENCE_ANALOGUE_M)} m (two answers each within that tolerance) · " +
                "inside the policy the cache stands as it is; the analogue is the real head-room of a " +
                "comparison, so a figure between the two says what the number is rather than what it " +
                "decides, and the choice past it stays the user's"
        )
    }

    /**
     * **How far apart two drawn lines stand, in metres — a vertex-sampled two-sided maximum, and no more
     * than that.** The figure is the worst offset of the one line from the other, with the mean of both
     * directions' own means beside it.
     *
     * Each line's **vertices** are read against the *other line's polyline* rather than against its vertex
     * list, because the two lines need not share a vertex where they differ: a bend moved by a hundred
     * metres would compare as a handful of near-coincident vertices and hide inside a vertex-to-vertex
     * reading. Both directions are taken, since neither line's own vertices need sample the other's bend,
     * and the larger of the two is what is reported.
     *
     * **Its limit, stated honestly: it samples points, so it can read zero while the lines differ.** A leg
     * whose own ends sit on the other line while its middle bows away is invisible here — on the drag's six
     * aims, aims 1–3 read **0.00 m** apart while their own leg times move by **4.4, 4.9 and 14.1 ms** — so
     * the leg-time gap is printed beside this figure and the **metres of line standing past the bound**
     * ([lineBeyondM]) are printed beside it too: a maximum offset answers "how far at worst", never "how
     * much of the line", and those are different questions about two drawn lines.
     */
    private fun lineApartM(a: List<RoutePoint>, b: List<RoutePoint>): Pair<Double, Double> {
        if (a.isEmpty() || b.isEmpty()) return 0.0 to 0.0
        val ab = offsetsM(a, b)
        val ba = offsetsM(b, a)
        return max(ab.first, ba.first) to (ab.second + ba.second) / 2.0
    }

    /** One direction's own reading: `(worst offset, mean offset)` of [from]'s vertices against [to]. */
    private fun offsetsM(from: List<RoutePoint>, to: List<RoutePoint>): Pair<Double, Double> {
        var worst = 0.0
        var sum = 0.0
        for (point in from) {
            val nearest = offsetToM(point.latitude, point.longitude, to)
            if (nearest > worst) worst = nearest
            sum += nearest
        }
        return worst to sum / from.size
    }

    /** One point's nearest distance to a polyline, or to its single point — the one home of that question. */
    private fun offsetToM(latitude: Double, longitude: Double, to: List<RoutePoint>): Double {
        if (to.size == 1) {
            return SpatialOperations.haversine(
                LatLng(latitude, longitude),
                LatLng(to[0].latitude, to[0].longitude)
            )
        }
        var nearest = Double.MAX_VALUE
        for (i in 0 until to.size - 1) {
            nearest = min(
                nearest,
                SpatialOperations.pointToSegmentDistance(
                    LatLng(latitude, longitude),
                    LatLng(to[i].latitude, to[i].longitude),
                    LatLng(to[i + 1].latitude, to[i + 1].longitude)
                )
            )
        }
        return nearest
    }

    /**
     * **How many metres of [a] stand further than [thresholdM] from [b]** — the length reading beside
     * [lineApartM]'s worst offset, and the quantity a user comparing two drawn lines actually sees.
     *
     * [lineApartM] answers *how far, at worst*, by reading each line's own vertices; this answers *how much
     * of it*, by walking [a]'s legs in steps of [PAST_BOUND_STEP_M] and adding a step wherever its own
     * middle stands past the bound. The step is a sampling resolution like any other, and it is named with
     * the reading rather than left implicit: 5 m against a 12.5 m bound resolves the excursion two orders
     * of magnitude finer than the bound itself.
     */
    private fun lineBeyondM(a: List<RoutePoint>, b: List<RoutePoint>, thresholdM: Double): Double {
        if (a.size < 2 || b.isEmpty()) return 0.0
        var beyondM = 0.0
        for (i in 0 until a.size - 1) {
            val legM = SpatialOperations.haversine(
                LatLng(a[i].latitude, a[i].longitude),
                LatLng(a[i + 1].latitude, a[i + 1].longitude)
            )
            val steps = max(1, ceil(legM / PAST_BOUND_STEP_M).toInt())
            val stepM = legM / steps
            for (step in 0 until steps) {
                val t = (step + 0.5) / steps
                val lat = a[i].latitude + (a[i + 1].latitude - a[i].latitude) * t
                val lon = a[i].longitude + (a[i + 1].longitude - a[i].longitude) * t
                if (offsetToM(lat, lon, b) > thresholdM) beyondM += stepM
            }
        }
        return beyondM
    }

    /** The two lines' leg times, as one gap in milliseconds — null when they carry different leg counts. */
    private fun legTimeGapMs(a: List<Double>, b: List<Double>): Double? {
        if (a.size != b.size) return null
        var worst = 0.0
        for (i in a.indices) worst = max(worst, abs(a[i] - b[i]) * 1000.0)
        return worst
    }

    /**
     * **The band's own share as a charge at fixed geometry, and the band's span ends against the world.**
     *
     * The same drawn chain is priced **twice**: once under the band's spans — the two-piece rule §18.1
     * built, and what the engine charges — and once under the per-leg **midpoint** value that pass
     * removed. Both prices are taken at the world's own nominal over the drawn legs, so the only thing
     * that differs between them is the band's rule, and the gap between the two figures is the band's own
     * share on this very line rather than the seconds a different line moves. The rest of §18.1's clock
     * rise is the line's own shape, which the vertex and turn readings beside this one carry.
     *
     * The same walk reads every band span's own ends against `distanceToCoastM`. The spans are solved in
     * the corridor's **frame metres** while the world's distance is the coastline index's **own
     * projection** — two different arithmetic answering one question — so an end that is not the band's
     * width away is a defect the engine's own closed form cannot see. Caps and slab ends alike: a cap's
     * root stands the width from the endpoint, a slab's the width from the segment's line with its
     * projection on the segment.
     */
    private fun bandReading(
        world: TautWorld,
        start: RoutePoint,
        aim: RoutePoint,
        paceKn: Double,
        points: List<RoutePoint>
    ): String {
        val berth = AppConfig.routeZoneBerthM.toDouble()
        val box = TautGraph.corridorBox(world, start, aim, TautRouteEngine.DEFAULT_CORRIDOR_GROWTH, berth)
        val terrain = TautTerrain.of(world, box, berth, AppConfig.routeShoreOffsetM.toDouble())
        val obstacles = terrain.obstacles
        val graph = TautGraph.build(world, terrain, start, aim, paceKn)
        // The band comes off the terrain rather than being rebuilt: it is what the engine charges with,
        // and a second one built here would be a second opinion about the same strip.
        val band = terrain.band
        var spannedSec = 0.0
        var midpointSec = 0.0
        var worstLocusM = 0.0
        var spanCount = 0
        var legs = 0
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            legs++
            for (piece in graph.drawnPieces(a, b, Double.MAX_VALUE)) {
                spannedSec += RoutePlanTiming.legSeconds(piece.lengthM, piece.limitKn, paceKn)
            }
            midpointSec += graph.pricing.midpointBandPriceSec(a, b)
            if (band != null) {
                val pa = obstacles.frame.pt(LatLng(a.latitude, a.longitude))
                val pb = obstacles.frame.pt(LatLng(b.latitude, b.longitude))
                for (span in band.spansOf(pa, pb)) {
                    spanCount++
                    for (t in listOf(span.from, span.to)) {
                        // **Only an interior end is a claim about the width's locus.** An end at t = 0 or
                        // t = 1 is the leg's own end: the leg begins or finishes inside the band, which is
                        // where its span begins or ends, and its distance to the coast is wherever the
                        // search put it. Reading those as crossings reported a worst case of 274 m, which
                        // was this walk measuring the wrong points rather than the band being wrong.
                        if (t <= 0.0 || t >= 1.0) continue
                        val point = obstacles.frame.latLng(
                            Pt(pa.x + (pb.x - pa.x) * t, pa.y + (pb.y - pa.y) * t)
                        )
                        val distance = world.distanceToCoastM(point.latitude, point.longitude)
                        val off = abs(distance - world.coastalBandWidthM)
                        if (off > worstLocusM) worstLocusM = off
                    }
                }
            }
        }
        val share = midpointSec - spannedSec
        return "the band's own share — $legs drawn leg(s), $spanCount band span(s) · spans " +
            "${"%.2f".format(spannedSec)} s against the removed midpoint rule's " +
            "${"%.2f".format(midpointSec)} s · the band's share ${"%.2f".format(share)} s " +
            "(${"%.2f".format(share / 60.0)} min) · band span ends off the width's own locus by at most " +
            "${"%.2f".format(worstLocusM)} m"
    }

    private companion object {
        /** The depth feature's own grid resolution — how finely a leg is walked for the readings. */
        const val DEPTH_CELL_M = 25.0

        /** How many steps the moved-aim drag is walked in, and how far each step pulls the aim back. */
        const val MOVED_AIM_STEPS = 5
        const val MOVED_AIM_FRACTION = 0.05

        /** The step [lineBeyondM] walks a leg in — the resolution the "metres past the bound" reading has (m). */
        private const val PAST_BOUND_STEP_M = 5.0

        /**
         * **The abstraction's own tolerance in metres — one product, one home** (`berth ×
         * `TOLERANCE_FRACTION``).
         *
         * Both bounds below are read off this, so the product is spelled once inside this harness instead
         * of twice, and neither bound can drift from the tolerance it is drawn from.
         */
        val ABSTRACTION_TOLERANCE_M: Double
            get() = AppConfig.routeZoneBerthM * TautObstacles.TOLERANCE_FRACTION

        /**
         * **The policy bound the warm line is judged against** (§19.5 C3's closing choice) — **the
         * abstraction's tolerance itself, taken as a policy chosen at one tolerance rather than derived
         * from the quantity it judges.**
         *
         * **What it is, and what it is not.** It is *not* "the error every drawn corner is already allowed
         * to spend", read as a claim about two lines: `TOLERANCE_FRACTION` bounds **one** answer's offset
         * from the truth, and two answers each inside it can stand **twice** it apart — so a figure past
         * this bound is not by itself a defect, and saying it were would decide the question the reading
         * exists to leave open ([DIVERGENCE_ANALOGUE_M] is that twice, printed beside this one). It *is*
         * the error the engine already accepts of a **single** drawn line, which is the honest reason to
         * take it as the policy: a warm line inside it stands inside the error the engine has already spent
         * on the line it drew, and the analogue printed beside it names the real head-room of a comparison.
         */
        val DIVERGENCE_POLICY_M: Double get() = ABSTRACTION_TOLERANCE_M

        /**
         * **The honest analogue: two drawn lines each within one tolerance of the truth can stand twice it
         * apart.** Printed beside [DIVERGENCE_POLICY_M] on every run so neither number is read alone.
         */
        val DIVERGENCE_ANALOGUE_M: Double get() = 2.0 * ABSTRACTION_TOLERANCE_M
    }
}

/**
 * **The band a reused answer is judged against** (§19.5 C3's decision, taken at the user's word on
 * 2026-09-21) — **the reading's own band rather than a tolerance chosen to let a guard pass.**
 *
 * The guard on a reuse is a **price** one: a reused answer may differ from a cold build's for the same aim
 * and must not be **dearer**. It replaced the equality — "the reused answer *is* the cold one" — when C3's
 * drag reading showed the two lines standing 0.00 m apart on four of six aims and 19.42 m on two, with the
 * warm line **dearer by 0.01 s on three** of them and cheaper on the two it diverges on.
 *
 * A band is needed at all because the quantity compared is a clock: the same code's own seconds move by
 * more than a hair between runs (§19.3's pair of runs, the caution §19.6's device reading is stated with),
 * so a guard of zero tolerance would fail on the machine rather than on the code. Its size is therefore the
 * reading's own — **0.01 s absolute**, the +0.01 s the drag carries, and **~6e-6 relative**, that movement
 * as a share of a route some 1 600 s long. It is **one home**: the harness's moved-aim verdict and the
 * focused guard in `TautTerrainTest` both read it, so neither can drift from the other.
 */
internal object TautReuseGuard {

    /** The absolute band: the +0.01 s the drag's own reading carries on the three aims it moves (s). */
    const val BAND_SEC = 0.01

    /** The relative band beside it: the same movement as a share of the cold line's own seconds. */
    const val BAND_RELATIVE = 6e-6

    /** The slack a cold line of [coldSec] seconds earns — the wider of the two bands. */
    fun bandSec(coldSec: Double): Double = max(BAND_SEC, BAND_RELATIVE * coldSec)

    /** Whether a reused answer's [warmSec] is no dearer than a cold build's [coldSec] within the band. */
    fun neverDearer(warmSec: Double, coldSec: Double): Boolean = warmSec - coldSec <= bandSec(coldSec)
}

/**
 * **The baked world, read as a [TautWorld].**
 *
 * It is the harness's own adapter, beside the one the app uses, and it reads the same `.bin` files
 * through the same serializers and the same indices the shipped engine does — so the numbers the harness
 * prints describe the artefact the app loads rather than a second world invented for the reading.
 */
internal class PrebakedTautWorld(
    private val coast: CoastlineData,
    private val zones: RegulatedZoneSet,
    private val depth: DepthGrid
) : TautWorld {

    private val coastIndex = CoastlineSpatialIndex(coast.allSegments)
    private val zoneList: List<SpeedZone> = SpeedZoneBuilder.build(zones)
    private val zoneIndex = SpeedZoneIndex(zoneList)

    /**
     * **The harness's world is ready by construction**, which is exactly what makes it the app's world:
     * the three `.bin` files are read before the engine is handed it, so the readiness the shipped engine
     * would wait on is already true here — the reading is of the shipped layers, not of a world that
     * could answer permissively.
     */
    override val readiness: TautWorldReadiness get() = TautWorldReadiness.READY

    /**
     * **This world never moves, so its generation never does.** The three `.bin` files are read once, before
     * the engine is handed the world, and nothing in the harness mutates them — a constant is the honest
     * reading of "nothing here changes", and it is what lets the harness's own loop keep a terrain.
     */
    override val generation: Int = 0

    private var contourGrid: DepthGrid? = null
    private var contours: List<List<LatLng>> = emptyList()

    override fun isWater(latitude: Double, longitude: Double): Boolean =
        coastIndex.isWater(latitude, longitude)

    override fun pricedZoneAt(latitude: Double, longitude: Double): PricedZoneRef? {
        val zone = zoneIndex.query(latitude, longitude).allInsideZones.firstOrNull() ?: return null
        return PricedZoneRef(zone.id, zone.name, zone.speedLimitKn)
    }

    override fun distanceToZoneM(latitude: Double, longitude: Double): Double =
        abs(zoneIndex.query(latitude, longitude).distanceToBoundaryM ?: Double.POSITIVE_INFINITY)

    /**
     * The 300 m band's own live limit, **read from the one home that owns it** — the same accessor the
     * shipped adapter reads, so the harness prices the band at what the app prices it at rather than at a
     * test literal of its own (item 4's last should-fix).
     */
    override val coastalBandSpeedLimitKn: Double get() = AppConfig.zoneRegulatorySpeedKn.toDouble()

    /** The band's own half-width, from the coastline feature that owns it. */
    override val coastalBandWidthM: Double get() = CoastlineRepository.ZONE_DISTANCE_M

    /**
     * The coastline polylines that meet [box], **read the way the shipped adapter reads them** — the
     * index's own bounding-box walk, then each polyline whole. Returning every polyline of the region
     * instead would have made the harness's harvest a different question from the app's: the corridor
     * would be handed shapes a hundred kilometres away and would size itself from them, so the reading
     * would describe a world the app never loads (item 5's last should-fix).
     */
    override fun landPolylinesIn(box: BoundingBox): List<List<LatLng>> {
        if (!coastIndex.hasData) return emptyList()
        val bbox = BBox(box.latSouth, box.latNorth, box.lonWest, box.lonEast)
        val wanted = LinkedHashSet<Int>()
        for (segment in coastIndex.segmentsInBbox(bbox)) wanted.add(segment.polylineIdx)
        val out = ArrayList<List<LatLng>>(wanted.size)
        for (polylineIdx in wanted) {
            val polyline = coast.allSegments.getOrNull(polylineIdx) ?: continue
            val points = polyline.points.map { LatLng(it.lat.toDouble(), it.lon.toDouble()) }
            if (points.size >= 2) out.add(points)
        }
        return out
    }

    override fun zoneShapesIn(box: BoundingBox): List<TautZoneShape> =
        zoneList.filter { zone -> zone.outerRing.any { inside(it, box) } }.map { zone ->
            TautZoneShape(zone.id, zone.name, zone.speedLimitKn, zone.outerRing, zone.holes)
        }

    override fun shallowContoursIn(box: BoundingBox): List<List<LatLng>> {
        if (contourGrid !== depth) {
            contours = DepthIsobaths.build(depth, levels = listOf(TautObstacles.SHALLOW_GATE_M.toFloat()))
                .flatMap { isobath -> isobath.lines.map { it.points } }
            contourGrid = depth
        }
        return contours.filter { line -> line.any { inside(it, box) } }
    }

    override fun depthSampleAt(latitude: Double, longitude: Double): DepthSample =
        depth.depthAt(latitude, longitude)

    override fun distanceToCoastM(latitude: Double, longitude: Double): Double =
        coastIndex.query(latitude, longitude).distanceMeters

    override val depthBox: BoundingBox get() = depth.boundingBox

    private fun inside(point: LatLng, box: BoundingBox): Boolean =
        point.latitude >= box.latSouth && point.latitude <= box.latNorth &&
            point.longitude >= box.lonWest && point.longitude <= box.lonEast
}
