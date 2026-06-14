package ykws.android.maro.data.regulation

import org.junit.Test
import ykws.android.maro.data.model.LatLng
import java.io.File

/**
 * Diagnostic: read the prebaked .bin file and for each zone print centroid,
 * description, display categories, and whether it contains the La Salis point.
 *
 * Run: `gradlew testDebugUnitTest --tests "*PrebakedDataDiagnostic*" --rerun-tasks -Dmaro.repoDir="."`
 */
class PrebakedDataDiagnostic {

    /** La Salis port (Cannes) — approximate boat position reported by user */
    private val laSalisPoint = LatLng(latitude = 43.549, longitude = 7.019)

    @Test
    fun `dump prebaked regulated zone centroids with descriptions`() {
        val repoDir = File(System.getProperty("maro.repoDir") ?: "..")
        val binFile = File(repoDir, "data/app-assets/regulated-zones/nice-frejus.bin")

        if (!binFile.exists()) {
            println("[DIAG] Prebaked .bin not found at ${binFile.absolutePath}")
            println("[DIAG] Run bake-regulated-zones.bat first.")
            return
        }

        val bytes = binFile.readBytes()
        val zoneSet = RegulatedZoneSerializer.deserialize(bytes)
        println("=" .repeat(100))
        println("  PREBAKED DATA DIAGNOSTIC — La Salis focus")
        println("  File: ${binFile.absolutePath}")
        println("  Size: ${binFile.length()} bytes")
        println("  Zones: ${zoneSet.zones.size}")
        println("  La Salis point: (${laSalisPoint.latitude}, ${laSalisPoint.longitude})")
        println("=" .repeat(100))

        println("\n  Zones containing La Salis point (boat is INSIDE these):")
        var containsCount = 0
        for ((i, zone) in zoneSet.zones.withIndex()) {
            if (zone.contains(laSalisPoint)) {
                containsCount++
                val c = centroid(zone)
                val cats = zone.displayCategories().joinToString(", ") { it.name }
                println("    #${i + 1} [${zone.zoneType.name.padEnd(25)}] " +
                        "centre=(${"%.4f".format(c.latitude)}, ${"%.4f".format(c.longitude)})")
                println("         name=\"${zone.name}\"")
                println("         desc=\"${zone.description.take(120)}\"")
                println("         speed=${zone.speedLimitKn}  categories=[$cats]")
            }
        }

        if (containsCount == 0) {
            println("    ⚠️  NONE — boat is not inside any zone polygon")
        }

        println("\n  All zones (sorted by type):")
        val byType = zoneSet.zones.groupBy { it.zoneType }
        for ((type, list) in byType.entries.sortedBy { it.key.name }) {
            println("\n  ▶ ${type.name} (${list.size})")
            for ((i, zone) in list.withIndex()) {
                val c = centroid(zone)
                val cats = zone.displayCategories().joinToString(", ") { it.name }
                val inLaSalis = if (zone.contains(laSalisPoint)) " ★ INSIDE La Salis" else ""
                println("    ${i + 1}. centre=(${"%.4f".format(c.latitude)}, ${"%.4f".format(c.longitude)}) " +
                        "v=${zone.outerRing.size} cats=[$cats]$inLaSalis")
                val desc = zone.description.take(100)
                if (desc.isNotBlank()) println("         desc=\"$desc\"")
            }
        }

        println()
    }

    private fun centroid(zone: RegulatedZone): LatLng {
        val avgLat = zone.outerRing.map { it.latitude }.average()
        val avgLon = zone.outerRing.map { it.longitude }.average()
        return LatLng(avgLat, avgLon)
    }
}
