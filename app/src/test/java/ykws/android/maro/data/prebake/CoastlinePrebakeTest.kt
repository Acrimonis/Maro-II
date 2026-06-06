package ykws.android.maro.data.prebake

import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import ykws.android.maro.data.coastline.CoastlineGenerator
import ykws.android.maro.data.coastline.CoastlineSerializer
import ykws.android.maro.spatial.CoastlineSpatialIndex
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Zone300Builder
import java.io.File
import kotlin.math.PI

/**
 * **Build-time prebake — NOT a unit test.** Fetches OSM, runs the coastline pipeline, builds the
 * Zone300 band on the computer, and writes the cooked coastline (with band) to
 * `assets/coastline/<region>.bin` (the default the app ships). Needs network. Gated by
 * `-Dmaro.prebake=true`, so it is **skipped in normal runs**. See docs/DepthMappingBake.md.
 */
class CoastlinePrebakeTest {

    @Test
    fun prebakeCoastline() {
        Assume.assumeTrue("set -Dmaro.prebake=true to run", System.getProperty("maro.prebake") == "true")

        val region = CoastlineGenerator.REGION_ID
        val data = runBlocking { CoastlineGenerator().generate(regionId = region) { _, _ -> } }
        val index = CoastlineSpatialIndex(data.allSegments)

        // isWater mirrors CoastlineRepository.isOnWater (south-ray parity + island enclosure),
        // duplicated here because the band build must run off-device at prebake time.
        val sixNm = 6.0 * 1852.0
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        fun isWater(lat: Double, lon: Double, dist: Double): Boolean {
            if (dist > sixNm) return true
            val rayEnd = lat - (sixNm / mPerDegLat)
            val cand = index.queryColumn(lat, lon, sixNm)
            var mainland = 0
            for (c in cand) if (c.polylineIdx == 0 &&
                SpatialOperations.rayCrossesSegmentSouth(lon, lat, rayEnd, c.a, c.b)) mainland++
            val water = mainland % 2 == 0
            if (water) {
                for ((_, segs) in cand.filter { it.polylineIdx > 0 }.groupBy { it.polylineIdx }) {
                    var x = 0
                    for (s in segs) if (SpatialOperations.rayCrossesSegmentSouth(lon, lat, rayEnd, s.a, s.b)) x++
                    if (x % 2 == 1) return false
                }
            }
            return water
        }

        val cell = data.metadata.meanSpacingM.coerceIn(5.0, 15.0)
        val band = Zone300Builder(
            index = index,
            segments = data.allSegments,
            refLat = data.metadata.projectionRefLat,
            isWater = { la, lo, d -> isWater(la, lo, d) },
            cellM = cell
        ).build()
        val withZone = data.copy(zone300 = band)

        val out = File("src/main/assets/coastline/$region.bin")
        out.parentFile?.mkdirs()
        out.writeBytes(CoastlineSerializer.serialize(withZone))
        println("Prebaked coastline -> ${out.path} (${out.length()} bytes)")
    }
}
