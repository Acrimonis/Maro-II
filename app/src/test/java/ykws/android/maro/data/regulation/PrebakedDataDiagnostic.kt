package ykws.android.maro.data.regulation

import org.junit.Test
import ykws.android.maro.data.model.LatLng
import java.io.File

/**
 * Diagnostic (not a test): read the prebaked `.bin` and report the zones that
 * contain a probe point, then dump every zone by type.
 *
 * Run:
 *   gradlew testDebugUnitTest --tests "*PrebakedDataDiagnostic*" --rerun-tasks
 *       -Dmaro.point=43.530864,7.034939
 *
 * `maro.point` is `<lat>,<lon>` and defaults to La Salis (Cannes).
 */
class PrebakedDataDiagnostic {

    /** Probe point: `<lat>,<lon>` via `-Dmaro.point`, else La Salis (Cannes). */
    private val probePoint: LatLng = System.getProperty("maro.point")
        ?.split(',')
        ?.takeIf { it.size == 2 }
        ?.let { LatLng(it[0].trim().toDouble(), it[1].trim().toDouble()) }
        ?: LatLng(latitude = 43.549, longitude = 7.019)

    @Test
    fun `dump prebaked regulated zones containing the probe point`() {
        val repoDir = File(System.getProperty("maro.repoDir") ?: "..")
        val binFile = File(repoDir, "data/app-assets/regulated-zones/nice-menton.bin")

        if (!binFile.exists()) {
            println("[DIAG] Prebaked .bin not found at ${binFile.absolutePath}")
            println("[DIAG] Run bake-regulated-zones.bat first.")
            return
        }

        val bytes = binFile.readBytes()
        val zoneSet = RegulatedZoneSerializer.deserialize(bytes)
        println("=".repeat(100))
        println("  PREBAKED DATA DIAGNOSTIC — probe point")
        println("  File: ${binFile.absolutePath}")
        println("  Size: ${binFile.length()} bytes")
        println("  Zones: ${zoneSet.zones.size}")
        println("  Probe point: (${probePoint.latitude}, ${probePoint.longitude})")
        println("=".repeat(100))

        println("\n  Zones containing the probe point:")
        var containsCount = 0
        for ((i, zone) in zoneSet.zones.withIndex()) {
            if (zone.contains(probePoint)) {
                containsCount++
                val c = centroid(zone)
                val cats = zone.displayCategories().joinToString(", ") { it.name }
                println(
                    "    #${i + 1} [${zone.zoneType.name.padEnd(25)}] " +
                        "speed=${zone.speedLimitKn}  source=${zone.source}  ref=${zone.sourceRef}"
                )
                println("         name=\"${zone.name}\"")
                println("         centre=(${"%.4f".format(c.latitude)}, ${"%.4f".format(c.longitude)})")
                println("         categories=[$cats]")
                val desc = zone.description.replace("\n", " | ").take(160)
                if (desc.isNotBlank()) println("         desc=\"$desc\"")
            }
        }
        if (containsCount == 0) {
            println("    (none)")
        }

        println("\n  All zones (sorted by type):")
        val byType = zoneSet.zones.groupBy { it.zoneType }
        for ((type, list) in byType.entries.sortedBy { it.key.name }) {
            println("\n  > ${type.name} (${list.size})")
            for ((i, zone) in list.withIndex()) {
                val c = centroid(zone)
                val inside = if (zone.contains(probePoint)) " <-- CONTAINS PROBE" else ""
                println(
                    "    ${i + 1}. [${zone.zoneType.name}] speed=${zone.speedLimitKn} " +
                        "centre=(${"%.4f".format(c.latitude)}, ${"%.4f".format(c.longitude)}) " +
                        "v=${zone.outerRing.size}$inside"
                )
                val desc = zone.description.replace("\n", " | ").take(140)
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
