package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.multipass.GateTestFixtures.at
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * **The corner pass draws no straight it has not measured** — the crossing a merged pair of bends would
 * stand on, and the chords joining a bow past a kink too flat to be a corner on either side, each read
 * against the walls the bow itself keeps — and a corner the end's waiver excused hands no bow point beyond
 * the waiver its own closeness. Every drawn straight is read back on the measured edge.
 */
class GateCornerTest {

    private val floorM = 10.0

    /**
     * Whether every straight of [line] keeps [hardM] wherever it stands more than [clearM] from both of the
     * line's ends — read on one-metre pieces.
     */
    private fun keepsWallPast(gate: GateRule, line: List<LatLng>, hardM: Double, clearM: Double): Boolean {
        val start = line.first()
        val end = line.last()
        fun near(p: LatLng) = SpatialOperations.haversine(p, start) < clearM || SpatialOperations.haversine(p, end) < clearM
        for (k in 0 until line.size - 1) {
            val pieces = ceil(SpatialOperations.haversine(line[k], line[k + 1])).toInt().coerceAtLeast(1)
            for (j in 0 until pieces) {
                val p = chordPointAt(line[k], line[k + 1], j.toDouble() / pieces)
                val q = chordPointAt(line[k], line[k + 1], (j + 1).toDouble() / pieces)
                if (!near(p) && !near(q) && gate.segmentEdgeM(p, q, hardM + 1.0) < hardM - 1e-6) return false
            }
        }
        return true
    }

    /** The largest heading change between two consecutive straights of [line], in degrees. */
    private fun sharpestTurnDeg(line: List<LatLng>): Double = (1 until line.size - 1).maxOfOrNull { k ->
        val inBearing = SpatialOperations.initialBearing(line[k - 1], line[k])
        val outBearing = SpatialOperations.initialBearing(line[k], line[k + 1])
        abs(((outBearing - inBearing + 540.0) % 360.0) - 180.0)
    } ?: 0.0

    /** The least measured distance from any straight of [line] to the edge. */
    private fun leastEdgeM(gate: GateRule, line: List<LatLng>): Double =
        (0 until line.size - 1).minOf { gate.segmentEdgeM(line[it], line[it + 1], 100.0) }

    /**
     * Two close left bends 12 m off a straight shelf: their outer legs cross 9 m off it, inside the wall,
     * so the pair is not merged and nothing the pass draws comes nearer than the line it was given.
     */
    @Test
    fun aMergeWhoseCrossingStandsInTheWallIsNotMade() {
        val raster = GateTestFixtures.raster(100, 120, 2.0) { n, _ -> if (n < 100.0) 2.0 else 10.0 }
        val gate = GateTestFixtures.gateOver(raster, hardM = floorM)
        val world = GateTestFixtures.RasterWorld(raster)
        val given = listOf(at(140.0, 0.0), at(112.0, 100.0), at(112.0, 120.0), at(140.0, 220.0))
        val crossing = at(109.2, 110.0)
        assertTrue(
            "the fixture's crossing stands inside the wall",
            gate.edgeM(crossing, 100.0) < floorM
        )
        assertTrue("while the given line keeps it", leastEdgeM(gate, given) >= floorM)

        val rounded = RouteCornerPass.round(given, 25.0, world, gate, 50.0)

        assertEquals("both bends are read on their own", 2, rounded.verdicts.size)
        assertFalse("the crossing is never drawn", rounded.points.any { hypot(it.latitude - crossing.latitude, it.longitude - crossing.longitude) < 1e-9 })
        assertTrue(
            "no drawn straight comes nearer the edge than the given line",
            leastEdgeM(gate, rounded.points) >= leastEdgeM(gate, given) - 1e-6
        )
    }

