package ykws.android.maro.data.regulation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.LatLng
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Live integration test that fetches regulation zones around **Cap d'Antibes**
 * from the public SHOM INSPIRE WFS and displays a summary.
 *
 * This is a **smoke test** — it requires network access to the SHOM WFS.
 * It does NOT gate on `-Dmaro.prebake=true` since it only reads, never writes.
 *
 * Run:
 *   gradlew testDebugUnitTest --tests "*CapAntibesZoneTest*"
 */
class CapAntibesZoneTest {

    // Wide bbox covering the full Nice–Fréjus corridor (matches project zone)
    private val capBbox = BoundingBox(
        lonWest = 6.70, latSouth = 43.35,
        lonEast = 7.31, latNorth = 43.73
    )

    @Test
    fun `fetch and summarize regulation zones around Cap d'Antibes`() = runBlocking {
        println("=" .repeat(70))
        println("  Réglementation maritime — Cap d'Antibes / Îles de Lérins")
        println("  Bbox: ${capBbox.lonWest}°E, ${capBbox.latSouth}°N → ${capBbox.lonEast}°E, ${capBbox.latNorth}°N")
        println("=" .repeat(70))

        val client = ShomRegulationClient()
        val zones = client.fetchZones(capBbox)

        println("\n${zones.size} zone(s) trouvée(s) dans la zone:")
        println("-".repeat(70))

        // Group by type
        val byType = zones.groupBy { it.zoneType }
        for ((type, list) in byType.entries.sortedByDescending { it.value.size }) {
            println("\n  ▶ ${typeLabel(type)} (${list.size})")
            println("  ${"-".repeat(50)}")
            for ((i, zone) in list.withIndex()) {
                val areaKm2 = approximateAreaKm2(zone)
                val centroid = centroid(zone)
                println("    ${i + 1}. ${zone.name.ifBlank { "(sans nom)" }}")
                if (zone.speedLimitKn != null) {
                    println("       Vitesse limite: ${zone.speedLimitKn} nds")
                }
                println("       Source: ${zone.source} · Réf: ${zone.sourceRef.ifBlank { "—" }}")
                println("       Surface: ~${"%.1f".format(areaKm2)} km²")
                println("       Centre: ${"%.4f".format(centroid.latitude)}°N, ${"%.4f".format(centroid.longitude)}°E")
                println("       Sommets: ${zone.outerRing.size}")
                if (zone.description.isNotBlank()) {
                    println("       Info: ${zone.description.take(120)}")
                }
            }
        }

        // Summary stats
        println("\n${"=".repeat(70)}")
        println("  Résumé: ${zones.size} zones · ${byType.size} types de réglementation")
        val totalArea = zones.sumOf { approximateAreaKm2(it) }
        println("  Surface totale couverte: ~${"%.1f".format(totalArea)} km²")
        val sources = zones.map { it.source }.distinct()
        println("  Sources: ${sources.joinToString(", ")}")
        println("${"=".repeat(70)}")

        // The point of this test: confirm we got data from the live endpoint
        assertTrue("Expected at least 1 regulation zone around Cap d'Antibes", zones.isNotEmpty())
        println("\n✅ Test passed: ${zones.size} zones fetched from live SHOM INSPIRE WFS")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun typeLabel(type: RegulatedZoneType): String = when (type) {
        RegulatedZoneType.SPEED_LIMIT           -> "🚤 Limitation de vitesse"
        RegulatedZoneType.ANCHORING_PROHIBITED  -> "⚓ Mouillage interdit"
        RegulatedZoneType.ACCESS_PROHIBITED     -> "🚫 Accès interdit"
        RegulatedZoneType.ENVIRONMENTAL         -> "🌿 Protection environnementale"
        RegulatedZoneType.MOORING               -> "⛵ Amarrage réglementé"
        RegulatedZoneType.FISHING_PROHIBITED    -> "🎣 Pêche interdite"
        RegulatedZoneType.NAVIGATION_RESTRICTION -> "🧭 Restriction de navigation"
        RegulatedZoneType.OTHER                 -> "❓ Autre réglementation"
    }

    private fun centroid(zone: RegulatedZone): LatLng {
        val avgLat = zone.outerRing.map { it.latitude }.average()
        val avgLon = zone.outerRing.map { it.longitude }.average()
        return LatLng(avgLat, avgLon)
    }

    /** Approximate polygon area using the Shoelace formula on a local planar projection. */
    private fun approximateAreaKm2(zone: RegulatedZone): Double {
        if (zone.outerRing.size < 3) return 0.0
        val refLat = zone.outerRing.map { it.latitude }.average()
        val mPerDegLat = 111_320.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(refLat))

        var area = 0.0
        val n = zone.outerRing.size
        for (i in 0 until n - 1) {
            val p1 = zone.outerRing[i]
            val p2 = zone.outerRing[(i + 1) % n]
            val x1 = p1.longitude * mPerDegLon
            val y1 = p1.latitude * mPerDegLat
            val x2 = p2.longitude * mPerDegLon
            val y2 = p2.latitude * mPerDegLat
            area += x1 * y2 - x2 * y1
        }
        return abs(area) / 2.0 / 1_000_000.0
    }
}
