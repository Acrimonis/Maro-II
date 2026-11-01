package ykws.android.maro.spatial

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.multipass.EvolutiveGridPlan
import ykws.android.maro.spatial.multipass.GateTestFixtures
import ykws.android.maro.spatial.multipass.GateTestFixtures.at
import ykws.android.maro.spatial.multipass.MultipassEdge
import ykws.android.maro.spatial.multipass.MultipassWorld
import ykws.android.maro.spatial.multipass.RouteGridPlan
import ykws.android.maro.spatial.multipass.ShallowEdgeIndex
import ykws.android.maro.spatial.multipass.UniformGridPlan
import java.util.Collections

/**
 * **Route todo 11 — the coast-boxed end.** An end standing at the head of an inlet narrower than twice the
 * coast's margin (`route.avoid.obstacle.marginM`): every cell of the inlet past the end's own disc stands
 * within the margin of a wall, so the grid holds the end in a pocket of its own disc, while the water beyond
 * the mouth is open and the depth deep everywhere. The ends sit clear of the repair's clearance, so nothing
 * moves them.
 *
 * The geometry, in metres north and east of the fixture's origin: a land block north of 400 m, notched by
 * an inlet centred on 700 m east and reaching 500 m into it, or by a marina's basin behind a mouth; the inlet
 * end 50 m short of the inlet's head (the basin's centre), the open end 300 m south of the shore. Every row
 * the registry ships is run — `evolutive_plus` (the default), `evolutive` and `avoid` — each way.
 */
class RouteCoastBoxedEndTest {

    private val paceKn = 12.0
    private val shoreN = 400.0
    private val headN = 900.0
    private val axisE = 700.0
    private val inletEnd = at(headN - 50.0, axisE)
    private val openEnd = at(100.0, axisE)

    /**
     * A land block whose shore carries [notch] — the (north, east) corners between its west and east shore
     * points, counter-clockwise; deep water everywhere, and no 3 m line at all.
     */
    private class ShoreWorld(shoreN: Double, notch: List<Pair<Double, Double>>) : MultipassWorld {
        private val raster = GateTestFixtures.RasterWorld(
            GateTestFixtures.raster(200, 200, 10.0) { _, _ -> 10.0 },
            bandWidthM = 300.0
        )

        /** The land's ring, counter-clockwise in (east, north): the shore with its notch, then the block. */
        private val ring: List<Pair<Double, Double>> =
            listOf(shoreN to 0.0) + notch + listOf(shoreN to 1900.0, 1900.0 to 1900.0, 1900.0 to 0.0)
        private val edges: List<MultipassEdge> = ring.indices.map { k ->
            val (an, ae) = ring[k]
            val (bn, be) = ring[(k + 1) % ring.size]
            MultipassEdge(at(an, ae), at(bn, be), LandRingOrientation.CCW_RING)
        }

        override val coastlineReady: Boolean get() = true
        override val depthReady: Boolean get() = true
        override val bandWidthM: Double get() = raster.bandWidthM
        override val regionBounds: BBox? get() = null
        override fun segmentsIn(box: BBox): List<MultipassEdge> = edges
        override fun openCoastIn(box: BBox): List<List<LatLng>> = emptyList()
        override fun depthAt(latitude: Double, longitude: Double): DepthSample = raster.depthAt(latitude, longitude)
        override fun shallowEdges(minDepthM: Double): ShallowEdgeIndex = raster.shallowEdges(minDepthM)

        override fun isWater(latitude: Double, longitude: Double): Boolean {
            val n = (latitude - GateTestFixtures.LAT0) * GateTestFixtures.M_PER_DEG_LAT
            val e = (longitude - GateTestFixtures.LON0) * GateTestFixtures.M_PER_DEG_LON
            var inside = false
            for (k in ring.indices) {
                val (an, ae) = ring[k]
                val (bn, be) = ring[(k + 1) % ring.size]
                if ((an > n) != (bn > n) && e < ae + (n - an) / (bn - an) * (be - ae)) inside = !inside
            }
            return !inside
        }

        override fun distanceToCoastM(latitude: Double, longitude: Double): Double {
            val p = LatLng(latitude, longitude)
            return edges.minOf { SpatialOperations.pointToSegmentDistance(p, it.a, it.b) }
        }
    }

    /** An inlet [widthM] wide centred on the axis, reaching from the shore to its head. */
    private fun inlet(widthM: Double): ShoreWorld {
        val west = axisE - widthM / 2.0
        val east = axisE + widthM / 2.0
        return ShoreWorld(shoreN, listOf(shoreN to west, headN to west, headN to east, shoreN to east))
    }

