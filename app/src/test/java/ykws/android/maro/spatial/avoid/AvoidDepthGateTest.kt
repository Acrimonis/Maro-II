package ykws.android.maro.spatial.avoid

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI

/**
 * The 3 m depth gate as the rasterizer reads it: a known depth below the threshold paints the cell
 * land, a depth at or above it and an unsurveyed one are both ignored, and the gate is **ANDed with
 * the coastline's own water** — a cell the sweep sealed stays blocked whatever the soundings say.
 */
class AvoidDepthGateTest {

    private val box = BBox(43.50, 43.52, 7.00, 7.02)
    private val minDepthM = 3.0

    @Test
    fun aKnownDepthBelowTheThresholdPaintsTheCellLand() {
        val grid = gated { p -> if (shallow(p)) 2.0 else Double.NaN }

        assertFalse("2 m of water is below the 3 m gate", passable(grid, 43.510, 7.010))
    }

    @Test
    fun aDepthAtTheThresholdIsIgnored() {
        val grid = gated { p -> if (shallow(p)) minDepthM else Double.NaN }

        assertTrue("the gate is strict: exactly the threshold is not below it", passable(grid, 43.510, 7.010))
    }

    @Test
    fun deeperWaterIsIgnored() {
        val grid = gated { p -> if (shallow(p)) 20.0 else Double.NaN }

        assertTrue("deeper water is not gated", passable(grid, 43.510, 7.010))
    }

    @Test
    fun anUnsurveyedCellIsIgnored() {
        val grid = gated { _ -> Double.NaN }

        assertTrue("NoData is not below the threshold, it is unknown", passable(grid, 43.510, 7.010))
    }

    /**
     * The AND. The depth mask erases the soundings on the land side, so this cell answers NoData while
     * the coastline's sweep has already sealed it: it must stay blocked rather than read as unsurveyed
     * water a route may use.
     */
    @Test
    fun aNoDataCellTheCoastlineSealedStaysBlocked() {
        val coast = listOf(LatLng(43.510, 7.00), LatLng(43.510, 7.02))
        val grid = rasterize(
            box, 50.0, 25.0, emptyList(), listOf(coast), box.latNorth,
            RouteCostField(listOf(depthGateSource(minDepthM) { _ -> Double.NaN }))
        )
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0

        assertFalse(
            "the land side of the coast stays land",
            passable(grid, 43.510 + 35.0 / mPerDegLat, 7.010)
        )
        assertTrue(
            "the water side of the coast stays water",
            passable(grid, 43.510 - 35.0 / mPerDegLat, 7.010)
        )
    }

    private fun gated(depthMAt: (LatLng) -> Double): AvoidGrid =
        rasterize(
            box, 50.0, 25.0, emptyList(), emptyList(), box.latNorth,
            RouteCostField(listOf(depthGateSource(minDepthM, depthMAt)))
        )

    /** A patch of water roughly 250 m by 110 m at the corridor's middle, away from the ends. */
    private fun shallow(p: LatLng): Boolean =
        p.latitude in 43.5095..43.5105 && p.longitude in 7.009..7.011

    private fun passable(grid: AvoidGrid, lat: Double, lon: Double): Boolean {
        val cell = grid.cellOf(lat, lon)
        return grid.cell(cell.row, cell.col).passable
    }
}
