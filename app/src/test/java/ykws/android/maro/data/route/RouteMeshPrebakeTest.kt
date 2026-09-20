package ykws.android.maro.data.route

import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import ykws.android.maro.BuildConfig
import ykws.android.maro.data.coastline.CoastlineSerializer
import ykws.android.maro.data.depth.DepthSerializer
import ykws.android.maro.data.depth.DepthZoneMask
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.CoastlineData
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.RouteMesh
import ykws.android.maro.data.model.RouteMeshArrays
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.regulation.RegulatedZoneSet
import ykws.android.maro.data.regulation.RegulatedZoneSerializer
import ykws.android.maro.data.regulation.SpeedZoneBuilder
import ykws.android.maro.spatial.CoastlineSpatialIndex
import ykws.android.maro.spatial.RoutePointQueries
import ykws.android.maro.spatial.SpeedZoneIndex
import ykws.android.maro.spatial.mesh.RouteSearch
import ykws.android.maro.spatial.mesh.meshDetailsOrEmpty
import java.io.File

/**
 * **Build-time prebake — NOT a unit test.** Builds the navigation mesh for the configured region,
 * writes it to the gitignored `data/app-assets/route/<region>.bin`, and measures the search that
 * reads it. Gated by `-Dmaro.prebake=true`, so it is **skipped in normal runs**. Run it through
 * `tools\bake-route.bat`, and re-run it after any coastline, depth or zone rebake — a mesh carries
 * nothing that would detect a stale input.
 *
 * **It refuses by name rather than writing a partial mesh.** The gate skips; the inputs do not. Once
 * a bake has been asked for, a missing coastline, depth or zone `.bin` is a failure that names the
 * file, because a mesh built from half its ingredients is a mesh that will route a boat onto a rock.
 *
 * **The node budget is measured, never asserted.** The node count, the edge count, the number of
 * connected stretches and the timing of a long A* over the result are printed; only the epic's
 * ≤ 500 ms search budget is asserted, and the budget is a property of the search, not of the mesh.
 */
class RouteMeshPrebakeTest {

    @Test
    fun prebakeRouteMeshAndMeasureTheSearch() {
        Assume.assumeTrue(
            "set -Dmaro.prebake=true to run the route bake",
            System.getProperty("maro.prebake") == "true"
        )

        val region = BuildConfig.REGION_ID
        val repoDir = System.getProperty("maro.repoDir")?.let { File(it) } ?: File("..")
        val coastBin = require(File(repoDir, "data/app-assets/coastlines/$region.bin"), "coastline")
        val depthBin = require(File(repoDir, "data/app-assets/depth/$region.bin"), "depth grid")
        val zoneBin = require(File(repoDir, "data/app-assets/regulated-zones/$region.bin"), "regulated zones")

        val coast = CoastlineSerializer.deserialize(coastBin.readBytes())
        val zones = RegulatedZoneSerializer.deserialize(zoneBin.readBytes())
        val depth = DepthSerializer.deserialize(depthBin.readBytes())
        val geometry = PrebakedRouteGeometry(coast, zones, depth)

        val builder = RouteMeshBuilder(region, geometry)
        val beganMs = System.currentTimeMillis()
        val mesh = builder.build()
        val buildMs = System.currentTimeMillis() - beganMs

        val out = File(repoDir, "data/app-assets/route/$region.bin")
        out.parentFile?.mkdirs()
        out.writeBytes(RouteMeshSerializer.serialize(mesh))

        val arrays = RouteMeshSerializer.deserialize(out.readBytes())
        println(
            "route bake: ${mesh.points.size} node(s), ${mesh.edges.size} edge(s), " +
                "${mesh.triangles.size} triangle(s), ${mesh.components.distinct().size} stretch(es), " +
                "built in ${buildMs} ms, ${out.length()} bytes -> ${out.path}"
        )
        printBerthReport(builder.berthReport, mesh, out.length())
        assertTrue("the baked mesh must hold nodes", arrays.nodeCount > 0)
        assertTrue("the baked mesh must hold edges", arrays.edgeCount > 0)
        // The triangles are what makes an inside-the-mesh test exact; a bake that dropped them
        // would silently leave every smoothed turn unverifiable, so the count is asserted here.
        assertTrue("the baked mesh must carry its triangles", arrays.triangleCount > 0)

        measureSearch(arrays, geometry, zones, region)
    }

