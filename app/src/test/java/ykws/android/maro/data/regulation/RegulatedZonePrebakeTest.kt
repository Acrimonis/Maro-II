package ykws.android.maro.data.regulation

import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import java.io.File
import java.util.Properties

/**
 * **Build-time prebake — NOT a unit test.** Fetches regulation zones from SHOM WFS
 * and INPN WFS, aggregates with hardcoded seed zones, deduplicates, drops the
 * hard-filtered zone types named by `regulatedZone.filteredTypes`, and writes the
 * cooked `.bin` to `assets/regulated-zones/<region>.bin` for bundling into the APK.
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

        val region = "nice-menton"

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
        val aggregated = RegulationAggregator.aggregate(
            shomZones = shomZones,
            inpnZones = ignZones,    // IGN API Carto Nature data
            bbox = bbox,
            nowMs = nowMs
        )
        println("[prebake] Aggregated: ${aggregated.metadata.totalZones} zones after dedup")

        // ── 3b. Hard-filter the excluded types (the bake's one type gate) ──────
        // Driven by `regulatedZone.filteredTypes` in maro.properties, but only the
        // types RegulationFilter.HARD_FILTERED_TYPES names are dropped here — so
        // listing FISHING_PROHIBITED removes the fishing cantonnements while the
        // soft list (ENVIRONMENTAL/OTHER) stays in the set. The filter's vessel-size
        // and boilerplate gates stay dormant.
        val filteredTypes = readFilteredTypes(repoDir)
        println("[prebake] regulatedZone.filteredTypes=" +
                filteredTypes.joinToString(",") { it.name }.ifEmpty { "(none)" })
        val finalSet = RegulationFilter.dropHardFiltered(aggregated, filteredTypes)
        println("[prebake] Hard-filter dropped ${aggregated.zones.size - finalSet.zones.size} " +
                "zones; kept ${finalSet.zones.size}")

        // ── 4. Seed the app-side override file ─────────────────────────────────
        // One entry per zone (add-only). The app applies the speed overrides and
        // ignore flags at runtime from app/src/main/assets/zones.properties, so
        // the serialized .bin keeps the raw set.
        val zonesFile = RegulationSpeedOverrides.file(repoDir)
        RegulationSpeedOverrides.seed(zonesFile, finalSet.zones)
        println("[prebake] Seeded ${finalSet.zones.size} zone entries at ${zonesFile.path}")

        // ── 5. Per-source summary ──────────────────────────────────────────────
        val bySource = finalSet.zones.groupBy { it.source }
        println("[prebake] Per-source breakdown:")
        for ((source, zones) in bySource.entries.sortedByDescending { it.value.size }) {
            println("         $source: ${zones.size} zones")
        }

        // ── 6. Per-zone detail dump ────────────────────────────────────────────
        println("[prebake] Per-zone details (full descriptions):")
        for ((i, z) in finalSet.zones.withIndex()) {
            val desc = z.description.replace("\n", " | ").take(120)
            val src = z.source.padEnd(5)
            println("         #${i + 1} [${src}] [${z.zoneType.name.padEnd(25)}] " +
                    "${z.name.padEnd(40)} desc=${desc}")
        }

        // ── 7. Display-category summary ────────────────────────────────────────
        val byCategory = finalSet.zones
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
        out.writeBytes(RegulatedZoneSerializer.serialize(finalSet))
        println("[prebake] Wrote ${out.length()} bytes -> ${out.path}")

        // ── 9. Breakdown by zone type ─────────────────────────────────────────
        val byType = finalSet.zones.groupBy { it.zoneType }
        println("[prebake] Breakdown by zone type:")
        for ((type, list) in byType.entries.sortedByDescending { it.value.size }) {
            println("         ${type.name.padEnd(30)} ${list.size}")
        }
        println("[prebake] Done.")
    }

    /**
     * Parse `regulatedZone.filteredTypes` from the shipped `maro.properties` — the
     * comma-separated, case-insensitive enum-name list the bake's hard drop reads.
     * A missing file or key yields an empty set, so the bake drops nothing.
     */
    private fun readFilteredTypes(repoDir: File): Set<RegulatedZoneType> {
        val propsFile = File(repoDir, "app/src/main/assets/maro.properties")
        if (!propsFile.exists()) return emptySet()
        val props = Properties()
        propsFile.inputStream().use { props.load(it) }
        return props.getProperty("regulatedZone.filteredTypes").orEmpty()
            .split(',')
            .mapNotNull { token ->
                val name = token.trim()
                if (name.isEmpty()) null
                else RegulatedZoneType.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            }
            .toSet()
    }
}