    /**
     * A 300 m square basin, its centre on the inlet end, entered from the shore by a mouth [mouthWidthM] wide
     * and 100 m long — a marina's shape: the end stands 150 m clear of every wall, inside open water of its
     * own, and only the mouth is narrower than the margin's two sides.
     */
    private fun basin(mouthWidthM: Double): ShoreWorld {
        val west = axisE - mouthWidthM / 2.0
        val east = axisE + mouthWidthM / 2.0
        val (n0, n1) = (headN - 50.0 - 150.0) to (headN - 50.0 + 150.0)
        val (e0, e1) = (axisE - 150.0) to (axisE + 150.0)
        return ShoreWorld(
            shoreN,
            listOf(shoreN to west, n0 to west, n0 to e0, n1 to e0, n1 to e1, n0 to e1, n0 to east, shoreN to east)
        )
    }

    /** One engine row, built as [RouteEngineChoice] builds it, with the trace captured. */
    private class Row(val id: String, val plan: RouteGridPlan, val anyAngle: Boolean)

    private val rows = listOf(
        Row("evolutive_plus", EvolutiveGridPlan, anyAngle = true),
        Row("evolutive", EvolutiveGridPlan, anyAngle = false),
        Row("avoid", UniformGridPlan, anyAngle = false)
    )

    private class Outcome(val line: List<LatLng>?, val reason: RouteReason?, val declared: Boolean, val trace: List<String>)

    private fun solveMain(row: Row, world: MultipassWorld, from: LatLng, to: LatLng): Outcome = runBlocking {
        val trace = Collections.synchronizedList(mutableListOf<String>())
        val engine = RouteAvoidEngine(
            paceKn = { paceKn },
            verdictWeight = { AppConfig.routeAvoidSlowWaterWeight },
            worldProvider = { world },
            plan = row.plan,
            traceSink = { line -> trace += line },
            anyAngle = row.anyAngle
        )
        try {
            val declared = engine.routesToCompute(RoutePoint(from.latitude, from.longitude), RoutePoint(to.latitude, to.longitude))
                as? RouteDeclarations.Available ?: return@runBlocking Outcome(null, null, false, trace.toList())
            coroutineScope {
                val subscribed = CompletableDeferred<Unit>()
                val done = CompletableDeferred<RouteUpdate?>()
                val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    engine.updates.onStart { subscribed.complete(Unit) }
                        .collect { u -> if (u.nextStage == null && !done.isCompleted) done.complete(u) }
                }
                subscribed.await()
                engine.startLookup(declared.computations[0].id)
                val update = withTimeout(120_000) { done.await() }
                collector.cancel()
                val success = update?.result as? RouteResult.Success
                Outcome(success?.points?.map { it.toLatLng() }, update?.reason, true, trace.toList())
            }
        } finally {
            engine.close()
        }
    }

    /** Each row, each direction, between the inlet end and the open end over [world]. */
    private fun everyRowBothWays(world: MultipassWorld): List<Pair<String, Outcome>> = rows.flatMap { row ->
        listOf("${row.id} out" to solveMain(row, world, inletEnd, openEnd), "${row.id} in" to solveMain(row, world, openEnd, inletEnd))
    }

    private fun describe(o: Outcome): String =
        if (!o.declared) "refused" else if (o.line != null) "a line of ${o.line.size} points" else "reason=${o.reason}"

    /** Every row lands a line both ways over [world], and every point of it stands on water. */
    private fun assertEveryRowRoutes(world: MultipassWorld) {
        for ((name, outcome) in everyRowBothWays(world)) {
            assertTrue("$name: the end routes out (${describe(outcome)})", outcome.line != null)
            assertTrue("$name: the line stays on water", outcome.line!!.all { world.isWater(it.latitude, it.longitude) })
        }
    }

    /**
     * **The reproduction.** An 80 m inlet: the end stands 40 m off either wall, clear of the repair's 25 m, so
     * it is not moved; every inlet cell past its 50 m disc stands within the 50 m margin, so on today's engines
     * every row answers `NO_PATH` both ways, on the first corridor and on the doubled retry.
     */
    @Ignore("Route todo 11: the coast-boxed end fails as no path; un-ignore when a coast-side fallback lands")
    @Test
    fun aCoastBoxedEndRoutesOutOfItsInlet() = assertEveryRowRoutes(inlet(80.0))

    /**
     * **The marina's shape.** The end stands 150 m clear in a basin of its own, so its disc touches free water,
     * and only the 80 m mouth is sealed by the margin: today every row answers `NO_PATH` both ways, so a fix that
     * reads the end's disc alone does not reach it.
     */
    @Ignore("Route todo 11: the coast-boxed end fails as no path; un-ignore when a coast-side fallback lands")
    @Test
    fun anEndInABasinBehindANarrowMouthRoutesOut() = assertEveryRowRoutes(basin(80.0))

    /**
     * **The precondition.** The same inlet and basin with a 240 m way out hold a free strip down its middle
     * past the margin, so every row routes both ways: the failures above are the margin's pocket, not the
     * fixture.
     */
    @Test
    fun aWayOutWiderThanTwiceTheMarginRoutes() {
        assertEveryRowRoutes(inlet(240.0))
        assertEveryRowRoutes(basin(240.0))
    }
}
