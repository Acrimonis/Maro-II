package ykws.android.maro.data.regulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.LatLng

/**
 * Unit tests for [RegulationAggregator] — deduplication, bbox validation, and metadata generation.
 */
class RegulationAggregatorTest {

    private val testBbox = BoundingBox(
        lonWest = 6.70, latSouth = 43.35,
        lonEast = 7.31, latNorth = 43.73
    )

    /** Helper to build a small triangle ring around a given centroid. */
    private fun ringAround(lat: Double, lon: Double, offset: Double = 0.001): List<LatLng> = listOf(
        LatLng(lat - offset, lon - offset),
        LatLng(lat + offset, lon),
        LatLng(lat - offset, lon + offset),
        LatLng(lat - offset, lon - offset) // closed
    )

    @Test
    fun `single zone returns same zone`() {
        val zone = RegulatedZone(
            outerRing = ringAround(43.56, 7.13),
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            name = "Only zone",
            source = "SHOM"
        )
        val result = RegulationAggregator.aggregate(
            shomZones = listOf(zone),
            seedZones = emptyList(),
            bbox = testBbox
        )
        assertEquals(1, result.metadata.totalZones)
        assertEquals("Only zone", result.zones.single().name)
    }

    @Test
    fun `dedup removes seed whose centroid overlaps SHOM bbox`() {
        val shom = RegulatedZone(
            outerRing = ringAround(43.56, 7.13),
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            name = "SHOM zone",
            source = "SHOM"
        )
        val seed = RegulatedZone(
            outerRing = ringAround(43.5601, 7.1301), // centroid inside SHOM bbox
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            name = "Seed zone",
            source = "SEED"
        )
        val result = RegulationAggregator.aggregate(
            shomZones = listOf(shom),
            seedZones = listOf(seed),
            bbox = testBbox
        )
        assertEquals(1, result.metadata.totalZones)
        assertEquals("SHOM zone", result.zones.single().name) // SHOM authoritative
    }

    @Test
    fun `keeps both zones when centroids are far apart`() {
        val zoneA = RegulatedZone(
            outerRing = ringAround(43.56, 7.13),
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            name = "Zone A",
            source = "SHOM"
        )
        val zoneB = RegulatedZone(
            outerRing = ringAround(43.60, 7.20), // ~8 km away
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            name = "Zone B",
            source = "SEED"
        )
        val result = RegulationAggregator.aggregate(
            shomZones = listOf(zoneA),
            seedZones = listOf(zoneB),
            bbox = testBbox
        )
        assertEquals(2, result.metadata.totalZones)
    }

    @Test
    fun `keeps multiple SHOM zones regardless of type`() {
        val speed = RegulatedZone(
            outerRing = ringAround(43.56, 7.13),
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            name = "Speed zone",
            source = "SHOM"
        )
        val anchor = RegulatedZone(
            outerRing = ringAround(43.5601, 7.1301), // same location
            zoneType = RegulatedZoneType.ANCHORING_PROHIBITED, // different type
            name = "Anchor zone",
            source = "SHOM"
        )
        val result = RegulationAggregator.aggregate(
            shomZones = listOf(speed, anchor),
            seedZones = emptyList(),
            bbox = testBbox
        )
        // Both are SHOM — dedup only discards non-SHOM zones overlapping SHOM bbox
        assertEquals(2, result.metadata.totalZones)
    }

    @Test
    fun `empty input returns empty set with metadata`() {
        val result = RegulationAggregator.aggregate(
            shomZones = emptyList(),
            seedZones = emptyList(),
            bbox = testBbox
        )
        assertTrue(result.zones.isEmpty())
        assertEquals(0, result.metadata.totalZones)
        assertEquals(0, result.metadata.sourceCount)
    }

    @Test
    fun `rejects zone outside bbox`() {
        val inside = RegulatedZone(
            outerRing = ringAround(43.56, 7.13),
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            name = "Inside",
            source = "SHOM"
        )
        val outside = RegulatedZone(
            outerRing = ringAround(44.00, 8.00), // well outside Nice-Fréjus bbox
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            name = "Outside",
            source = "SHOM"
        )
        val result = RegulationAggregator.aggregate(
            shomZones = listOf(inside, outside),
            seedZones = emptyList(),
            bbox = testBbox
        )
        assertEquals(1, result.metadata.totalZones)
        assertEquals("Inside", result.zones.single().name)
    }

    @Test
    fun `metadata contains correct source counts`() {
        val shomZone = RegulatedZone(
            outerRing = ringAround(43.56, 7.13),
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            source = "SHOM"
        )
        val seedZone = RegulatedZone(
            outerRing = ringAround(43.60, 7.20),
            zoneType = RegulatedZoneType.ANCHORING_PROHIBITED,
            source = "SEED"
        )
        val result = RegulationAggregator.aggregate(
            shomZones = listOf(shomZone),
            seedZones = listOf(seedZone),
            bbox = testBbox
        )
        assertEquals(2, result.metadata.sourceCount)
        assertEquals(2, result.metadata.totalZones)
        assertEquals("nice-frejus", result.metadata.regionId)
    }

    @Test
    fun `metadata timestamp is set`() {
        val before = System.currentTimeMillis()
        val result = RegulationAggregator.aggregate(
            shomZones = listOf(
                RegulatedZone(
                    outerRing = ringAround(43.56, 7.13),
                    zoneType = RegulatedZoneType.OTHER,
                    source = "SHOM"
                )
            ),
            seedZones = emptyList(),
            bbox = testBbox
        )
        val after = System.currentTimeMillis()
        assertTrue(result.metadata.fetchTimestampMs in before..after)
    }

    @Test
    fun `SHOM zone wins over SEED for same location same type`() {
        val shom = RegulatedZone(
            outerRing = ringAround(43.56, 7.13),
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            name = "Authoritative",
            source = "SHOM"
        )
        val osm = RegulatedZone(
            outerRing = ringAround(43.56, 7.13), // same centroid
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            name = "Community",
            source = "OSM"
        )
        val seed = RegulatedZone(
            outerRing = ringAround(43.56, 7.13),
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            name = "Fallback",
            source = "SEED"
        )
        val result = RegulationAggregator.aggregate(
            shomZones = listOf(osm, shom),
            seedZones = listOf(seed),
            bbox = testBbox
        )
        assertEquals(1, result.metadata.totalZones)
        assertEquals("Authoritative", result.zones.single().name) // SHOM highest priority
    }
}
