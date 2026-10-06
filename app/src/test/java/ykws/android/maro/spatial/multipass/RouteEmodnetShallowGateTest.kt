package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.MutableDepthGrid
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.settings.AppSettings

/**
 * **The routing's shallow wall, read through the same gate the chart passes.** [costField] builds the
 * depth gate's hard source out of `world.depthAt`, and the pull refuses a chord on a mark that source
 * calls [RouteCostField.hardBlocked] — so the water the world answers *is* the water the router
 * walls.
 *
 * The world below mirrors the live adapter: it applies [DepthSample.gatedForEmodnetShallow] at the
 * chart's own `emodnetShallowCutoffM`, exactly as
 * [`LiveMultipassWorld`](../../../../main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt)
 * now reads it — one rule, one value, chart and router alike. Two cells, same 1.5 m sounding, below
 * the 3 m depth gate either way:
 *
 * - a **coarse EMODnet** one, gated to no-data by the chart's 2 m cutoff, is no longer a wall;
 * - a **fine Litto3D** one, which the EMODnet gate never touches, still is.
 */
class RouteEmodnetShallowGateTest {

    private val box = BBox(43.50, 43.52, 7.00, 7.02)

    /** A point at the corridor's middle, over whichever grid the case built. */
    private val inside = LatLng(43.510, 7.010)

    /** The cell size the field prices at; the gate itself is geometry and reads no pace or cell. */
    private val cellM = 50.0
    private val paceKn = 28.0

    /** The chart's own cutoff — the setting's shipped default, never a constant written here. */
    private val cutoffM: Float = AppSettings().emodnetShallowCutoffM

    @Test
    fun aShallowEmodnetCellNoLongerRefusesAChord() {
        val field = fieldOver(gridOf(DepthSource.EMODNET, 1.5f))

        assertFalse(
            "the chart gates a 1.5 m EMODnet cell to no-data, so the router reads no wall there",
            field.hardBlocked(inside)
        )
    }

    @Test
    fun aShallowFineSourceCellStillRefusesAChord() {
        val field = fieldOver(gridOf(DepthSource.LITTO3D, 1.5f))

        assertTrue(
            "the EMODnet gate never touches a fine source: 1.5 m of Litto3D stays below the 3 m gate",
            field.hardBlocked(inside)
        )
    }

    /** The production field over [depths] through the live world's own gated read. */
    private fun fieldOver(depths: DepthGrid): RouteCostField = costField(
        GatedWorld(depths, cutoffM), cellM, paceKn,
        withZones = false, withBand = false, zones = emptyList(), lambda = 0.0
    )

    /** A uniform grid of one [source] at [depthM] over [box], so every sample is that cell. */
    private fun gridOf(source: DepthSource, depthM: Float): DepthGrid {
        val m = MutableDepthGrid.empty(
            regionId = "gate-test",
            bbox = BoundingBox(box.latSouth, box.latNorth, box.lonWest, box.lonEast),
            gridResM = cellM
        )
        for (r in 0 until m.rows) for (c in 0 until m.cols) {
            m.set(r, c, depthM, source, source.seedConfidence)
        }
        return m.toImmutable(validation = null, fetchTimestampMs = 0L, sourceLabel = "gate-test")
    }

    /**
     * The live world's own depth read, in miniature: water everywhere, no band, and [depthAt]
     * returned through [DepthSample.gatedForEmodnetShallow] at the chart's cutoff — the one line
     * [`LiveMultipassWorld`](../../../../main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt)
     * adds over the raw layer read.
     */
    private class GatedWorld(
        private val depths: DepthGrid,
        private val emodnetShallowCutoffM: Float
    ) : MultipassWorld {

        override val coastlineReady: Boolean get() = true

        override val depthReady: Boolean get() = true

        override val bandWidthM: Double get() = 0.0

        override val regionBounds: BBox
            get() = BBox(43.50, 43.52, 7.00, 7.02)

        override fun segmentsIn(box: BBox): List<MultipassEdge> = emptyList()

        override fun openCoastIn(box: BBox): List<List<LatLng>> = emptyList()

        override fun isWater(latitude: Double, longitude: Double): Boolean = true

        override fun distanceToCoastM(latitude: Double, longitude: Double): Double =
            Double.MAX_VALUE

        override fun depthAt(latitude: Double, longitude: Double): DepthSample =
            depths.depthAt(latitude, longitude).gatedForEmodnetShallow(emodnetShallowCutoffM)
    }
}
