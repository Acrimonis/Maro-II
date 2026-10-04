package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * **The pull's sampled clearance** — the coarse marks and the read they spare, and the three cases
 * where the read must still happen: a wall the coarse marks cannot clear, a shallow patch narrower than
 * one coarse interval, and a wall standing just inside the margin at a division boundary, where the
 * bound is at its tightest.
 *
 * The walk's verdicts are the fine walk's own — the 1-Lipschitz proof makes that exact rather than
 * approximate — so every fixture here pins the **equality** and the **saving**: the same line, read
 * from fewer coastline queries.
 */
class AvoidPullSamplingTest {

    /** `route.avoid.obstacle.marginM` as it ships. */
    private val marginM = 25.0

    /** The coarse step these walks are handed — `avoid`'s own cell, eight times the fine step. */
    private val coarseStepM = 100.0

    /**
     * **The saving, and the proof that it is not a reroute.** Two walks over the same open water — one
     * at the fine step, one at the coarse — and the coarse one pays strictly fewer coastline reads while
     * returning the very same line.
     */
    @Test
    fun theCoarseMarksSpareTheFineReadsAndMoveNothing() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val mid = LatLng(CHORD_LAT, east(800.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        var denseReads = 0
        var coarseReads = 0
        val dense = RouteCostField.ofHard { denseReads++; Double.MAX_VALUE }
        val coarse = RouteCostField.ofHard { coarseReads++; Double.MAX_VALUE }

        val denseLine = MultipassPull.pull(
            listOf(start, mid, aim), start, aim, marginM, MultipassPull.clearanceStep(marginM), dense
        )
        val coarseLine = MultipassPull.pull(
            listOf(start, mid, aim), start, aim, marginM, coarseStepM, coarse
        )

        assertEquals("the coarse walk returns the fine walk's own line", denseLine, coarseLine)
        assertTrue("and it reads the coastline strictly less", coarseReads < denseReads)
    }

    /**
     * A wall 20 m off the path's middle is inside the margin at the fine mark that stands on it, and
     * 50 m from the nearest coarse mark — whose own read stays under the trigger, so the half-step is
     * not proved and the fine marks pay the read exactly as they do today.
     */
    @Test
    fun aChordTheCoarseMarksCannotClearIsStillRefused() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val mid = LatLng(CHORD_LAT, east(800.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        val path = listOf(start, mid, aim)
        val wall = LatLng(south(20.0), east(800.0))
        val field = RouteCostField.ofHard { p -> SpatialOperations.haversine(p, wall) }

        val pulled = MultipassPull.pull(path, start, aim, marginM, coarseStepM, field)

        assertEquals("the grazed chord is refused, as the fine walk refuses it", path, pulled)
    }

    /**
     * **The depth-gate regression.** The coast is far everywhere, so every coarse mark proves its own
     * half-step clear and the distance read is skipped — while the gate is a **step**, tested at every
     * mark, and its patch is narrower than one coarse interval. A walk that coarsened the gate with the
     * clearance would sail this chord; this one does not.
     */
    @Test
    fun aShallowPatchInsideOneCoarseIntervalIsStillRefused() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val mid = LatLng(CHORD_LAT, east(800.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        val path = listOf(start, mid, aim)
        val field = RouteCostField(
            listOf(
                RouteCostSource.Hard(distanceAt = { _ -> Double.MAX_VALUE }),
                depthGateSource(3.0) { p -> if (shallowPatch(p)) 2.0 else Double.NaN }
            )
        )

        val pulled = MultipassPull.pull(path, start, aim, marginM, coarseStepM, field)

        assertEquals("a shallow patch the coarse marks step over is still refused", path, pulled)
    }

    /**
     * **The bound's own tight place.** The wall stands at the first division's boundary, 24 m off the
     * chord — the point furthest from the two coarse marks that cover it, and `marginM - 1` away from
     * it. Neither mark may prove the half-step clear, so the fine marks pay the read and the chord is
     * refused rather than sailed over.
     */
    @Test
    fun aWallJustInsideTheMarginAtADivisionBoundaryIsStillRefused() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val mid = LatLng(CHORD_LAT, east(800.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        val path = listOf(start, mid, aim)
        val wall = LatLng(south(24.0), east(100.0))
        val field = RouteCostField.ofHard { p -> SpatialOperations.haversine(p, wall) }

        val pulled = MultipassPull.pull(path, start, aim, marginM, coarseStepM, field)

        assertEquals("a wall just inside the margin is never proved clear", path, pulled)
    }

    /** A patch of 2 m water, 30 m across, centred on the path's middle — one coarse interval's width. */
    private fun shallowPatch(p: LatLng): Boolean =
        abs(p.latitude - CHORD_LAT) <= 15.0 / M_PER_DEG_LAT &&
            abs(p.longitude - east(800.0)) <= 15.0 / M_PER_DEG_LON

    private companion object {

        /** The chord's own parallel, so every fixture offsets in metres without a projection. */
        private const val CHORD_LAT = 43.50

        private val M_PER_DEG_LAT = SpatialOperations.EARTH_RADIUS_M * PI / 180.0

        private val M_PER_DEG_LON = M_PER_DEG_LAT * cos(Math.toRadians(CHORD_LAT))

        /** A longitude [m] metres east of the chord's origin. */
        private fun east(m: Double): Double = 7.00 + m / M_PER_DEG_LON

        /** A latitude [m] metres south of the chord's parallel. */
        private fun south(m: Double): Double = CHORD_LAT - m / M_PER_DEG_LAT
    }
}
