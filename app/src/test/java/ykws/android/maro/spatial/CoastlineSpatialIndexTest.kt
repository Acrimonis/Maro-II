package ykws.android.maro.spatial

import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test
import ykws.android.maro.BuildConfig
import ykws.android.maro.data.coastline.CoastlineSerializer
import ykws.android.maro.data.model.CoastlinePoint
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.LatLng
import java.io.File
import kotlin.math.abs
import kotlin.math.sin

/**
 * Correctness tests for [CoastlineSpatialIndex.query].
 *
 * The key invariant: the grid query must return the SAME nearest-segment distance
 * as an exhaustive brute-force scan. A grid sweep over many positions guards against
 * the "occasional jumps" bug, where stopping at the first non-empty ring missed a
 * closer segment one ring further out.
 */
class CoastlineSpatialIndexTest {

    /** Wavy synthetic coastline (~2.6 km E–W), vertices a few tens of metres apart. */
    private val coast: CoastlineSegment = run {
        val pts = (0..200).map { k ->
            val t = k / 200.0
            CoastlinePoint(
                lat = (43.5 + 0.008 * sin(t * 6.0)).toFloat(),
                lon = (7.0 + 0.03 * t).toFloat()
            )
        }
        CoastlineSegment(osmWayId = 1L, points = pts, isMainland = true, isClosed = false)
    }

    /** Exhaustive nearest-distance over every segment — the ground truth. */
    private fun bruteForce(lat: Double, lon: Double): Double {
        val p = LatLng(lat, lon)
        var best = Double.MAX_VALUE
        val pts = coast.points
        for (i in 0 until pts.size - 1) {
            val a = LatLng(pts[i].lat.toDouble(), pts[i].lon.toDouble())
            val b = LatLng(pts[i + 1].lat.toDouble(), pts[i + 1].lon.toDouble())
            val d = SpatialOperations.pointToSegmentDistance(p, a, b)
            if (d < best) best = d
        }
        return best
    }

    @Test
    fun `query matches brute-force nearest across a grid (no jumps)`() {
        // Deliberately small cells to stress the ring-expansion / stop logic.
        val index = CoastlineSpatialIndex(listOf(coast), cellSizeM = 100.0)
        var checked = 0
        var maxErr = 0.0
        var lat = 43.488
        while (lat <= 43.512) {
            var lon = 6.997
            while (lon <= 7.033) {
                val expected = bruteForce(lat, lon)
                val actual = index.query(lat, lon).distanceMeters
                val err = abs(actual - expected)
                if (err > maxErr) maxErr = err
                assertTrue(
                    "mismatch at ($lat, $lon): index=$actual brute=$expected (err=$err)",
                    err <= 0.5
                )
                checked++
                lon += 0.0004
            }
            lat += 0.0004
        }
        assertTrue("expected a dense sweep, only checked $checked", checked > 2000)
    }

    /**
     * **A segment skipped on its box's bound never changes the answer.** The walk measures a segment only where
     * its box could still beat the best so far, so the distance it returns must be the exhaustive scan's own to
     * the last bit, and where one segment alone holds that minimum it must be the one taken — near the coast,
     * where boxes overlap the best, up to a kilometre off, where the walk spans many rings and skips almost every
     * segment it meets (further out, past the walk's own ring cap, the index answers no coast at all, as it always
     * has), and on each vertex's own longitude a few metres off, where a box's bound is tight to its segment.
     */
    @Test
    fun `query takes the exhaustive nearest segment exactly, near and far`() {
        val fine = CoastlineSpatialIndex(listOf(coast), cellSizeM = 100.0)
        val coarse = CoastlineSpatialIndex(listOf(coast))
        val probes = buildList {
            var lat = 43.483
            while (lat <= 43.517) {
                var lon = 6.988
                while (lon <= 7.042) {
                    add(lat to lon)
                    lon += 0.0004
                }
                lat += 0.0004
            }
            for (v in coast.points) {
                for (offsetDeg in listOf(-0.00003, -0.000001, 0.000001, 0.00003)) {
                    add(v.lat.toDouble() + offsetDeg to v.lon.toDouble())
                }
            }
        }
        for ((lat, lon) in probes) {
            val (expected, segment, unique) = bruteForceNearest(lat, lon)
            for ((name, index) in listOf("fine" to fine, "default" to coarse)) {
                val r = index.query(lat, lon)
                assertEquals("$name cells at ($lat, $lon)", expected, r.distanceMeters, 0.0)
                if (unique) assertEquals("$name cells' segment at ($lat, $lon)", segment, r.vertexIndex)
            }
        }
        assertTrue("expected a wide sweep, only ${probes.size}", probes.size > 9000)
    }

    /** The exhaustive minimum, its segment, and whether no other segment measures the same. */
    private fun bruteForceNearest(lat: Double, lon: Double): Triple<Double, Int, Boolean> {
        val p = LatLng(lat, lon)
        val pts = coast.points
        val d = (0 until pts.size - 1).map { i ->
            SpatialOperations.pointToSegmentDistance(
                p,
                LatLng(pts[i].lat.toDouble(), pts[i].lon.toDouble()),
                LatLng(pts[i + 1].lat.toDouble(), pts[i + 1].lon.toDouble())
            )
        }
        val best = d.min()
        return Triple(best, d.indexOf(best), d.count { it == best } == 1)
    }

