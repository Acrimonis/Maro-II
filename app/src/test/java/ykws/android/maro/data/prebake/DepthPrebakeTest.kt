package ykws.android.maro.data.prebake

import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import ykws.android.maro.data.coastline.CoastlineSerializer
import ykws.android.maro.data.depth.DepthConstants
import ykws.android.maro.data.depth.DepthGenerator
import ykws.android.maro.data.depth.DepthSerializer
import ykws.android.maro.data.depth.DepthZoneMask
import ykws.android.maro.data.depth.raster.AsciiGridParser
import ykws.android.maro.data.model.DepthSource
import java.io.File

/**
 * **Build-time prebake — NOT a unit test.** Reads the GDAL-baked `.asc` ingredients from assets,
 * merges + validates them on the computer, and writes the cooked grid to `assets/depth/<region>.bin`
 * (the default the app ships). Gated by `-Dmaro.prebake=true`, so it is **skipped in normal runs**.
 * Invoked by `apk-build.bat` after `tools\bake_depth.bat`. See docs/DepthMappingBake.md.
 */
class DepthPrebakeTest {

    @Test
    fun prebakeDepth() {
        Assume.assumeTrue("set -Dmaro.prebake=true to run", System.getProperty("maro.prebake") == "true")

        val region = DepthConstants.REGION_ID
        // All baked data lives in the gitignored data/app-assets tree (packaged via the build's asset
        // srcDir); resolve via maro.repoDir so the test CWD (app/) doesn't matter.
        val repoDir = System.getProperty("maro.repoDir")?.let { File(it) } ?: File("..")
        val depthDir = File(repoDir, "data/app-assets/depth")
        val emodnetAsc = File(depthDir, "emodnet-$region.asc")
        val litto3dAsc = File(depthDir, "litto3d-$region.asc")
        Assume.assumeTrue("EMODnet .asc missing — run tools\\bake_emodnet.bat first", emodnetAsc.exists())

        // The depth zone DERIVES from the shipped coastline: envelope = coastline bbox + 6 NM, then
        // DepthZoneMask trims to the exact 6 NM-of-coast buffer (same asset the app loads).
        val coastBin = File(repoDir, "data/app-assets/coastlines/$region.bin")
        Assume.assumeTrue(
            "coastline asset missing (${coastBin.path}) — bake the coastline first; the depth zone derives from it",
            coastBin.exists()
        )
        val coast = CoastlineSerializer.deserialize(coastBin.readBytes())
        val envelope = DepthZoneMask.envelopeOf(coast.boundingBox)

        val deep = AsciiGridParser.parse(
            emodnetAsc, DepthSource.EMODNET, DepthSource.EMODNET.nominalResM, negate = true
        )
        val shallow = if (litto3dAsc.exists()) AsciiGridParser.parse(
            litto3dAsc, DepthSource.LITTO3D, DepthSource.LITTO3D.nominalResM,
            negate = true, latOffsetM = DepthConstants.IGN69_ABOVE_LAT_M
        ) else null

        val grid = runBlocking {
            DepthGenerator().generate(
                region, bbox = envelope, deepSources = listOf(deep), shallowSource = shallow,
                coastlineSegments = coast.allSegments, nowMs = 0L
            )
        }

        val out = File(depthDir, "$region.bin")
        out.parentFile?.mkdirs()
        out.writeBytes(DepthSerializer.serialize(grid))
        println("Prebaked depth grid -> ${out.path} (${out.length()} bytes)")

        // Coverage by source + the embedded validation report (collision-tier RMSE is the safety gate).
        val counts = IntArray(DepthSource.entries.size)
        for (b in grid.source) { val id = b.toInt() and 0xFF; if (id < counts.size) counts[id]++ }
        println("Coverage: " + DepthSource.entries.joinToString(" ") { "${it.name}=${counts[it.id]}" } + " /${grid.source.size}")
        grid.metadata.validation?.let { v ->
            println("Validation: passed=${v.passed} rmse=%.3f bias=%.3f maxErr=%.3f pts=${v.controlPointCount} uncovered=${v.uncoveredCount} datumMismatch=${v.datumMismatchSuspected}"
                .format(v.rmseM, v.meanBiasM, v.maxAbsErrM))
            v.tiers.forEach { t -> println("  tier %.0f-%.0f m: rmse=%.3f bias=%.3f max=%.3f n=${t.count}".format(t.minDepthM, t.maxDepthM, t.rmseM, t.meanBiasM, t.maxAbsErrM)) }
        } ?: println("Validation: (none — no control points covered)")
    }
}
