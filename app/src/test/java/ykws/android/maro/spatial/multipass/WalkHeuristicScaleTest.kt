package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.cos

/**
 * **The heuristic's admissibility factor** — a same-layer step is charged the lattice's nominal cell, sized
 * at one reference latitude, while the heuristic reads the true great-circle distance; nearer the equator
 * than that reference the true east–west spacing exceeds the nominal, so the heuristic is scaled down by
 * exactly that ratio and never up.
 */
class WalkHeuristicScaleTest {

    private val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0

    /** A grid whose reference is its own south edge stands on the far side of it: the scale reads one. */
    @Test
    fun aGridOnTheFarSideOfItsReferenceReadsOne() {
        val grid = MultipassGrid(
            GateTestFixtures.LAT0, GateTestFixtures.LON0,
            100.0 / GateTestFixtures.M_PER_DEG_LAT, 100.0 / GateTestFixtures.M_PER_DEG_LON,
            20, 20, 100.0, baseCostSec(100.0, 12.0)
        )
        assertEquals(1.0, WalkWindows.of(grid).heuristicScale, 1e-12)
    }

    /**
     * A grid spanning 59–60°N whose cell was sized at 60°N: at its south edge an east step covers
     * `cos 59° / cos 60°` of the nominal cell, so the scale is that ratio's inverse, and the scaled
     * heuristic for that step never exceeds the step's nominal charge.
     */
    @Test
    fun aGridTowardsTheEquatorFromItsReferenceScalesTheHeuristicDown() {
        val cellM = 100.0
        val cosRef = cos(Math.toRadians(60.0))
        val rows = (1.0 / (cellM / mPerDegLat)).toInt()
        val grid = MultipassGrid(
            59.0, 10.0, cellM / mPerDegLat, cellM / (mPerDegLat * cosRef), rows, 10, cellM, baseCostSec(cellM, 12.0)
        )
        val walk = WalkWindows.of(grid)
        val expected = cosRef / cos(Math.toRadians(59.0))
        assertEquals("the scale is cos φ_ref / cos φ_nearest", expected, walk.heuristicScale, 1e-6)
        assertTrue("and it is under one", walk.heuristicScale < 1.0)
        val a = grid.center(0, 0)
        val b = grid.center(0, 1)
        val trueM = SpatialOperations.haversine(a, b)
        assertTrue("an east step at the south edge is longer than the nominal cell ($trueM m)", trueM > cellM)
        assertTrue(
            "and scaled it is no longer than the nominal cell the step is charged",
            trueM * walk.heuristicScale <= cellM + 1e-3
        )
    }
}