    @Test
    fun `query returns the closest segment's vertex info`() {
        val index = CoastlineSpatialIndex(listOf(coast), cellSizeM = 100.0)
        val r = index.query(43.5, 7.015)
        assertTrue(r.distanceMeters < Double.MAX_VALUE)
        assertTrue("polylineIdx populated", r.polylineIdx == 0)
        assertTrue("vertexIndex populated", r.vertexIndex in 0 until coast.points.size - 1)
        assertEquals(bruteForce(43.5, 7.015), r.distanceMeters, 0.5)
    }

    @Test
    fun `offshore point near the coast still resolves`() {
        val index = CoastlineSpatialIndex(listOf(coast), cellSizeM = 100.0)
        val r = index.query(43.486, 7.015)   // ~0.7 km south of the coast
        assertTrue(r.distanceMeters < Double.MAX_VALUE)
        assertEquals(bruteForce(43.486, 7.015), r.distanceMeters, 0.5)
    }

    @Test
    fun `empty index returns sentinel`() {
        val index = CoastlineSpatialIndex(emptyList())
        assertEquals(Double.MAX_VALUE, index.query(43.5, 7.0).distanceMeters, 0.0)
        assertEquals(Double.MAX_VALUE, index.distanceM(43.5, 7.0), 0.0)
    }

    /**
     * **`distanceM` is `query`'s distance to the last bit** — on the coast, kilometres off it where the cell's
     * candidates are many, outside the grid where it falls back, on an island ring beside the mainland, and read a
     * second time from a cell already built.
     */
    @Test
    fun `distanceM answers query's own distance, near, far and outside the grid`() {
        val island = CoastlineSegment(
            osmWayId = 2L,
            points = (0..40).map { k ->
                val a = 2.0 * Math.PI * k / 40
                CoastlinePoint(lat = (43.49 + 0.002 * sin(a)).toFloat(), lon = (7.012 + 0.003 * kotlin.math.cos(a)).toFloat())
            },
            isMainland = false,
            isClosed = true
        )
        for (cellM in listOf(100.0, 500.0)) {
            val index = CoastlineSpatialIndex(listOf(coast, island), cellSizeM = cellM)
            repeat(2) { pass ->
                var lat = 43.40
                while (lat <= 43.60) {
                    var lon = 6.90
                    while (lon <= 7.13) {
                        assertEquals(
                            "cells $cellM m, pass $pass at ($lat, $lon)",
                            index.query(lat, lon).distanceMeters, index.distanceM(lat, lon), 0.0
                        )
                        lon += 0.00137
                    }
                    lat += 0.00113
                }
            }
        }
    }

    /**
     * **Three readers on one fresh index answer `query`'s distance** — the three rungs' own shape: the cells are
     * built under contention, each list stored once, and every read, during and after, is the walk's own double.
     */
    @Test
    fun `distanceM answers query's own distance under three concurrent readers`() {
        val index = CoastlineSpatialIndex(listOf(coast), cellSizeM = 100.0)
        val probes = buildList {
            var lat = 43.45
            while (lat <= 43.55) {
                var lon = 6.95
                while (lon <= 7.08) {
                    add(lat to lon)
                    lon += 0.0009
                }
                lat += 0.0007
            }
        }
        val answers = List(3) { DoubleArray(probes.size) }
        val readers = answers.mapIndexed { t, out ->
            Thread {
                // Each reader starts at its own offset, so the three meet in cells none of them has built yet.
                for (i in probes.indices) {
                    val k = (i + t * probes.size / 3) % probes.size
                    out[k] = index.distanceM(probes[k].first, probes[k].second)
                }
            }
        }
        readers.forEach { it.start() }
        readers.forEach { it.join() }
        for ((k, probe) in probes.withIndex()) {
            val expected = index.query(probe.first, probe.second).distanceMeters
            for (out in answers) assertEquals("at $probe", expected, out[k], 0.0)
        }
    }

    /** The same identity on the shipped region's coast, wherever the bake is present. */
    @Test
    fun `distanceM answers query's own distance on the baked coast`() {
        val file = File("../data/app-assets/coastlines/${BuildConfig.REGION_ID}.bin")
        Assume.assumeTrue("no baked coastline at $file", file.isFile)
        val coast = CoastlineSerializer.deserialize(file.readBytes())
        val index = CoastlineSpatialIndex(coast.allSegments)
        val box = coast.boundingBox
        var checked = 0
        var lat = box.latSouth
        while (lat <= box.latNorth) {
            var lon = box.lonWest
            while (lon <= box.lonEast) {
                assertEquals("at ($lat, $lon)", index.query(lat, lon).distanceMeters, index.distanceM(lat, lon), 0.0)
                checked++
                lon += 0.00731
            }
            lat += 0.00317
        }
        assertTrue("expected a wide sweep, only $checked", checked > 5000)
    }
}
