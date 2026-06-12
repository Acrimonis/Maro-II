package ykws.android.maro.data.regulation

import org.junit.Test
import ykws.android.maro.data.model.LatLng
import java.io.File

/**
 * Diagnostic: read the prebaked .bin file and print centroids of all zones.
 * Kept for manual verification after re-bakes.
 *
 * Run: `gradlew testDebugUnitTest --tests "*PrebakedDataDiagnostic*" --rerun-tasks`
 */
class PrebakedDataDiagnostic {

    @Test
    fun `dump prebaked regulated zone centroids`() {
        val repoDir = File(System.getProperty("maro.repoDir") ?: "..")
        val binFile = File(repoDir, "data/app-assets/regulated-zones/nice-frejus.bin")

        if (!binFile.exists()) {
            println("[DIAG] Prebaked .bin not found at ${binFile.absolutePath}")
            println("[DIAG] Run bake-regulated-zones.bat first.")
            return
        }

        val bytes = binFile.readBytes()
        val zoneSet = RegulatedZoneSerializer.deserialize(bytes)
        println("=" .repeat(70))
        println("  PREBAKED DATA DIAGNOSTIC")
        println("  File: ${binFile.absolutePath}")
        println("  Size: ${binFile.length()} bytes")
        println("  Zones: ${zoneSet.zones.size}")
        println("  Fetched: ${zoneSet.metadata.fetchTimestampMs}")
        println("=" .repeat(70))

        val byType = zoneSet.zones.groupBy { it.zoneType }
        for ((type, list) in byType.entries.sortedBy { it.key.name }) {
            println("\n  ▶ ${type.name} (${list.size})")
            for ((i, zone) in list.withIndex()) {
                val c = centroid(zone)
                println("    ${i + 1}. centre=(${"%.4f".format(c.latitude)}, ${"%.4f".format(c.longitude)}) " +
                        "sommets=${zone.outerRing.size} ref=${zone.sourceRef.take(30)}")
                if (zone.speedLimitKn != null) {
                    println("         vitesse=${zone.speedLimitKn} nds")
                }
            }
        }

        // Check for any zones with clearly wrong coordinates
        val suspectZones = zoneSet.zones.filter {
            val c = centroid(it)
            c.latitude < 43.0 || c.latitude > 44.0 || c.longitude < 6.5 || c.longitude > 7.5
        }
        if (suspectZones.isEmpty()) {
            println("\n✅ All ${zoneSet.zones.size} zones within expected lat/lon range")
        } else {
            println("\n⚠️  ${suspectZones.size} zones OUTSIDE expected range")
        }
    }

    private fun centroid(zone: RegulatedZone): LatLng {
        val avgLat = zone.outerRing.map { it.latitude }.average()
        val avgLon = zone.outerRing.map { it.longitude }.average()
        return LatLng(avgLat, avgLon)
    }
}
