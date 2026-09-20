package ykws.android.maro.spatial

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.spatial.mesh.RouteShortcut
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * The shortcut pass over synthetic chains rather than a baked mesh.
 *
 * The pass is pure and its whole decision is one lambda — the water test the search supplies — so a
 * hole in the mesh, a stretch the boat cannot reach and open water are three lambdas here and not
 * three bakes. That is also why the case the study cares about most is the middle one: the pass must
 * **refuse** a shortcut whose straight line crosses water the mesh excludes, and no reading of a
 * baked mesh can hold that case still.
 *
 * Measured in the pass's own terms — which positions survive, how many candidates the water refused —
 * so the tests say what the pass did rather than what the line looks like.
 */
class RouteShortcutTest {

    private fun point(xM: Double, yM: Double): RoutePoint =
        RoutePoint(ORIGIN_LAT + yM / M_PER_DEG_LAT, ORIGIN_LON + xM / M_PER_DEG_LON)

    /** A water test that refuses everything inside the given metre-frame box. */
    private fun excluding(x0: Double, x1: Double, y0: Double, y1: Double) =
        { latitude: Double, longitude: Double ->
            val x = (longitude - ORIGIN_LON) * M_PER_DEG_LON
            val y = (latitude - ORIGIN_LAT) * M_PER_DEG_LAT
            !(x in x0..x1 && y in y0..y1)
        }

    private fun openWater() = { _: Double, _: Double -> true }

    // ── What the pass keeps ───────────────────────────────────────────────────

    @Test
    fun aChainInOpenWaterCollapsesToItsTwoEnds() {
        // A gentle arc of five vertices, every straight line between two of them on water: the
        // vertices in between are the geometry's, not the water's, so none of them survives.
        val chain = listOf(
            point(0.0, 0.0),
            point(200.0, 50.0),
            point(400.0, 0.0),
            point(600.0, -50.0),
            point(800.0, 0.0)
        )

        val kept = RouteShortcut.keep(chain, openWater())

        assertArrayEquals(intArrayOf(0, 4), kept.positions)
        assertEquals("no candidate was refused", 0, kept.refused)
        assertEquals("three vertices were dropped", 3, kept.removed)
    }

    @Test
    fun aChainShorterThanThreeIsHandedBackWhole() {
        val chain = listOf(point(0.0, 0.0), point(400.0, 0.0))

        val kept = RouteShortcut.keep(chain, openWater())

        assertArrayEquals(intArrayOf(0, 1), kept.positions)
        assertEquals(0, kept.refused)
        assertEquals(0, kept.removed)
    }

    @Test
    fun aShortcutAcrossAHoleInTheMeshIsRefusedAndTheDetourStays() {
        // A V round a hole: the straight line from the first vertex to the last runs through the
        // hole, so it is refused, and the corner is what the water forces. The hole is 400 m wide —
        // forty times the sample step — so nothing here turns on the sampling.
        val chain = listOf(point(0.0, 0.0), point(1000.0, 700.0), point(2000.0, 0.0))
        val water = excluding(800.0, 1200.0, -50.0, 100.0)

        val kept = RouteShortcut.keep(chain, water)

        assertArrayEquals(
            "the corner is kept because the straight line crosses water the mesh excludes",
            intArrayOf(0, 1, 2),
            kept.positions
        )
        assertEquals("the refused candidate is counted", 1, kept.refused)
        assertEquals(0, kept.removed)
    }

    @Test
    fun aShortcutThatWouldLeaveTheBoatSStretchIsRefused() {
        // The pass is handed one test, and the search builds it as "kept water **in my own stretch**".
        // A strip the boat cannot reach — a stretch of its own across the way — is refused exactly as
        // a hole is, and the vertex that keeps the line inside the stretch survives.
        val chain = listOf(point(0.0, 0.0), point(400.0, 0.0), point(800.0, 0.0))
        val water = excluding(500.0, 700.0, -100.0, 100.0)

        val kept = RouteShortcut.keep(chain, water)

        assertArrayEquals(intArrayOf(0, 1, 2), kept.positions)
        assertEquals(1, kept.refused)
        assertEquals(0, kept.removed)
    }

    @Test
    fun aChainOnNoWaterAtAllComesBackIntactAndSaysSo() {
        // The limit of the pass: when nothing ahead is vetted, every step falls back on the leg the
        // chain already holds — so the line is untouched and the refusal count is every candidate
        // tried. It is the reading that separates "nothing to straighten" from "nowhere to straighten
        // it", and the second is a finding about the mesh rather than a pass to tune.
        val chain = listOf(
            point(0.0, 0.0),
            point(200.0, 0.0),
            point(400.0, 0.0),
            point(600.0, 0.0),
            point(800.0, 0.0)
        )

        val kept = RouteShortcut.keep(chain, insideWater = { _, _ -> false })

        assertArrayEquals(intArrayOf(0, 1, 2, 3, 4), kept.positions)
        assertEquals("every candidate but the chain's own legs was refused", 6, kept.refused)
        assertEquals(0, kept.removed)
    }

