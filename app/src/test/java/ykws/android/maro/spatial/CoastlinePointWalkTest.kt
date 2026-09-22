package ykws.android.maro.spatial

import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import ykws.android.maro.BuildConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.route.PrebakedInputs

/**
 * **The point walk's answers, pinned against an oracle that knows nothing about the walk** (walk item 12).
 *
 * The walk was changed to visit each grid cell once per query instead of re-collecting the whole square at
 * every ring, so the guard has to be a *reading of the answer* rather than a reading of the mechanism:
 * every sampled point's nearest-coast distance is compared with a **brute-force minimum over every
 * coastline segment of the region**, computed here and sharing only
 * [`SpatialOperations.pointToSegmentDistance`](app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt)
 * with the index. Both sides call the same function on the same winning segment, so the comparison is
 * exact: a cell the walk failed to visit, a ring it clipped differently or a candidate it skipped shows up
 * as a different distance and a red test — which is exactly the revert this change could ever hide.
 *
 * The water answer is pinned the same way, on the branches the rule actually has: the **mainland side
 * test** where the closest point is interior to its segment — points whose closest point coincides with a
 * vertex are counted and left out on purpose, that being the corner rule's own case and asserting it here
 * would mean writing a second copy of the rule — and the **real-island override**, counted where a point
 * inside an island ring reads as land. The counts are printed, so the reading says which branches were
 * exercised rather than implying all of them were.
 *
 * Gated by `-Dmaro.prebake=true` and skipping when the baked world is absent, exactly like the route
 * harness, so `gradlew test` never depends on a bake.
 */
class CoastlinePointWalkTest {

    /** A coastline edge as the oracle sees it: its two ends and whether it belongs to the mainland. */
    private class Edge(val a: LatLng, val b: LatLng, val mainland: Boolean)

