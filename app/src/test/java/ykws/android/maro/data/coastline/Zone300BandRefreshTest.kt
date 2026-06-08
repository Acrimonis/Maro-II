package ykws.android.maro.data.coastline

import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import ykws.android.maro.spatial.CoastlineSpatialIndex
import ykws.android.maro.spatial.Zone300Builder
import java.io.File

/**
 * Network-free **300 m band refresh**: rebuilds the band from the EXISTING shipped coastline
 * (`data/app-assets/coastlines/<region>.bin`) and writes it back — no OSM fetch. Use after a band
 * builder/classifier tweak when the coastline geometry itself hasn't changed. Opt-in via
 * `-Dmaro.bake=true` (run by `tools\bake-zone300.bat`). Mirrors the band build in [Zone300AssetBaker].
 */
class Zone300BandRefreshTest {

    @Test
    fun `refresh 300m band from existing coastline asset`() {
        Assume.assumeTrue(
            "Set -Dmaro.bake=true to refresh the 300 m band.",
            System.getProperty("maro.bake") == "true"
        )
        runBlocking {
            val region = CoastlineGenerator.REGION_ID
            val repoDir = System.getProperty("maro.repoDir")?.let { File(it) } ?: File("..")
            val binFile = File(repoDir, "data/app-assets/coastlines/$region.bin")
            Assume.assumeTrue(
                "coastline asset missing (${binFile.path}) — run bake-coastline first",
                binFile.exists()
            )

            val data = CoastlineSerializer.deserialize(binFile.readBytes())
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
            binFile.writeBytes(CoastlineSerializer.serialize(data.copy(zone300 = band)))
            println(
                "Refreshed 300 m band (${band.fillPolygons.size} fills, ${band.seawardLines.size} lines) " +
                    "-> ${binFile.path} (${binFile.length()} bytes)"
            )
        }
    }
}
