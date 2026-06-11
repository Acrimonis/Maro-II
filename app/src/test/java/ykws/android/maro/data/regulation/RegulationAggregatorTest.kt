package ykws.android.maro.data.regulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng

/**
 * Unit tests for [RegulationAggregator] — deduplication and metadata generation.
 */
class RegulationAggregatorTest {

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
        val result = RegulationAggregator.aggregate(listOf(zone))
        assertEquals(1, result.metadata.totalZones)
        assertEquals("Only zone", result.zones.single().name)
    }

    @Test
    fun `dedup removes seed within 100m of SHOM zone with same type`() {
        val shom = RegulatedZone(
            outerRing = ringAround(43.56, 7.13),
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            name = "SHOM zone",
            source = "SHOM"
        )
        val seed = RegulatedZone(
            outerRing = ringAround(43.5605, 7.1305), // ~55 m away, well within 100 m
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            name = "Seed zone",
            source = "SEED"
        )
        val result = RegulationAggregator.aggregate(listOf(shom, seed))
        assertEquals(1, result.metadata.totalZones)
        assertEquals("SHOM zone", result.zones.single().name) // SHOM wins priority
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
            source = "SHOM"
        )
        val result = RegulationAggregator.aggregate(listOf(zoneA, zoneB))
        assertEquals(2, result.metadata.totalZones)
    }

    @Test
    fun `keeps zones of different types even when close`() {
        val speed = RegulatedZone(
            outerRing = ringAround(43.56, 7.13),
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            name = "Speed zone",
            source = "SHOM"
        )
        val anchor = RegulatedZone(
            outerRing = ringAround(43.5605, 7.1305), // same location
            zoneType = RegulatedZoneType.ANCHORING_PROHIBITED, // different type
            name = "Anchor zone",
            source = "SHOM"
        )
        val result = RegulationAggregator.aggregate(listOf(speed, anchor))
        assertEquals(2, result.metadata.totalZones)
    }

    @Test
    fun `empty input returns empty set with metadata`() {
        val result = RegulationAggregator.aggregate(emptyList())
        assertTrue(result.zones.isEmpty())
        assertEquals(0, result.metadata.totalZones)
        assertEquals(0, result.metadata.sourceCount)
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
        val result = RegulationAggregator.aggregate(listOf(shomZone, seedZone))
        assertEquals(2, result.metadata.sourceCount)
        assertEquals(2, result.metadata.totalZones)
        assertEquals("nice-frejus", result.metadata.regionId)
    }

    @Test
    fun `metadata timestamp is set`() {
        val before = System.currentTimeMillis()
        val result = RegulationAggregator.aggregate(
            listOf(
                RegulatedZone(
                    outerRing = ringAround(43.56, 7.13),
                    zoneType = RegulatedZoneType.OTHER,
                    source = "SHOM"
                )
            )
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
        val result = RegulationAggregator.aggregate(listOf(osm, seed, shom))
        assertEquals(1, result.metadata.totalZones)
        assertEquals("Authoritative", result.zones.single().name) // SHOM highest priority
    }
}