    @Test
    fun theWalkAnswersTheBruteForceNearestAndTheRule() {
        Assume.assumeTrue(
            "set -Dmaro.prebake=true to run the point-walk guard",
            System.getProperty("maro.prebake") == "true"
        )
        val region = BuildConfig.REGION_ID
        val repoDir = System.getProperty("maro.repoDir")?.let { File(it) } ?: File("..")
        val missing = PrebakedInputs.paths(repoDir, region).filterNot { it.exists() }
        Assume.assumeTrue(
            "guard skipped — no baked world to read: ${missing.joinToString { it.path }}",
            missing.isEmpty()
        )

        val inputs = PrebakedInputs.load(repoDir, region)
        val index = CoastlineSpatialIndex(inputs.coast.allSegments)

        // Every segment the index holds, flattened for the oracle — the honest brute force.
        //
        // **The index's own cleaned set, read from the index rather than re-derived here**: a degenerate
        // ring and a sub-threshold detached scrap are dropped at construction (the 13 m sliver its own
        // note names), so an oracle over the raw file would demand a distance to a segment the walk is
        // *right* not to answer about — which is how this guard found that difference on its first run.
        // Reading `usableSegments` keeps the filter in its one home and makes the oracle ask exactly the
        // question the walk answers.
        val mainlandSegment = inputs.coast.mainland
        val edges = ArrayList<Edge>()
        for (polyline in index.usableSegments) {
            for (i in 0 until polyline.points.size - 1) {
                edges.add(
                    Edge(
                        LatLng(polyline.points[i].lat.toDouble(), polyline.points[i].lon.toDouble()),
                        LatLng(
                            polyline.points[i + 1].lat.toDouble(),
                            polyline.points[i + 1].lon.toDouble()
                        ),
                        mainland = polyline === mainlandSegment
                    )
                )
            }
        }
        assertTrue("the region has coastline to walk", edges.size > 100)

        val probes = ArrayList<LatLng>()
        var seed = 20260921L
        fun nextUnit(): Double {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((seed ushr 11).toDouble() / (1L shl 53).toDouble())
        }
        repeat(RANDOM_PROBES) {
            probes.add(
                LatLng(
                    PROBE_BOX_S + (PROBE_BOX_N - PROBE_BOX_S) * nextUnit(),
                    PROBE_BOX_W + (PROBE_BOX_E - PROBE_BOX_W) * nextUnit()
                )
            )
        }
        // Along the coast and on its vertices: where the nearest point is a point of a segment, and where
        // it is a shared vertex — the two shapes the side test and the corner rule divide between them.
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        repeat(OFFSET_PROBES) {
            val edge = edges[(nextUnit() * edges.size).toInt().coerceIn(0, edges.size - 1)]
            val t = nextUnit()
            val lat = edge.a.latitude + (edge.b.latitude - edge.a.latitude) * t
            val lon = edge.a.longitude + (edge.b.longitude - edge.a.longitude) * t
            val metres = if (nextUnit() < 0.5) -OFFSET_M else OFFSET_M
            val mLonDeg = mPerDegLat * cos(Math.toRadians(abs(lat)))
            probes.add(LatLng(lat + metres / mPerDegLat, lon))
            probes.add(LatLng(lat, lon + metres / mLonDeg))
        }
        for (edge in edges) {
            if (probes.size >= RANDOM_PROBES + OFFSET_PROBES * 2 + VERTEX_PROBES) break
            probes.add(edge.a)
            probes.add(edge.b)
        }

        var islandLand = 0
        var mainlandSide = 0
        var closestOnAVertex = 0
        var worstDistanceGapM = 0.0
        var mismatchedWater = 0

        for (probe in probes) {
            var best = Double.MAX_VALUE
            var winner: Edge? = null
            for (edge in edges) {
                val d = SpatialOperations.pointToSegmentDistance(probe, edge.a, edge.b)
                if (d < best) {
                    best = d
                    winner = edge
                }
            }
            val answer = index.query(probe.latitude, probe.longitude)
            val gap = abs(answer.distanceMeters - best)
            if (gap > worstDistanceGapM) worstDistanceGapM = gap
            assertEquals(
                "the walk's nearest distance is the brute force's, at " +
                    "[${probe.latitude}, ${probe.longitude}]",
                best,
                answer.distanceMeters,
                1e-9
            )

            val edge = winner ?: continue
            val onVertex = abs(answer.closestPoint.latitude - edge.a.latitude) < VERTEX_EPS_DEG &&
                abs(answer.closestPoint.longitude - edge.a.longitude) < VERTEX_EPS_DEG ||
                (abs(answer.closestPoint.latitude - edge.b.latitude) < VERTEX_EPS_DEG &&
                    abs(answer.closestPoint.longitude - edge.b.longitude) < VERTEX_EPS_DEG)
            if (onVertex) {
                closestOnAVertex++
                continue
            }

            val water = index.isWater(probe.latitude, probe.longitude)
            if (edge.mainland) {
                // The mainland's own side test: water lies to the right of the direction of travel.
                val expected = SpatialOperations.signedSide(probe, edge.a, edge.b) < 0.0
                if (water != expected) mismatchedWater++
                mainlandSide++
            } else if (!water) {
                // A point whose nearest coast is an island's ring and which reads as land is the
                // override firing — the branch the point question's own answer is built on.
                islandLand++
            }
        }

        println(
            "point walk [$region]: ${probes.size} point(s) · ${edges.size} segment(s) · worst distance " +
                "gap ${"%.1e".format(worstDistanceGapM)} m · mainland side $mainlandSide · island " +
                "reads as land $islandLand · closest on a vertex $closestOnAVertex · water mismatches " +
                "$mismatchedWater"
        )
        assertEquals("the side test answers what the walk finds", 0, mismatchedWater)
        assertTrue("the island override is exercised", islandLand > 0)
    }

    private companion object {
        const val RANDOM_PROBES = 600
        const val OFFSET_PROBES = 300
        const val VERTEX_PROBES = 400
        const val OFFSET_M = 30.0
        const val VERTEX_EPS_DEG = 1e-6
        const val PROBE_BOX_S = 43.5100
        const val PROBE_BOX_N = 43.6100
        const val PROBE_BOX_W = 7.0700
        const val PROBE_BOX_E = 7.1900
    }
}