    /**
     * A bow whose room runs past a 4° kink: the drawing tail takes the kink back and joins the line's start
     * to the bow's first point, a chord that cuts toward a shoal the given line clears by its preferred
     * standoff. The bow shrinks until that chord keeps the standoff too.
     */
    @Test
    fun theChordAPassedKinkLeavesKeepsTheBowsFloor() {
        val rock = at(173.5, 140.0)
        val raster = GateTestFixtures.raster(230, 180, 2.0) { n, e -> if (hypot(n - 173.5, e - 140.0) < 5.0) 2.0 else 10.0 }
        val gate = GateTestFixtures.gateOver(raster, hardM = floorM)
        val world = GateTestFixtures.RasterWorld(raster)
        val start = at(200.0, 20.0)
        val kink = at(200.0, 240.0)
        val corner = at(202.1, 270.0)
        val given = listOf(start, kink, corner, at(400.0, 320.0))
        assertTrue("the given line keeps the preferred standoff", leastEdgeM(gate, given) >= gate.preferredM)
        // The trap is real: a chord from the start to a point 60 m behind the kink, on the leg it bends onto,
        // cuts inside the standoff.
        val behindKink = at(200.0 - 60.0 * 2.1 / 30.0, 240.0 - 60.0)
        assertTrue(
            "the chord to a bow starting behind the kink would cut the standoff " +
                "(${gate.segmentEdgeM(start, behindKink, 100.0)} m)",
            gate.segmentEdgeM(start, behindKink, 100.0) < gate.preferredM
        )
        assertTrue("the rock stands south of the line", rock.latitude < start.latitude)

        val rounded = RouteCornerPass.round(given, 12.0, world, gate, 50.0)

        assertEquals(
            "the corner is still rounded",
            RouteCornerPass.CornerOutcome.ROUNDED, rounded.verdicts.last().outcome
        )
        assertTrue(
            "every drawn straight keeps the preferred standoff (${leastEdgeM(gate, rounded.points)} m)",
            leastEdgeM(gate, rounded.points) >= gate.preferredM - 1e-6
        )
    }
    /**
     * A corner 19 m from the start, a few metres off a rock — inside the end's waiver, so its own floor is
     * that — turns into a leg that runs just past the wall off a shelf on the turn's inside. A bow at the
     * corner's floor would cut that shelf under the wall past the waiver; held to the wall there, it keeps it.
     * The deep water reads 3.5 m so the 3 m line stands two thirds of a node out from the shelf's last node.
     */
    @Test
    fun aWaivedCornersBowKeepsTheWallPastTheWaiver() {
        val raster = GateTestFixtures.raster(130, 25, 2.0) { n, e ->
            if (hypot(n - 14.0, e - 27.0) < 2.5 || (e < 12.0 && n > 24.0)) 2.0 else 3.5
        }
        val gate = GateTestFixtures.gateOver(raster, hardM = floorM)
        val world = GateTestFixtures.RasterWorld(raster)
        val start = at(20.0, 5.0)
        val corner = at(20.0, 24.0)
        val given = listOf(start, corner, at(220.0, 24.0))
        assertTrue("the corner stands inside the start's waiver", gate.waives(corner, start, given.last()))
        assertTrue("and under the wall", gate.edgeM(corner, 100.0) < floorM)
        assertTrue("while the given line keeps the wall past it", keepsWallPast(gate, given, floorM, 20.5))
        assertTrue("by under a metre", !keepsWallPast(gate, given, floorM + 1.0, 20.5))

        val rounded = RouteCornerPass.round(given, 12.0, world, gate, 50.0)

        assertEquals(RouteCornerPass.CornerOutcome.ROUNDED, rounded.verdicts.single().outcome)
        assertTrue("no bow point past the waiver comes under the wall", keepsWallPast(gate, rounded.points, floorM, 20.5))
    }

    /**
     * The lead-chord fixture run the other way: the bow at the corner passes the kink **after** it. The kink
     * is not drawn again behind the bow, so the line never runs back along the bow's overrun, and the chord
     * from the bow's last point to the line's end keeps the standoff the given line keeps.
     */
    @Test
    fun aKinkTheBowPassesAheadIsNotDrawnBehindIt() {
        val raster = GateTestFixtures.raster(230, 180, 2.0) { n, e -> if (hypot(n - 173.5, e - 140.0) < 5.0) 2.0 else 10.0 }
        val gate = GateTestFixtures.gateOver(raster, hardM = floorM)
        val world = GateTestFixtures.RasterWorld(raster)
        val given = listOf(at(400.0, 320.0), at(202.1, 270.0), at(200.0, 240.0), at(200.0, 20.0))
        assertTrue("the given line keeps the preferred standoff", leastEdgeM(gate, given) >= gate.preferredM)

        val rounded = RouteCornerPass.round(given, 12.0, world, gate, 50.0)

        assertEquals(RouteCornerPass.CornerOutcome.ROUNDED, rounded.verdicts.first().outcome)
        assertTrue(
            "the drawn line never turns back on itself (${sharpestTurnDeg(rounded.points)}°)",
            sharpestTurnDeg(rounded.points) < 90.0
        )
        assertTrue(
            "every drawn straight keeps the preferred standoff (${leastEdgeM(gate, rounded.points)} m)",
            leastEdgeM(gate, rounded.points) >= gate.preferredM - 1e-6
        )
    }
}
