package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.cos

/**
 * **The snap meets a marker body** — a bend inside a marker's collar is offered the body's own corner and
 * takes it exactly where the snap's total-cost proof says standing there pays; a bend beyond the collar is
 * not offered it; a wall body's corner-to-corner leg clears at the walk's own mark step.
 */
class MarkerBodySnapTest {

    private val start = pt(0.0, 0.0)
    private val aim = pt(1000.0, 0.0)
    private val centre = pt(500.0, 0.0)
    private val marginM = 25.0
    private val cellM = 20.0

    @Test
    fun aBendInsideTheCollarIsOfferedTheBodyCornerAndTakesItWhereItPays() {
        val marker = RouteMarker("m", RouteMarkerGeometry.Circle(centre, 100.0, 300.0), step = 2, isWall = false)
        val setup = setup(marker)
        val set = bodySet(marker)
        val bend = pt(500.0, 250.0)
        val ctx = setup.context()
        val north = set.points.minByOrNull { SpatialOperations.haversine(it, bend) }!!
        assertTrue("the nearest corner is the body's northern one", north.latitude > centre.latitude)
        val viaBend = MultipassPull.totalSec(start, bend, ctx) + MultipassPull.totalSec(bend, aim, ctx)
        val viaCorner = MultipassPull.totalSec(start, north, ctx) + MultipassPull.totalSec(north, aim, ctx)
        assertTrue("a grade-2 body's corner pays: shorter legs, a lean of a few seconds", viaCorner <= viaBend)

        assertEquals(listOf(start, north, aim), snapToCorners(setup, listOf(set), listOf(start, bend, aim)))
    }

    @Test
    fun aBendBeyondTheCollarIsNotOfferedTheBodyCorner() {
        val marker = RouteMarker("m", RouteMarkerGeometry.Circle(centre, 100.0, 300.0), step = 2, isWall = false)
        val setup = setup(marker)
        val bend = pt(500.0, 600.0)
        assertEquals(listOf(start, bend, aim), snapToCorners(setup, listOf(bodySet(marker)), listOf(start, bend, aim)))
    }

    @Test
    fun aWallBodysCornerToCornerLegClearsAtTheMarkStep() {
        val wall = RouteMarker("w", RouteMarkerGeometry.Circle(centre, 100.0, 300.0), step = 0, isWall = true)
        val setup = setup(wall)
        val ctx = setup.context()
        val corners = bodySet(wall).points
        assertTrue("the wall blocks its own body", ctx.field.hardBlocked(centre))
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            assertTrue("leg $i between adjacent body corners is clear", MultipassPull.legClear(a, b, ctx))
        }
    }

    private fun setup(marker: RouteMarker): PullSetup {
        val field = RouteCostField(
            markerSources(listOf(marker), baseCostSec(cellM, 30.0), AppConfigStrength, priceEnabled = true),
            cellM
        )
        return PullSetup(marginM, cellM, cellM, field, start, aim, paceMps = 10.0)
    }

    private fun bodySet(marker: RouteMarker): CornerSet {
        val corners = TangentCorners.bodyCorners(listOf(marker), marginM, cellM)
        val reach = corners.associate { it.point to it.reachM }
        return CornerSet(corners.map { it.point }, { p -> (reach[p] ?: 0.0) + cellM })
    }

    private companion object {
        const val AppConfigStrength = 0.2
        const val FRAME_LAT = 43.5
        const val FRAME_LON = 7.0
        val M_PER_DEG_LAT = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val M_PER_DEG_LON = M_PER_DEG_LAT * cos(Math.toRadians(FRAME_LAT))
        fun pt(eastM: Double, northM: Double) = LatLng(FRAME_LAT + northM / M_PER_DEG_LAT, FRAME_LON + eastM / M_PER_DEG_LON)
    }
}
