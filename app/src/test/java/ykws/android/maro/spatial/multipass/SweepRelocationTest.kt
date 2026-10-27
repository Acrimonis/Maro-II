package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

/**
 * **A bend the sweep kept slides onto the edge that kept it.** The sweep records each kept vertex with
 * the mark of its skipping chord that kept it — the dearest for a price keep, the first refused for a land
 * keep — and the relocation moves the bend to the nearest corner that stands nearer that mark than the bend
 * does, on the snap's own two proofs. The sweep that follows then drops the neighbour the move freed.
 *
 * The frame: a priced disc at `(500, 0)` of radius 100 m the straight `a → c` crosses, a bend `m` in open
 * water the greedy left north of it, a vertex `n` held by a point wall its chord grazes, and a corner on
 * the disc's northern edge the relocation can hold.
 */
class SweepRelocationTest {

    private val a = pt(0.0, 0.0)
    private val m = pt(500.0, 300.0)
    private val n = pt(750.0, 75.0)
    private val c = pt(1000.0, 0.0)
    private val disc = pt(500.0, 0.0)
    private val wallPoint = pt(760.0, 170.0)
    private val edgeCorner = pt(500.0, 150.0)
    private val marginM = 25.0
    private val stepM = MultipassPull.clearanceStep(marginM)

    @Test
    fun aPriceKeptBendMovesToTheEdgeAndTheNextSweepDropsItsFreedNeighbour() {
        val setup = setup(pricedDiscWithWall())
        val keeps = ArrayList<KeptBend>()
        val swept = MultipassPull.sweep(setup, listOf(a, m, n, c), keeps = keeps)
        assertEquals("the first sweep keeps both bends", listOf(a, m, n, c), swept)
        assertEquals("one record per kept vertex, taken once", 2, keeps.size)
        val keptM = keeps.first { it.at == m }
        val keptN = keeps.first { it.at == n }
        assertEquals(ChordRefusal.PRICE, keptM.cause)
        assertEquals(ChordRefusal.LAND, keptN.cause)
        assertTrue("m's worst mark lies on the disc", SpatialOperations.haversine(keptM.worstAt, disc) <= 100.0 + stepM)
        assertTrue("n's worst mark is at the wall", SpatialOperations.haversine(keptN.worstAt, wallPoint) < marginM + stepM)

        val lines = mutableListOf<String>()
        val relocated = relocateKeptBends(setup, listOf(cornerSet(edgeCorner)), swept, keeps, trace = { lines += it() })
        assertEquals("m slides to the disc's edge, n has no corner nearer its wall", listOf(a, edgeCorner, n, c), relocated)
        assertTrue(lines.any { it.startsWith("RELOCATE idx=1 ") && it.contains("to=") })
        assertTrue(lines.any { it.startsWith("RELOCATE keep idx=2 ") && it.contains("why=NONE") })

        assertEquals("the second sweep drops n, now on the chord", listOf(a, edgeCorner, c), MultipassPull.sweep(setup, relocated))
    }

    @Test
    fun aLandKeptBendMovesToTheWallBodysCorner() {
        val setup = setup(wallDisc())
        val keeps = ArrayList<KeptBend>()
        val swept = MultipassPull.sweep(setup, listOf(a, m, c), keeps = keeps)
        assertEquals(listOf(a, m, c), swept)
        val kept = keeps.single()
        assertEquals(ChordRefusal.LAND, kept.cause)
        assertTrue(SpatialOperations.haversine(kept.worstAt, disc) < 100.0 + marginM + stepM)

        assertEquals(listOf(a, edgeCorner, c), relocateKeptBends(setup, listOf(cornerSet(edgeCorner)), swept, keeps))
    }

    @Test
    fun aRelocationWhoseLegCrossesLandIsRefused() {
        val setup = setup(wallDisc(extraWallAt = pt(250.0, 75.0)))
        val keeps = ArrayList<KeptBend>()
        val swept = MultipassPull.sweep(setup, listOf(a, m, c), keeps = keeps)
        assertEquals(1, keeps.size)
        assertTrue("the corner's leg from a crosses the extra wall", !MultipassPull.legClear(a, edgeCorner, setup.context()))

        assertEquals(listOf(a, m, c), relocateKeptBends(setup, listOf(cornerSet(edgeCorner)), swept, keeps))
    }

