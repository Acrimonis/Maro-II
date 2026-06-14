package ykws.android.maro.data.regulation

import org.junit.Assert.*
import org.junit.Test
import ykws.android.maro.data.model.LatLng

/**
 * Unit tests for [SpeedZoneBuilder] — filtering and transformation of [RegulatedZoneSet] to [SpeedZone] list.
 */
class SpeedZoneBuilderTest {

    private val sampleOuterRing = listOf(
        LatLng(43.55, 7.10),
        LatLng(43.55, 7.12),
        LatLng(43.54, 7.12),
        LatLng(43.54, 7.10),
        LatLng(43.55, 7.10)
    )

    private val sampleHoles = listOf(listOf(
        LatLng(43.547, 7.107),
        LatLng(43.547, 7.113),
        LatLng(43.544, 7.113),
        LatLng(43.544, 7.107),
        LatLng(43.547, 7.107)
    ))

    private fun makeZone(
        name: String = "Test Zone",
        speedLimitKn: Double? = 10.0,
        sourceRef: String = "SRC-001",
        source: String = "SHOM",
        zoneType: RegulatedZoneType = RegulatedZoneType.SPEED_LIMIT
    ) = RegulatedZone(
        outerRing = sampleOuterRing,
        holes = emptyList(),
        zoneType = zoneType,
        speedLimitKn = speedLimitKn,
        name = name,
        source = source,
        sourceRef = sourceRef,
        description = "Test zone",
        restrictionCode = null,
        vesselSizeRestriction = null,
        classification = null,
        speedSource = null,
        legalDecreeRef = null
    )

    private fun makeZoneSet(zones: List<RegulatedZone>) = RegulatedZoneSet(
        zones = zones,
        metadata = RegulationMetadata(
            regionId = "nice-frejus",
            fetchTimestampMs = 0L,
            sourceCount = zones.distinctBy { it.source }.size.coerceAtLeast(1),
            totalZones = zones.size
        )
    )

    // ── Edge cases ──────────────────────────────────────────────────────

    @Test
    fun `build from null returns empty`() {
        val result = SpeedZoneBuilder.build(null)
        assertTrue("null input should yield empty list", result.isEmpty())
    }

    @Test
    fun `build from empty zone set returns empty`() {
        val result = SpeedZoneBuilder.build(makeZoneSet(emptyList()))
        assertTrue("empty zone set should yield empty list", result.isEmpty())
    }

    @Test
    fun `build skips zones without speed limit`() {
        val zones = listOf(
            makeZone(name = "Speed Zone", speedLimitKn = 10.0),
            makeZone(name = "Anchoring Zone", speedLimitKn = null, zoneType = RegulatedZoneType.ANCHORING_PROHIBITED)
        )
        val result = SpeedZoneBuilder.build(makeZoneSet(zones))
        assertEquals("should include only the speed zone", 1, result.size)
        assertEquals("Speed Zone", result[0].name)
    }

    @Test
    fun `build skips all non-speed zones`() {
        val zones = listOf(
            makeZone(name = "Env Zone", speedLimitKn = null, zoneType = RegulatedZoneType.ENVIRONMENTAL),
            makeZone(name = "Anchor Zone", speedLimitKn = null, zoneType = RegulatedZoneType.ANCHORING_PROHIBITED)
        )
        val result = SpeedZoneBuilder.build(makeZoneSet(zones))
        assertTrue("no speed zones should yield empty list", result.isEmpty())
    }

    // ── Data mapping ────────────────────────────────────────────────────

    @Test
    fun `build maps fields correctly`() {
        val zones = listOf(
            makeZone(
                name = "Cap d'Antibes",
                speedLimitKn = 10.0,
                sourceRef = "SHOM-123",
                source = "SHOM"
            )
        )
        val result = SpeedZoneBuilder.build(makeZoneSet(zones))
        assertEquals(1, result.size)
        with(result[0]) {
            assertEquals("SHOM-123", id)
            assertEquals("Cap d'Antibes", name)
            assertEquals(10.0, speedLimitKn, 0.0)
            assertEquals(sampleOuterRing, outerRing)
            assertTrue(holes.isEmpty())
            assertEquals("SHOM", source)
        }
    }

    @Test
    fun `build uses name as id fallback when sourceRef is blank`() {
        val zones = listOf(
            makeZone(name = "Zone Alpha", speedLimitKn = 8.0, sourceRef = "")
        )
        val result = SpeedZoneBuilder.build(makeZoneSet(zones))
        assertEquals("Zone Alpha", result[0].id)
    }

    @Test
    fun `build uses speed-kn name fallback when name is blank`() {
        val zones = listOf(
            makeZone(name = "", speedLimitKn = 6.0, sourceRef = "X-1",
                zoneType = RegulatedZoneType.SPEED_LIMIT)
        )
        val result = SpeedZoneBuilder.build(makeZoneSet(zones))
        assertEquals("X-1", result[0].id)
        assertEquals("Zone 6 kn", result[0].name)
    }

    @Test
    fun `build preserves holes`() {
        val zones = listOf(
            RegulatedZone(
                outerRing = sampleOuterRing,
                holes = sampleHoles,
                zoneType = RegulatedZoneType.SPEED_LIMIT,
                speedLimitKn = 5.0,
                name = "With Hole",
                source = "SHOM",
                sourceRef = "H-1",
                description = "",
                restrictionCode = null,
                vesselSizeRestriction = null,
                classification = null,
                speedSource = null,
                legalDecreeRef = null
            )
        )
        val result = SpeedZoneBuilder.build(makeZoneSet(zones))
        assertEquals(1, result.size)
        assertEquals(sampleHoles, result[0].holes)
    }

    @Test
    fun `build handles multiple speed zones`() {
        val zones = (1..5).map { i ->
            makeZone(name = "Zone $i", speedLimitKn = i * 2.0, sourceRef = "SRC-$i")
        }
        val result = SpeedZoneBuilder.build(makeZoneSet(zones))
        assertEquals(5, result.size)
        // Verify the most restrictive
        val sortedBySpeed = result.sortedBy { it.speedLimitKn }
        assertEquals(2.0, sortedBySpeed[0].speedLimitKn, 0.0)
        assertEquals(10.0, sortedBySpeed[4].speedLimitKn, 0.0)
    }
}
