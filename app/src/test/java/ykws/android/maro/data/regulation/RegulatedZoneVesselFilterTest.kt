package ykws.android.maro.data.regulation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng

/**
 * Unit tests for [RegulatedZone.appliesTo] — vessel size filtering logic.
 */
class RegulatedZoneVesselFilterTest {

    private val defaultRing = listOf(
        LatLng(43.5, 7.1), LatLng(43.6, 7.2), LatLng(43.5, 7.3), LatLng(43.5, 7.1)
    )

    @Test
    fun `no restriction applies to all vessels`() {
        val zone = RegulatedZone(
            outerRing = defaultRing,
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            vesselSizeRestriction = null
        )
        assertTrue(zone.appliesTo(6.0))
        assertTrue(zone.appliesTo(25.0))
        assertTrue(zone.appliesTo(50.0))
    }

    @Test
    fun `minLengthM excludes smaller vessels`() {
        val zone = RegulatedZone(
            outerRing = defaultRing,
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            vesselSizeRestriction = VesselSizeRestriction(minLengthM = 25.0)
        )
        assertFalse("6m boat should be excluded", zone.appliesTo(6.0))
        assertFalse("20m boat should be excluded", zone.appliesTo(20.0))
        assertTrue("25m boat should be included (inclusive)", zone.appliesTo(25.0))
        assertTrue("30m boat should be included", zone.appliesTo(30.0))
    }

    @Test
    fun `maxLengthM excludes larger vessels`() {
        val zone = RegulatedZone(
            outerRing = defaultRing,
            zoneType = RegulatedZoneType.ANCHORING_PROHIBITED,
            vesselSizeRestriction = VesselSizeRestriction(maxLengthM = 20.0)
        )
        assertTrue("6m boat should be included", zone.appliesTo(6.0))
        assertTrue("20m boat should be included (inclusive)", zone.appliesTo(20.0))
        assertFalse("25m boat should be excluded", zone.appliesTo(25.0))
        assertFalse("50m boat should be excluded", zone.appliesTo(50.0))
    }

    @Test
    fun `both min and max define a range`() {
        val zone = RegulatedZone(
            outerRing = defaultRing,
            zoneType = RegulatedZoneType.ACCESS_PROHIBITED,
            vesselSizeRestriction = VesselSizeRestriction(minLengthM = 10.0, maxLengthM = 25.0)
        )
        assertFalse("5m boat below range", zone.appliesTo(5.0))
        assertTrue("10m boat at lower bound", zone.appliesTo(10.0))
        assertTrue("15m boat inside range", zone.appliesTo(15.0))
        assertTrue("25m boat at upper bound", zone.appliesTo(25.0))
        assertFalse("30m boat above range", zone.appliesTo(30.0))
    }

    @Test
    fun `zero-length vessel is handled`() {
        val zone = RegulatedZone(
            outerRing = defaultRing,
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            vesselSizeRestriction = VesselSizeRestriction(minLengthM = 1.0)
        )
        assertFalse("0m boat below min", zone.appliesTo(0.0))
    }

    @Test
    fun `negative values do not crash`() {
        val zone = RegulatedZone(
            outerRing = defaultRing,
            zoneType = RegulatedZoneType.OTHER,
            vesselSizeRestriction = VesselSizeRestriction(minLengthM = -5.0, maxLengthM = -1.0)
        )
        // Negative restriction is unusual but should not crash
        assertFalse("6m boat above negative max", zone.appliesTo(6.0))
        assertTrue("-3m boat inside negative range", zone.appliesTo(-3.0))
    }
}
