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
 * Generates the dataset offline (on a networked build machine) and writes it into the
 * **gitignored** `data/app-assets/coastlines/<region>.bin` (NOT committed to git).
 * `app/build.gradle.kts` adds `data/app-assets` as an assets source root, so the build packages
 * it into the APK at `assets/coastlines/<region>.bin`. The shipped app then loads a correct,
 * ready-to-use band with **no on-device fetch and no "Bande 300 m" regeneration** —
 * [CoastlineRepository.loadCoastline] reads it (cache miss → bundled asset).
 *
 * It mirrors the production band build exactly (same containment `isWater` + the >6 NM
 * open-water short-circuit + the same cell size), so the baked band equals what a correct
 * on-device regen would produce.
 *
 * **Opt-in:** it needs network (Overpass) and writes into data/, so it SELF-SKIPS in normal/CI
 * runs and only executes when explicitly enabled with `-Dmaro.bake=true` (forwarded to the test
 * JVM by `app/build.gradle.kts`). Run it whenever the bundled data must be refreshed (e.g. after
 * a classifier/builder fix), then just rebuild/ship the APK — the band is gitignored and
 * incorporated at build time, so there is nothing to commit:
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
            // Robust online bake: a generous per-endpoint timeout (a slow Overpass server can take
            // far longer than the 10 s runtime default) AND several retry attempts with back-off, so
            // a transient disruption is recovered from instead of failing the whole bake. The
            // on-device path keeps the fail-fast defaults (1 attempt, 10 s) → bundled-asset fallback.
            val data = CoastlineGenerator(
                httpTimeoutSeconds = 180,
                maxFetchAttempts = 4
            ).generate(regionId)

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

            // Bake into the GITIGNORED data/ tree (not committed). app/build.gradle.kts adds
            // data/app-assets as an assets source root, so the build packages it into the APK.
            val repoDir = System.getProperty("maro.repoDir")?.let { File(it) } ?: File("..")
            val outDir = File(repoDir, "data/app-assets/coastlines").apply { mkdirs() }
            val outFile = File(outDir, "$regionId.bin")
            outFile.writeBytes(CoastlineSerializer.serialize(baked))

            println(
                "Baked ${baked.allSegments.size} segments + band " +
                    "(${band.fillPolygons.size} fills, ${band.seawardLines.size} lines) → " +
                    "${outFile.absolutePath} (${outFile.length()} bytes). " +
                    "Now apk-build.bat + apk-deploy.bat — incorporated from gitignored data/, nothing to commit."
            )
            assertTrue("baked asset should be non-empty", outFile.length() > 0)
        }
    }
}
