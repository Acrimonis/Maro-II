package ykws.android.maro.data.coastline

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import ykws.android.maro.spatial.CoastlineSpatialIndex
import ykws.android.maro.spatial.Zone300Builder
import java.io.File

/**
 * MANUAL, ONLINE build-time **baker** for the Nice–Fréjus coastline + 300 m band.
 *
 * Generates the dataset offline (on a networked build machine) and writes it as the bundled
 * asset `app/src/main/assets/coastlines/<region>.bin`. The shipped app then loads a correct,
 * ready-to-use band with **no on-device fetch and no "Bande 300 m" regeneration** —
 * [CoastlineRepository.loadCoastline] reads this asset on a cache miss.
 *
 * It mirrors the production band build exactly (same containment `isWater` + the >6 NM
 * open-water short-circuit + the same cell size), so the baked band equals what a correct
 * on-device regen would produce.
 *
 * **Opt-in:** it needs network (Overpass) and writes a source asset, so it SELF-SKIPS in
 * normal/CI runs and only executes when explicitly enabled with `-Dmaro.bake=true` (forwarded
 * to the test JVM by `app/build.gradle.kts`). Run it whenever the bundled data must be
 * refreshed (e.g. after a classifier/builder fix), then commit the regenerated `.bin` and
 * rebuild/ship the APK:
 *
 *   gradlew.bat :app:testDebugUnitTest --tests "*Zone300AssetBaker*" -Dmaro.bake=true
 */
class Zone300AssetBaker {

    @Test
    fun `bake coastline plus 300m band into the bundled asset`() {
        // Opt-in guard: skip (not fail) unless explicitly enabled — keeps the network fetch and
        // the source-asset write out of normal/CI test runs.
        Assume.assumeTrue(
            "Set -Dmaro.bake=true to run the online coastline/band baker.",
            System.getProperty("maro.bake") == "true"
        )
        runBlocking {
            val regionId = CoastlineGenerator.REGION_ID
            val data = CoastlineGenerator().generate(regionId)

            // Build the band with the SAME classifier the app uses: containment isWater, with the
            // >6 NM open-water short-circuit (mirrors CoastlineRepository.isOnWater).
            val index = CoastlineSpatialIndex(data.allSegments)
            val sixNmM = 6.0 * 1852.0
            val isWater: (Double, Double, Double) -> Boolean = { lat, lon, d ->
                if (d > sixNmM) true else index.isWater(lat, lon)
            }
            val cell = data.metadata.meanSpacingM.coerceIn(5.0, 15.0)
            val band = Zone300Builder(
                index = index,
                segments = data.allSegments,
                refLat = data.metadata.projectionRefLat,
                isWater = isWater,
                cellM = cell
            ).build()
            val baked = data.copy(zone300 = band)

            // testDebugUnitTest runs with the module dir (app/) as the working directory.
            val outDir = File("src/main/assets/coastlines").apply { mkdirs() }
            val outFile = File(outDir, "$regionId.bin")
            outFile.writeBytes(CoastlineSerializer.serialize(baked))

            println(
                "Baked ${baked.allSegments.size} segments + band " +
                    "(${band.fillPolygons.size} fills, ${band.seawardLines.size} lines) → " +
                    "${outFile.absolutePath} (${outFile.length()} bytes). " +
                    "Commit the .bin, then rebuild + ship the APK."
            )
            assertTrue("baked asset should be non-empty", outFile.length() > 0)
        }
    }
}
