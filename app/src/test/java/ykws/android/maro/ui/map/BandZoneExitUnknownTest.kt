package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 300 m band as the zone the boat is in when no exit lies along the heading
 * ([bandZoneExitUnknown]) — the harbour-approach case, heading landward.
 */
class BandZoneExitUnknownTest {

    @Test
    fun `the band stands with its limit and an exit that is infinitely far`() {
        val zone = bandZoneExitUnknown(sogKn = 3f, limitKn = 5.0)

        assertEquals("BANDE 300M", zone.zoneName)
        assertEquals(5.0, zone.speedLimitKn, 0.0)
        assertEquals(Double.POSITIVE_INFINITY, zone.distanceM, 0.0)
        assertEquals(BeyondType.UNKNOWN, zone.beyondType)
    }

    @Test
    fun `no exit means no ETA and no beyond name`() {
        val zone = bandZoneExitUnknown(sogKn = 3f, limitKn = 5.0)

        assertNull(zone.etaSeconds)
        assertNull(zone.beyondName)
        assertNull(zone.boundaryPosition)
    }

    @Test
    fun `compliance reads the boat's speed against the limit`() {
        assertTrue(bandZoneExitUnknown(sogKn = 4.9f, limitKn = 5.0).isCompliant)
        assertFalse(bandZoneExitUnknown(sogKn = 5.0f, limitKn = 5.0).isCompliant)
        assertFalse(bandZoneExitUnknown(sogKn = 12f, limitKn = 5.0).isCompliant)
    }

    @Test
    fun `an unknown speed is compliant`() {
        val zone = bandZoneExitUnknown(sogKn = null, limitKn = 5.0)

        assertTrue(zone.isCompliant)
        assertNull(zone.currentSpeedKnots)
    }
}