    @Test
    fun aDearerRelocationIsRefused() {
        val setup = setup(pricedDiscWithWall())
        val keeps = ArrayList<KeptBend>()
        val swept = MultipassPull.sweep(setup, listOf(a, m, c), keeps = keeps)
        val inside = pt(480.0, -60.0)
        val ctx = setup.context()
        val viaInside = MultipassPull.totalSec(a, inside, ctx) + MultipassPull.totalSec(inside, c, ctx)
        val viaM = MultipassPull.totalSec(a, m, ctx) + MultipassPull.totalSec(m, c, ctx)
        assertTrue("a corner inside the priced disc is dearer than the bend", viaInside > viaM)

        assertEquals(listOf(a, m, c), relocateKeptBends(setup, listOf(cornerSet(inside)), swept, keeps))
    }

    @Test
    fun aCornerEqualToANeighbourIsNeverTaken() {
        val setup = setup(pricedDiscWithWall())
        val keep = KeptBend(at = m, worstAt = a, cause = ChordRefusal.PRICE, arm = PriceArm.ZONE)
        assertEquals(listOf(a, m, c), relocateKeptBends(setup, listOf(cornerSet(a)), listOf(a, m, c), listOf(keep)))
    }

    @Test
    fun theSameLineRelocatesTheSameFromEitherEnd() {
        val setup = setup(pricedDiscWithWall())
        val keeps = ArrayList<KeptBend>()
        val swept = MultipassPull.sweep(setup, listOf(a, m, n, c), keeps = keeps)
        val forward = inCanonicalDirection(swept, setup) { line, water ->
            relocateKeptBends(water, listOf(cornerSet(edgeCorner)), line, keeps)
        }
        val backward = inCanonicalDirection(swept.asReversed(), setup.reversed()) { line, water ->
            relocateKeptBends(water, listOf(cornerSet(edgeCorner)), line, keeps)
        }
        assertEquals(forward, backward.asReversed())
        assertNotNull(forward)
    }

    private fun setup(field: RouteCostField): PullSetup =
        PullSetup(marginM, stepM, stepM, field, a, c, paceMps = 10.0)

    private fun cornerSet(vararg corners: LatLng): CornerSet = CornerSet(corners.toList()) { 0.0 }

    /** A soft disc of 0.5 s/m at [disc] and a point wall at [wallPoint] the chord `m → c` grazes. */
    private fun pricedDiscWithWall(): RouteCostField = RouteCostField(
        listOf(
            RouteCostSource.Hard(distanceAt = { p -> SpatialOperations.haversine(p, wallPoint) }),
            RouteCostSource.Soft(
                priceSec = { p -> if (SpatialOperations.haversine(p, disc) < 100.0) 0.5 else 0.0 },
                tag = MultipassCellState.ZONE
            )
        ),
        cellM = 1.0
    )

    /** A hard disc of radius 100 m at [disc], and optionally a second small wall at [extraWallAt]. */
    private fun wallDisc(extraWallAt: LatLng? = null): RouteCostField = RouteCostField.ofHard { p ->
        val body = max(0.0, SpatialOperations.haversine(p, disc) - 100.0)
        if (extraWallAt == null) body else minOf(body, max(0.0, SpatialOperations.haversine(p, extraWallAt) - 10.0))
    }

    private companion object {
        const val FRAME_LAT = 43.5
        const val FRAME_LON = 7.0
        val M_PER_DEG_LAT = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val M_PER_DEG_LON = M_PER_DEG_LAT * cos(Math.toRadians(FRAME_LAT))
        fun pt(eastM: Double, northM: Double) = LatLng(FRAME_LAT + northM / M_PER_DEG_LAT, FRAME_LON + eastM / M_PER_DEG_LON)
    }
}
