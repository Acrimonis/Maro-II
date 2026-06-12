package ykws.android.maro.data.regulation

import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import java.io.File

/**
 * **Build-time prebake — NOT a unit test.** Fetches regulation zones from SHOM WFS,
 * aggregates with hardcoded seed zones, deduplicates, filters by vessel size and
 * zone type, and writes the cooked `.bin` to `assets/regulated-zones/<region>.bin`
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

        val bbox = BoundingBox(
            lonWest = 6.70, latSouth = 43.35,
            lonEast = 7.31, latNorth = 43.73
        )

        // ── 1. Fetch from SHOM WFS ────────────────────────────────────────────
        println("[prebake] Fetching SHOM regulation zones for $region...")
        val shomZones: List<RegulatedZone> = runBlocking {
            ShomRegulationClient().fetchZones(bbox = bbox)
        }
        println("[prebake] SHOM returned ${shomZones.size} zones")

        // ── 2. No seed zones — SHOM data only ──────────────────────────────
        println("[prebake] 0 seed zones (SHOM-only mode)")

        // ── 3. Wrapped zone set (no dedup needed, no seeds) ─────────────────
        val nowMs = System.currentTimeMillis()
        val metadata = RegulationMetadata(
            fetchTimestampMs = nowMs,
            sourceCount = 1,
            totalZones = shomZones.size
        )
        val zoneSet = RegulatedZoneSet(zones = shomZones, metadata = metadata)
        println("[prebake] ${zoneSet.metadata.totalZones} zones (no filtering)")

        // ── 4. Per-zone detail dump ───────────────────────────────────────────
        println("[prebake] Per-zone details (full descriptions):")
        for ((i, z) in zoneSet.zones.withIndex()) {
            val desc = z.description.replace("\n", " | ")
            println("         #${i + 1} [${z.restrictionCode?.toString()?.padEnd(4) ?: "null"}] " +
                    "[${z.zoneType.name.padEnd(25)}] " +
                    "${z.name.padEnd(40)} desc=${desc}")
        }

        // ── 5. No filtering — raw SHOM data ─────────────────────────────────
        val filtered = zoneSet
        println("[prebake] Filter disabled — using all ${filtered.metadata.totalZones} zones")

        // ── 6. Display-category summary ───────────────────────────────────────
        val byCategory = filtered.zones
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

        // ── 7. Serialize to .bin ──────────────────────────────────────────────
        val out = File(outputDir, "$region.bin")
        out.writeBytes(RegulatedZoneSerializer.serialize(filtered))
        println("[prebake] Wrote ${out.length()} bytes -> ${out.path}")

        // ── 8. Summary ────────────────────────────────────────────────────────
        val byType = filtered.zones.groupBy { it.zoneType }
        println("[prebake] Breakdown by zone type:")
        for ((type, list) in byType.entries.sortedByDescending { it.value.size }) {
            println("         ${type.name.padEnd(30)} ${list.size}")
        }
        println("[prebake] Done.")
    }
}
