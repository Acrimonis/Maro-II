package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.cos

/**
 * **The plain loops read the cancel they are handed.** The A* reads its own job between expansions; the
 * raster and the pull are plain functions, so the engine hands them its job's `ensureActive` as a lambda.
 * These fixtures pin that the lambda is consulted — a throwing hook stops the loop where it stands, and a
 * counting hook is read at least once over a loop longer than the cadence. Nothing here times anything:
 * a loop that honours the hook is the whole claim.
 */
class RouteCancellationHooksTest {

    private val box = BBox(43.50, 43.52, 7.00, 7.02)
    private val paceKn = 12.0

    /** A soft source over the whole box, so the raster's per-cell pass — the loop the hook sits in — runs. */
    private val priced = RouteCostSource.Soft(priceSec = { _ -> 1.0 }, tag = MultipassCellState.BAND)

    @Test
    fun theRasterStopsWhereItsHookThrows() {
        try {
            rasterize(
                box, 50.0, paceKn, 25.0, emptyList(), emptyList(), box.latNorth, RouteCostField(listOf(priced)),
                checkCancelled = { throw CancellationException("cancelled mid-raster") }
            )
            fail("a raster whose hook throws must not answer a grid")
        } catch (expected: CancellationException) {
            // The per-cell pass read the hook on its first block of rows and stopped there.
        }
    }

    @Test
    fun theRasterReadsItsHookWhileItRuns() {
        var reads = 0
        rasterize(
            box, 50.0, paceKn, 25.0, emptyList(), emptyList(), box.latNorth, RouteCostField(listOf(priced)),
            checkCancelled = { reads++ }
        )
        assertTrue("the per-cell pass consults the hook at least once", reads >= 1)
    }

    @Test
    fun thePullReadsItsHookAndStopsWhereItThrows() {
        val path = openWaterPath(points = 200)
        val setup = PullSetup(
            25.0, 100.0, 100.0, RouteCostField.ofHard { Double.MAX_VALUE }, path.first(), path.last()
        )
        var reads = 0
        MultipassPull.pull(setup, path, checkCancelled = { reads++ })
        assertTrue("a walk longer than the cadence consults the hook", reads >= 1)

        try {
            MultipassPull.pull(setup, path, checkCancelled = { throw CancellationException("cancelled mid-pull") })
            fail("a pull whose hook throws must not answer a line")
        } catch (expected: CancellationException) {
            // The walk read the hook at its cadence and stopped there.
        }
    }

    /** [points] collinear points eastward along the box's middle latitude, 20 m apart, on open water. */
    private fun openWaterPath(points: Int): List<LatLng> {
        val lat = (box.latSouth + box.latNorth) / 2.0
        val mPerDegLon = SpatialOperations.EARTH_RADIUS_M * PI / 180.0 * cos(Math.toRadians(lat))
        return List(points) { i -> LatLng(lat, box.lonWest + i * 20.0 / mPerDegLon) }
    }
}
