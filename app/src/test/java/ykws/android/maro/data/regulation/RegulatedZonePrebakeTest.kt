package ykws.android.maro.data.regulation

import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import java.io.File

/**
 * **Build-time prebake — NOT a unit test.** Fetches regulation zones from SHOM WFS
 * and INPN WFS, aggregates with hardcoded seed zones, deduplicates, filters by vessel
 * size and zone type, and writes the cooked `.bin` to `assets/regulated-zones/<region>.bin`
 * for bundling into the APK.
 *
 * Gated by `-Dmaro.prebake=true`, so it is **skipped in normal runs**.
 * Invoked by `tools\bake-regulated-zones.bat`.
 */
class RegulatedZonePrebakeTest {

    @Test
    fun prebakeRegulatedZones() {
        Assume.assumeTrue(
            "set -Dmaro.prebake=true to run",
            System.getProperty("maro.prebake") == "true"
        )

        val region = "nice-frejus"

        // Resolve repo root — the test CWD is typically app/, fall back to parent.
        val repoDir = System.getProperty("maro.repoDir")?.let { File(it) } ?: File("..")
        val outputDir = File(repoDir, "data/app-assets/regulated-zones")
        outputDir.mkdirs()

        // Expanded bbox: Menton (7.6°E) to Fréjus (6.7°E)
        val bbox = BoundingBox(
            lonWest = 6.7, latSouth = 43.4,
            lonEast = 7.6, latNorth = 43.8
        )

        // ── 1. Fetch from SHOM WFS ────────────────────────────────────────────
        println("[prebake] Fetching SHOM regulation zones for $region...")
        val shomZones: List<RegulatedZone> = runBlocking {
            ShomRegulationClient().fetchZones(bbox = bbox)
        }
        println("[prebake] SHOM returned ${shomZones.size} zones")

        // ── 2. Fetch from IGN API Carto Nature (Natura 2000, etc.) ──────────
        println("[prebake] Fetching IGN Nature zones for $region...")
        val ignZones: List<RegulatedZone> = runBlocking {
            IgnCartoNatureClient().fetchZones(bbox = bbox)
        }
        println("[prebake] IGN Nature returned ${ignZones.size} zones")

        // ── 3. Aggregate (no seed zones — all data from live sources) ──────────
        val nowMs = System.currentTimeMillis()
        val zoneSet = RegulationAggregator.aggregate(
            shomZones = shomZones,
            inpnZones = ignZones,    // IGN API Carto Nature data
            bbox = bbox,
            nowMs = nowMs
        )
        println("[prebake] Aggregated: ${zoneSet.metadata.totalZones} zones after dedup")

        // ── 5. Per-source summary ──────────────────────────────────────────────
        val bySource = zoneSet.zones.groupBy { it.source }
        println("[prebake] Per-source breakdown:")
        for ((source, zones) in bySource.entries.sortedByDescending { it.value.size }) {
            println("         $source: ${zones.size} zones")
        }

        // ── 6. Per-zone detail dump ────────────────────────────────────────────
        println("[prebake] Per-zone details (full descriptions):")
        for ((i, z) in zoneSet.zones.withIndex()) {
            val desc = z.description.replace("\n", " | ").take(120)
            val src = z.source.padEnd(5)
            println("         #${i + 1} [${src}] [${z.zoneType.name.padEnd(25)}] " +
                    "${z.name.padEnd(40)} desc=${desc}")
        }

        // ── 7. Display-category summary ────────────────────────────────────────
        val byCategory = zoneSet.zones
            .flatMap { zone ->
                zone.displayCategories().map { cat -> cat to zone.speedLimitKn }
            }
            .groupBy { it.first }
        println("[prebake] Display categories in strip:")
        for ((cat, list) in byCategory.entries.sortedBy { it.key.ordinal }) {
            val speeds = list.mapNotNull { it.second }.distinct().sorted()
            val speedInfo = if (speeds.isNotEmpty()) " (${speeds.joinToString(", ")} kn)" else ""
            println("         ${cat.name.padEnd(20)} ${list.size} zones$speedInfo")
        }

        // ── 8. Serialize to .bin ──────────────────────────────────────────────
        val out = File(outputDir, "$region.bin")
        out.writeBytes(RegulatedZoneSerializer.serialize(zoneSet))
        println("[prebake] Wrote ${out.length()} bytes -> ${out.path}")

        // ── 9. Breakdown by zone type ─────────────────────────────────────────
        val byType = zoneSet.zones.groupBy { it.zoneType }
        println("[prebake] Breakdown by zone type:")
        for ((type, list) in byType.entries.sortedByDescending { it.value.size }) {
            println("         ${type.name.padEnd(30)} ${list.size}")
        }
        println("[prebake] Done.")
    }
}
