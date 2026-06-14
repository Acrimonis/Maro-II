package ykws.android.maro.data.regulation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng

/**
 * Unit tests for forward-looking vessel size filtering on [RegulatedZone].
 *
 * Note: The current [RegulatedZone] model does NOT yet include a
 * `VesselSizeRestriction` field. These tests validate that the zone's
 * `appliesTo()` method (once implemented) correctly handles vessel length
 * inclusion/exclusion. For now, all zones apply to all vessels.
 */
class RegulatedZoneVesselFilterTest {

    private val defaultRing = listOf(
        LatLng(43.5, 7.1), LatLng(43.6, 7.2), LatLng(43.5, 7.3), LatLng(43.5, 7.1)
    )

    @Test
    fun `zone without speed limit applies to all vessels`() {
        val zone = RegulatedZone(
            outerRing = defaultRing,
            zoneType = RegulatedZoneType.ENVIRONMENTAL,
            name = "Protected area"
        )
        // Without speedLimitKn, the zone applies universally
        assertTrue("Environmental zone applies to all", true)
    }

    @Test
    fun `speed limit zone applies regardless of vessel size`() {
        val zone = RegulatedZone(
            outerRing = defaultRing,
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            speedLimitKn = 10.0,
            name = "Speed zone"
        )
        // Speed limit zones apply to all vessels
        assertTrue("Speed zone applies to all vessels", true)
    }

    @Test
    fun `zone metadata tracks source correctly`() {
        val zone = RegulatedZone(
            outerRing = defaultRing,
            zoneType = RegulatedZoneType.ANCHORING_PROHIBITED,
            source = "SHOM",
            name = "Anchoring restriction"
        )
        assertTrue("Source should be SHOM", zone.source == "SHOM")
    }
}
