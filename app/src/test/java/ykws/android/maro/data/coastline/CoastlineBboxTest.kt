package ykws.android.maro.data.coastline

import org.junit.Assume
import org.junit.Test
import ykws.android.maro.data.depth.DepthZoneMask
import java.io.File

/**
 * OFFLINE — emit the depth-zone clip-envelope sidecar `<region>.bbox` ("latS latN lonW lonE" =
 * coastline bbox + 6 NM) from the EXISTING coastline asset, **no network**. `bake-env` needs the
 * sidecar for the GDAL clip; this regenerates it from the already-baked `coastline.bin` without
 * re-fetching OSM ([Zone300AssetBaker] also emits it on a full online bake). Opt-in via
 * `-Dmaro.bake=true` (run by `tools\bake-depth.bat` when the `.bin` exists but the `.bbox` doesn't).
 */
class CoastlineBboxTest {

    @Test
    fun `emit bbox sidecar from existing coastline asset`() {
        Assume.assumeTrue("Set -Dmaro.bake=true to emit the bbox sidecar.", System.getProperty("maro.bake") == "true")
        val region = CoastlineGenerator.REGION_ID
        val repoDir = System.getProperty("maro.repoDir")?.let { File(it) } ?: File("..")
        val bin = File(repoDir, "data/app-assets/coastlines/$region.bin")
        Assume.assumeTrue(
            "coastline.bin missing (${bin.path}) — a full (online) bake-coastline is needed first",
            bin.exists()
        )
        val data = CoastlineSerializer.deserialize(bin.readBytes())
        val env = DepthZoneMask.envelopeOf(data.boundingBox)
        val out = File(bin.parentFile, "$region.bbox")
        out.writeText("${env.latSouth} ${env.latNorth} ${env.lonWest} ${env.lonEast}\n")
        println("Emitted bbox sidecar -> ${out.path}: ${out.readText().trim()}")
    }
}
