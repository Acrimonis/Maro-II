package ykws.android.maro.data.prebake

import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import ykws.android.maro.data.coastline.CoastlineSerializer
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.CoastlineSpatialIndex
import java.io.File

/**
 * Regression guard for the prebaked 300 m band (isOnWaterAgain).
 *
 * The band is, by construction, the water region within [Zone300Data.bandM] of the coast, so EVERY
 * band vertex must lie within ~that distance of the (cleaned) coast. A `capOpenEnds` spike — a flood
 * barrier sealed from an interior fragmented-mainland end out to the grid boundary — puts band
 * geometry far from any coast, so this catches exactly that artifact (and the seg4323 tiny-fragment
 * spike). Runs against the committed asset; skips if it is absent or has no band.
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
}