    /**
     * **The hybrid berth's own reading, printed by the bake that made the decision.**
     *
     * The rule is per zone — hard where cutting its 25 m strip severs nothing, priced everywhere else —
     * so the verdict, its reason and the water it cost are printed for every zone, beside the totals
     * the decision is judged on: the water the hard cuts removed against the water the mesh carries,
     * the stretch count either side, the node / edge / triangle deltas the cuts moved and the file's
     * own size. The tripwire is printed last and by name: past one percent of the mesh's water the
     * hard cuts have become the hard-everywhere berth the user declined, and that is a finding for the
     * report rather than a number to pass quietly.
     */
    private fun printBerthReport(
        report: RouteMeshBuilder.BerthReport,
        mesh: RouteMesh,
        bytes: Long
    ) {
        println(
            "route bake berth: ${report.hardZones} hard / ${report.pricedZones} priced of " +
                "${report.zones.size} zone(s) at ${"%.0f".format(report.berthM)} m · " +
                "${report.cutZones} cut(s) found water · removed " +
                "${"%.4f".format(report.removedM2 / 1_000_000.0)} km2 of " +
                "${"%.4f".format(report.waterBeforeM2 / 1_000_000.0)} km2 " +
                "(${"%.4f".format(100.0 * report.removedShare)}%) · tripwire " +
                "${"%.0f".format(100.0 * RouteMeshBuilder.BerthReport.TRIPWIRE_SHARE)}% " +
                (if (report.tripwireFired) "FIRED" else "clear")
        )
        println(
            "route bake berth mesh: ${report.before.nodes} → ${mesh.points.size} node(s), " +
                "${report.before.edges} → ${mesh.edges.size} edge(s), " +
                "${report.before.triangles} → ${mesh.triangles.size} triangle(s), " +
                "${report.stretchesBefore} → ${report.stretchesAfter} stretch(es), $bytes bytes"
        )
        for (verdict in report.zones) {
            println(
                "route bake berth zone ${verdict.zone}: " +
                    (if (verdict.hard) "HARD" else "PRICED") +
                    " · strip ${"%.0f".format(verdict.stripAreaM2)} m2 holding " +
                    "${"%.0f".format(verdict.stripWaterM2)} m2 of kept water — ${verdict.reason}"
            )
        }
    }

    /**
     * The epic's ≤ 500 ms target, taken on the mesh that was just baked rather than on a synthetic
     * one, so the number is the number the app will see. The route runs between the two most distant
     * nodes of the largest stretch — as close to a worst case as the corridor offers.
     */
    private fun measureSearch(
        arrays: RouteMeshArrays,
        geometry: RouteGeometry,
        zoneSet: RegulatedZoneSet,
        region: String
    ) {
        val (start, destination) = farthestPair(arrays)
        val search = RouteSearch(arrays, PrebakedQueries(geometry, zoneSet))

        search.search(start, destination, CRUISE_SPEED_KN)
        val beganNs = System.nanoTime()
        val result = search.search(start, destination, CRUISE_SPEED_KN)
        val elapsedMs = (System.nanoTime() - beganNs) / 1_000_000.0

        assertTrue("expected a route across the baked mesh, got $result", result is RouteResult.Success)
        val route = result as RouteResult.Success
        println(
            "route search [$region]: " +
                // The empty-dossier reading, whose one home is `meshDetailsOrEmpty` — a print wants
                // zeros rather than a failure where an engine answered no dossier.
                "${route.meshDetailsOrEmpty.nodeIndices.size} node(s), " +
                "${"%.1f".format(route.distanceM / 1000.0)} km, " +
                "${"%.1f".format(route.durationSec / 60.0)} min, " +
                "A* took ${"%.1f".format(elapsedMs)} ms on ${arrays.nodeCount} node(s) / " +
                "${arrays.edgeCount} edge(s)"
        )
        assertTrue("A* took ${elapsedMs}ms, over the ${SEARCH_BUDGET_MS}ms budget", elapsedMs < SEARCH_BUDGET_MS)
    }

    /** The two most distant nodes of the largest stretch, so the search cannot be a short hop. */
    private fun farthestPair(arrays: RouteMeshArrays): Pair<RoutePoint, RoutePoint> {
        val ofLargest = arrays.nodeComponent.indices
            .groupBy { arrays.nodeComponent[it] }
            .maxBy { it.value.size }
            .value
        val south = ofLargest.minBy { arrays.pointsLat[it] }
        val north = ofLargest.maxBy { arrays.pointsLat[it] }
        return arrays.point(south) to arrays.point(north)
    }

    /**
     * A missing input is a refusal, not a skip: the message names the file and the bake that produces
     * it, because a route bake always follows the coastline, depth and zone bakes it reads.
     */
    private fun require(file: File, what: String): File {
        if (!file.exists()) {
            throw AssertionError(
                "route bake refused: the $what is missing — ${file.path}. " +
                    "Bake it first (apk-bake.bat coastline / depth / regulatedzones), then re-run " +
                    "tools\\bake-route.bat; a partial mesh is never written."
            )
        }
        return file
    }

    private companion object {
        /** The pace the epic's budget is measured at. */
        const val CRUISE_SPEED_KN = 28.0

        /** The epic's ≤ 500 ms worst-case target for one search. */
        const val SEARCH_BUDGET_MS = 500.0

        /** The *bande des 300 m* limit, as the band layer owns it. */
        const val COASTAL_BAND_SPEED_LIMIT_KN = 5.0
    }
}