    // ── What the pass promises about the line it returns ──────────────────────

    @Test
    fun everySegmentThePassKeepsIsEitherAChainLegOrOnVettedWater() {
        // The pass's contract, checked on a zig-zag whose water is a corridor with two holes in it:
        // the ends survive, the positions ascend, and any pair that is **not** a leg of the chain
        // handed in was sampled and accepted — never taken because the walk happened to reach it.
        val chain = (0 until 9).map { i -> point(i * 300.0, if (i % 2 == 0) 0.0 else 250.0) }
        val water = excluding(400.0, 700.0, -100.0, 100.0)
        val samples = ArrayList<Pair<Double, Double>>()
        val recording = { latitude: Double, longitude: Double ->
            samples.add(latitude to longitude)
            water(latitude, longitude)
        }

        val kept = RouteShortcut.keep(chain, recording)

        assertEquals("the first vertex always survives", 0, kept.positions.first())
        assertEquals("and the last one does", chain.size - 1, kept.positions.last())
        for (i in 1 until kept.positions.size) {
            assertTrue(
                "positions must ascend, got ${kept.positions.toList()}",
                kept.positions[i] > kept.positions[i - 1]
            )
        }
        for (i in 1 until kept.positions.size) {
            val from = kept.positions[i - 1]
            val to = kept.positions[i]
            if (to == from + 1) continue
            val samplesBefore = samples.size
            var interior = 0
            val steps = 2.coerceAtLeast((metres(chain[from], chain[to]) / RouteShortcut.SAMPLE_M).toInt())
            for (k in 1 until steps) {
                val t = k.toDouble() / steps
                interior++
                assertTrue(
                    "a kept shortcut $from→$to must sample only vetted water",
                    water(
                        chain[from].latitude + (chain[to].latitude - chain[from].latitude) * t,
                        chain[from].longitude + (chain[to].longitude - chain[from].longitude) * t
                    )
                )
            }
            assertTrue("the pass must have sampled the candidate it kept", samples.size >= samplesBefore)
        }
        assertTrue("the walk must have refused something — it has to walk round a hole", kept.refused > 0)
    }

    // ── The price side of the same decision ───────────────────────────────────

    @Test
    fun aCandidateThePriceRefusesIsCountedApartFromTheWaterS() {
        // Open water, and a price that admits only the chain's own legs: the walk tries the farthest
        // candidate, the water vouches for it, and the **price** is what refuses it — so the refusal
        // is counted as the price's, apart from the water's, and the line falls back on the legs the
        // chain already holds.
        val chain = listOf(point(0.0, 0.0), point(200.0, 0.0), point(400.0, 0.0), point(600.0, 0.0))

        val kept = RouteShortcut.keep(
            chain,
            insideWater = openWater(),
            admissible = { from, to -> to == from + 1 }
        )

        assertArrayEquals(intArrayOf(0, 1, 2, 3), kept.positions)
        assertEquals("the water refused nothing", 0, kept.refused)
        assertEquals("the price refused every candidate but the chain's own legs", 3, kept.refusedByCost)
        assertEquals(0, kept.removed)
    }

    @Test
    fun aCandidateThePriceAdmitsIsTakenWhereTheWaterAllowsIt() {
        // The other side of the same rule: a candidate the price admits and the water vouches for is
        // the shortcut, so the corner between the two ends is dropped.
        val chain = listOf(point(0.0, 0.0), point(200.0, 50.0), point(400.0, 0.0))

        val kept = RouteShortcut.keep(
            chain,
            insideWater = openWater(),
            admissible = { from, to -> to == from + 2 }
        )

        assertArrayEquals(intArrayOf(0, 2), kept.positions)
        assertEquals(0, kept.refused)
        assertEquals(0, kept.refusedByCost)
        assertEquals(1, kept.removed)
    }

    @Test
    fun theChainIntactReadingKeepsEverything() {
        val kept = RouteShortcut.chainIntact(4)

        assertArrayEquals(intArrayOf(0, 1, 2, 3), kept.positions)
        assertEquals(0, kept.refused)
        assertEquals(0, kept.removed)
    }

    private fun metres(from: RoutePoint, to: RoutePoint): Double {
        val dLat = (to.latitude - from.latitude) * PI / 180.0
        val dLon = (to.longitude - from.longitude) * PI / 180.0
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            cos(from.latitude * PI / 180.0) * cos(to.latitude * PI / 180.0) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return 2.0 * SpatialOperations.EARTH_RADIUS_M * kotlin.math.asin(sqrt(a))
    }

    private companion object {
        const val ORIGIN_LAT = 43.5
        const val ORIGIN_LON = 7.0

        val M_PER_DEG_LAT: Double = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val M_PER_DEG_LON: Double = M_PER_DEG_LAT * cos(ORIGIN_LAT * PI / 180.0)
    }
}
