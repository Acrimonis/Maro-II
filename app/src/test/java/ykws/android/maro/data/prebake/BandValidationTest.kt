package ykws.android.maro.data.prebake

import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import ykws.android.maro.data.coastline.CoastlineSerializer
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.CoastlineSpatialIndex
import java.io.File

/**
 * Regression guards for the prebaked 300 m band (isOnWaterAgain). Both run against the committed
 * asset and skip if it is absent or has no band.
 */
class BandValidationTest {

    @Test
    fun `prebaked 300m band stays within band distance of the coast (no cap spikes)`() {
        val f = File("src/main/assets/coastline/nice-frejus.bin")
        Assume.assumeTrue("no prebaked asset", f.exists())
        val data = CoastlineSerializer.deserialize(f.readBytes())
        val zone = data.zone300
        Assume.assumeTrue("asset has no 300 m band", zone != null)

        // The index drops degenerate rings / tiny fragments, so distances are to the REAL coast.
        val index = CoastlineSpatialIndex(data.allSegments)
        val threshold = zone!!.bandM + 3.0 * zone.gridCellM + 30.0

        var worst = 0.0
        var worstPt: LatLng? = null
        var checked = 0
        val pts = sequence {
            for (poly in zone.fillPolygons) {
                yieldAll(poly.outer)
                for (h in poly.holes) yieldAll(h)
            }
            for (line in zone.seawardLines) yieldAll(line)
        }
        for (p in pts) {
            val d = index.query(p.latitude, p.longitude).distanceMeters
            checked++
            if (d > worst) { worst = d; worstPt = p }
        }

        assertTrue("band has too few vertices to validate ($checked)", checked > 50)
        assertTrue(
            "band vertex %s is %.0f m from the coast (> %.0f) — a capOpenEnds / fragment spike"
                .format(worstPt, worst, threshold),
            worst <= threshold
        )
    }

    /**
     * 300m-pinch guard: the red **seaward** line must sit on the ~bandM offshore contour, never dive
     * into a harbour channel. After `dropPinchedSeawardRuns`, no seaward vertex anywhere is closer
     * than `bandM / 2` to the coast. (Landward *fill* vertices are a separate structure and are
     * allowed to hug the coast — only the seaward line is checked.) The `> 50` count also guards
     * against the filter over-clipping the line away.
     */
    @Test
    fun `prebaked 300m band - no seaward vertex pinches toward the coast`() {
        val f = File("src/main/assets/coastline/nice-frejus.bin")
        Assume.assumeTrue("no prebaked asset", f.exists())
        val data = CoastlineSerializer.deserialize(f.readBytes())
        val zone = data.zone300
        Assume.assumeTrue("asset has no 300 m band", zone != null)

        val index = CoastlineSpatialIndex(data.allSegments)
        val floor = zone!!.bandM / 2.0

        var worst = Double.MAX_VALUE
        var worstPt: LatLng? = null
        var checked = 0
        for (line in zone.seawardLines) for (p in line) {
            val d = index.query(p.latitude, p.longitude).distanceMeters
            checked++
            if (d < worst) { worst = d; worstPt = p }
        }

        assertTrue("seaward line has too few vertices to validate ($checked)", checked > 50)
        assertTrue(
            "seaward vertex %s is only %.0f m from the coast (< %.0f = bandM/2) — a marina pinch"
                .format(worstPt, worst, floor),
            worst >= floor
        )
    }
}
