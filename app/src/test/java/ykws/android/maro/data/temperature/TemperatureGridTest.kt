package ykws.android.maro.data.temperature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.random.Random

class TemperatureGridTest {

    private val originLat = 43.5696
    private val originLon = 7.1313
    private val side = 1_000.0
    private val grid = TemperatureGrid(TemperatureSource.AIR, originLat, originLon, side)

    private fun lat(yM: Double) = originLat + yM / 111_320.0
    private fun lon(xM: Double) = originLon + xM / (111_320.0 * cos(Math.toRadians(originLat)))

    @Test
    fun `a position on the origin is answered by grid A column 0 row 0`() {
        val point = grid.nearest(originLat, originLon, 1).single()
        assertEquals(0, point.grid)
        assertEquals(0, point.col)
        assertEquals(0, point.row)
        assertEquals(0.0, grid.distanceM(originLat, originLon, point), 0.5)
    }

    @Test
    fun `a position half a side out on both axes is answered by grid B`() {
        val point = grid.nearest(lat(500.0), lon(500.0), 1).single()
        assertEquals(1, point.grid)
        assertEquals(0, point.col)
        assertEquals(0, point.row)
    }

    @Test
    fun `a grid A cell corner is the centre of a grid B point`() {
        // Grid A column 0 row 0 spans -500..500 on both axes: its corner (500, 500) is a grid B centre.
        val corner = grid.nearest(lat(500.0), lon(500.0), 1).single()
        assertEquals(0.0, grid.distanceM(lat(500.0), lon(500.0), corner), 0.5)
    }

    @Test
    fun `the nearest centre is never farther than half a side`() {
        val random = Random(7)
        repeat(500) {
            val x = random.nextDouble(-6_000.0, 6_000.0)
            val y = random.nextDouble(-6_000.0, 6_000.0)
            val point = grid.nearest(lat(y), lon(x), 1).single()
            val distance = grid.distanceM(lat(y), lon(x), point)
            assertTrue("distance $distance at ($x, $y)", distance <= side / 2 + 1.0)
        }
    }

    @Test
    fun `several centres come back nearest first with unique keys`() {
        val points = grid.nearest(lat(120.0), lon(-340.0), 10)
        assertEquals(10, points.size)
        assertEquals(10, points.map { it.key }.toSet().size)
        val distances = points.map { grid.distanceM(lat(120.0), lon(-340.0), it) }
        assertEquals(distances.sorted(), distances)
    }

    @Test
    fun `the same place always gives the same key`() {
        val a = grid.nearest(lat(40.0), lon(40.0), 1).single().key
        val b = grid.nearest(lat(41.0), lon(39.0), 1).single().key
        assertEquals(a, b)
    }

    @Test
    fun `a centre is reported at its own coordinates`() {
        val point = grid.nearest(lat(2_000.0), lon(3_000.0), 1).single()
        val x = hypot(
            (point.longitude - originLon) * 111_320.0 * cos(Math.toRadians(originLat)),
            0.0
        )
        assertTrue("centre x $x is a lattice position", (x % 500.0).let { it < 0.5 || it > 499.5 })
    }
}
