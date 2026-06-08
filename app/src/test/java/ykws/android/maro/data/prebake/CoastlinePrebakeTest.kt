package ykws.android.maro.data.prebake

import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import ykws.android.maro.data.coastline.CoastlineGenerator
import ykws.android.maro.data.coastline.CoastlineSerializer
import ykws.android.maro.spatial.CoastlineSpatialIndex
import ykws.android.maro.spatial.Zone300Builder
import java.io.File

/**
 * **Build-time prebake — NOT a unit test.** Fetches OSM, runs the coastline pipeline, builds the
 * Zone300 band on the computer, and writes the cooked coastline (with band) to the **committed**
 * `src/main/assets/coastline/<region>.bin` (singular) — a git-tracked fallback and the
 * [ykws.android.maro.data.prebake.BandValidationTest] fixture.
 *
 * NOTE: the shipped app loads `assets/`**`coastlines`**`/<region>.bin` (plural) from the gitignored
 * `data/app-assets/` tree, baked by [ykws.android.maro.data.coastline.Zone300AssetBaker] /
 * `apk-bake.bat` — NOT this file. To change what the device shows, bake **that**. Needs network.
 * Gated by `-Dmaro.prebake=true`, so it is **skipped in normal runs**. See docs/DepthMappingBake.md.
 */
class CoastlinePrebakeTest {

    @Test
    fun prebakeCoastline() {
        Assume.assumeTrue("set -Dmaro.prebake=true to run", System.getProperty("maro.prebake") == "true")

        val region = CoastlineGenerator.REGION_ID
        val data = runBlocking { CoastlineGenerator().generate(regionId = region) { _, _ -> } }
        val index = CoastlineSpatialIndex(data.allSegments)

        // Use the SAME classifier and cleaned segments as the app (CoastlineRepository.isOnWater →
        // CoastlineSpatialIndex.isWater: mainland side test + real-island containment, with degenerate
        // rings and tiny fragments dropped) so the prebaked band matches the live water/land result.
        val sixNm = 6.0 * 1852.0
        fun isWater(lat: Double, lon: Double, dist: Double): Boolean =
            if (dist > sixNm) true else index.isWater(lat, lon)

        val cell = data.metadata.meanSpacingM.coerceIn(5.0, 15.0)
        val band = Zone300Builder(
            index = index,
            segments = index.usableSegments,
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
