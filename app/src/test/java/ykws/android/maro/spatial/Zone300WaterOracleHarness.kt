package ykws.android.maro.spatial

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import ykws.android.maro.data.coastline.CoastlineGenerator
import ykws.android.maro.data.depth.raster.EmodnetRestClient

/**
 * MANUAL, ONLINE oracle check for the water/land classifier ([CoastlineSpatialIndex.isWater]).
 *
 * `memory/zone300-land-mirror.md` (and the Zone300 feature todo) require that the
 * land-mirror fix be validated against **real OSM data AND an independent oracle**, not
 * just synthetic unit tests — those have passed before while the real coast still
 * mirrored. This harness is that check, made repeatable:
 *
 *  1. fetch the real Nice–Fréjus coastline live ([CoastlineGenerator.generate]);
 *  2. build the spatial index over it;
 *  3. walk a grid through the 300 m band ribbon;
 *  4. for each near-coast point compare `index.isWater(p)` to EMODnet bathymetry
 *     ([EmodnetRestClient.depthSample] > 0 ⇒ water), the independent oracle.
 *
 * It prints the agreement rate and, crucially, the **land-mirror** disagreements
 * (classifier = water, EMODnet = land) — the exact signature of a band painted on land.
 *
 * @Ignore by design: it needs network and takes a few minutes. Run it explicitly:
 *   ./gradlew :app:testDebugUnitTest --tests "*Zone300WaterOracleHarness*" -Dharness=on
 * (remove @Ignore or use the gradle `--tests` filter with your IDE). EMODnet is ≈115 m
 * coarse near shore, so a handful of disagreements within ~120 m of the coast are
 * expected; the assertion therefore only bounds the mirror rate for points ≥ [OFFSET_M].
 */
@Ignore("Manual online oracle check — requires network (Overpass + EMODnet); run explicitly.")
class Zone300WaterOracleHarness {

    private companion object {
        const val STEP_DEG = 0.0025          // ~280 m grid
        const val NEAR_MIN_M = 40.0          // skip the coastline itself
        const val NEAR_MAX_M = 450.0         // band ribbon (+ margin)
        const val OFFSET_M = 120.0           // beyond EMODnet's near-shore coarseness
        const val MAX_MIRROR_RATE = 0.02     // ≤2% land-mirror beyond OFFSET_M (calibrate on first run)
        const val THROTTLE_MS = 40L          // be polite to the EMODnet endpoint
    }

    @Test
    fun `isWater agrees with EMODnet bathymetry along the real coast`() = runBlocking {
        val data = CoastlineGenerator().generate()
        val index = CoastlineSpatialIndex(data.allSegments)
        val emodnet = EmodnetRestClient()
        val bb = data.boundingBox

        var checked = 0
        var agree = 0
        var mirror = 0            // classifier=water, oracle=land (band-on-land)
        var mirrorBeyondOffset = 0
        var beyondOffset = 0
        val mirrorSamples = ArrayList<String>()

        var lat = bb.latSouth
        while (lat <= bb.latNorth) {
            var lon = maxOf(bb.lonWest, CoastlineGenerator.LON_WEST)
            val lonEnd = minOf(bb.lonEast, CoastlineGenerator.LON_EAST)
            while (lon <= lonEnd) {
                val d = index.query(lat, lon).distanceMeters
                if (d in NEAR_MIN_M..NEAR_MAX_M) {
                    val depth = emodnet.depthSample(lat, lon)   // positive-down; null = no data
                    if (depth != null) {
                        val classifierWater = index.isWater(lat, lon)
                        val oracleWater = depth > 0.0
                        checked++
                        if (classifierWater == oracleWater) agree++
                        if (classifierWater && !oracleWater) {
                            mirror++
                            if (d >= OFFSET_M) mirrorBeyondOffset++
                            if (mirrorSamples.size < 25)
                                mirrorSamples.add("  MIRROR lat=%.4f lon=%.4f d=%.0fm elev=%.1fm".format(lat, lon, d, -depth))
                        }
                        if (d >= OFFSET_M) beyondOffset++
                        Thread.sleep(THROTTLE_MS)
                    }
                }
                lon += STEP_DEG
            }
            lat += STEP_DEG
        }

        val agreePct = if (checked > 0) 100.0 * agree / checked else 0.0
        val mirrorRate = if (beyondOffset > 0) mirrorBeyondOffset.toDouble() / beyondOffset else 0.0
        println("─".repeat(64))
        println("Zone300 water/land oracle check (EMODnet bathymetry)")
        println("  near-coast points checked : $checked")
        println("  agreement                 : %.1f%% ($agree/$checked)".format(agreePct))
        println("  land-mirror (water≠land)  : $mirror total, $mirrorBeyondOffset beyond ${OFFSET_M.toInt()}m")
        println("  mirror rate beyond offset : %.2f%% (limit %.0f%%)".format(100 * mirrorRate, 100 * MAX_MIRROR_RATE))
        if (mirrorSamples.isNotEmpty()) {
            println("  sample mirror points (verify on a chart):")
            mirrorSamples.forEach(::println)
        }
        println("─".repeat(64))

        assertTrue("no near-coast points sampled — is the coastline loaded?", checked > 100)
        assertTrue(
            "land-mirror rate %.2f%% beyond ${OFFSET_M.toInt()}m exceeds %.0f%% — band is on land".format(
                100 * mirrorRate, 100 * MAX_MIRROR_RATE
            ),
            mirrorRate <= MAX_MIRROR_RATE
        )
    }
}
