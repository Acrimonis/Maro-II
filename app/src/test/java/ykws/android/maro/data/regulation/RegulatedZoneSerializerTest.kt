package ykws.android.maro.data.regulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ykws.android.maro.data.model.LatLng

/**
 * Unit tests for [RegulatedZoneSerializer] — validates Protobuf roundtrip.
 */
class RegulatedZoneSerializerTest {

    @Test
    fun `roundtrip preserves all fields`() {
        val original = RegulatedZoneSet(
            zones = listOf(
                RegulatedZone(
                    outerRing = listOf(LatLng(43.5, 7.1), LatLng(43.6, 7.2), LatLng(43.5, 7.3), LatLng(43.5, 7.1)),
                    holes = listOf(
                        listOf(LatLng(43.52, 7.15), LatLng(43.53, 7.16), LatLng(43.52, 7.17), LatLng(43.52, 7.15))
                    ),
                    zoneType = RegulatedZoneType.SPEED_LIMIT,
                    speedLimitKn = 10.0,
                    name = "Test Zone",
                    source = "SHOM",
                    sourceRef = "REF-001",
                    description = "A test zone",
                    vesselSizeRestriction = VesselSizeRestriction(minLengthM = 25.0)
                )
            ),
            metadata = RegulationMetadata(
                regionId = "test-region",
                fetchTimestampMs = 1000L,
                sourceCount = 1,
                totalZones = 1
            )
        )

        val bytes = RegulatedZoneSerializer.serialize(original)
        val restored = RegulatedZoneSerializer.deserialize(bytes)

        assertEquals(original, restored)
    }

    @Test
    fun `roundtrip with empty zones`() {
        val original = RegulatedZoneSet(
            zones = emptyList(),
            metadata = RegulationMetadata(
                fetchTimestampMs = 0L,
                sourceCount = 0,
                totalZones = 0
            )
        )

        val bytes = RegulatedZoneSerializer.serialize(original)
        val restored = RegulatedZoneSerializer.deserialize(bytes)

        assertEquals(original, restored)
    }

    @Test
    fun `roundtrip with null vesselSizeRestriction`() {
        val original = RegulatedZoneSet(
            zones = listOf(
                RegulatedZone(
                    outerRing = listOf(LatLng(43.5, 7.1), LatLng(43.6, 7.2), LatLng(43.5, 7.3), LatLng(43.5, 7.1)),
                    zoneType = RegulatedZoneType.ANCHORING_PROHIBITED,
                    vesselSizeRestriction = null
                )
            ),
            metadata = RegulationMetadata(
                fetchTimestampMs = 2000L,
                sourceCount = 1,
                totalZones = 1
            )
        )

        val bytes = RegulatedZoneSerializer.serialize(original)
        val restored = RegulatedZoneSerializer.deserialize(bytes)

        assertNull(restored.zones.single().vesselSizeRestriction)
        assertEquals(original, restored)
    }

    @Test
    fun `roundtrip with full vesselSizeRestriction`() {
        val original = RegulatedZoneSet(
            zones = listOf(
                RegulatedZone(
                    outerRing = listOf(LatLng(43.5, 7.1), LatLng(43.6, 7.2), LatLng(43.5, 7.3), LatLng(43.5, 7.1)),
                    zoneType = RegulatedZoneType.ACCESS_PROHIBITED,
                    vesselSizeRestriction = VesselSizeRestriction(minLengthM = 10.0, maxLengthM = 25.0)
                )
            ),
            metadata = RegulationMetadata(
                fetchTimestampMs = 3000L,
                sourceCount = 1,
                totalZones = 1
            )
        )

        val bytes = RegulatedZoneSerializer.serialize(original)
        val restored = RegulatedZoneSerializer.deserialize(bytes)

        val vsr = restored.zones.single().vesselSizeRestriction
        assertNotNull(vsr)
        assertEquals(10.0, vsr!!.minLengthM!!, 0.001)
        assertEquals(25.0, vsr!!.maxLengthM!!, 0.001)
    }

    @Test
    fun `enum values survive roundtrip`() {
        for (type in RegulatedZoneType.entries) {
            val original = RegulatedZoneSet(
                zones = listOf(
                    RegulatedZone(
                        outerRing = listOf(LatLng(43.5, 7.1), LatLng(43.6, 7.2), LatLng(43.5, 7.3), LatLng(43.5, 7.1)),
                        zoneType = type
                    )
                ),
                metadata = RegulationMetadata(
                    fetchTimestampMs = 0L,
                    sourceCount = 1,
                    totalZones = 1
                )
            )
            val bytes = RegulatedZoneSerializer.serialize(original)
            val restored = RegulatedZoneSerializer.deserialize(bytes)
            assertEquals(type, restored.zones.single().zoneType)
        }
    }
}
