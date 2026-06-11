package ykws.android.maro.data.regulation

import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import java.io.File

/**
 * **Build-time prebake — NOT a unit test.** Fetches regulation zones from SHOM WFS,
 * aggregates with hardcoded seed zones, deduplicates, and writes the cooked `.bin`
 * to `assets/regulated-zones/<region>.bin` for bundling into the APK.
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

        // ── 2. Add seed fallback zones ────────────────────────────────────────
        val seeds = RegulationSeeds.getSeeds()
        println("[prebake] ${seeds.size} seed zones")

        // ── 3. Aggregate (dedup SHOM + seeds) ─────────────────────────────────
        val zoneSet = RegulationAggregator.aggregate(
            shomZones = shomZones,
            seedZones = seeds,
            bbox = bbox
        )
        println("[prebake] ${zoneSet.metadata.totalZones} zones after dedup " +
                "(${zoneSet.metadata.sourceCount} sources)")

        // ── 4. Serialize to .bin ──────────────────────────────────────────────
        val out = File(outputDir, "$region.bin")
        out.writeBytes(RegulatedZoneSerializer.serialize(zoneSet))
        println("[prebake] Wrote ${out.length()} bytes -> ${out.path}")

        // ── 5. Summary ────────────────────────────────────────────────────────
        val byType = zoneSet.zones.groupBy { it.zoneType }
        println("[prebake] Breakdown by zone type:")
        for ((type, list) in byType.entries.sortedByDescending { it.value.size }) {
            println("         ${type.name.padEnd(30)} ${list.size}")
        }
        println("[prebake] Done.")
    }
}
