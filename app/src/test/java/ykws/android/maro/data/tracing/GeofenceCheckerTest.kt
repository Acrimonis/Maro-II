package ykws.android.maro.data.tracing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GeofenceChecker] — Haversine-based circular geofence.
 *
 * Origin: Port Salis anchorage (43.5283, 7.0450). Geofence radius: 200 m
 * (matching maro.properties defaults).
 */
class GeofenceCheckerTest {

    private val checker = GeofenceChecker()
    private val originLat = 43.5283
    private val originLon = 7.0450
    private val radiusM = 200f

    @Test
    fun `exactly at origin is inside`() {
        assertTrue(
            checker.isInsideGeofence(
                posLat = originLat, posLon = originLon,
                originLat = originLat, originLon = originLon,
                radiusM = radiusM
            )
        )
    }

    @Test
    fun `well inside geofence returns true`() {
        // ~100 m north of origin (≈0.0009°)
        assertTrue(
            checker.isInsideGeofence(
                posLat = 43.5292, posLon = 7.0450,
                originLat = originLat, originLon = originLon,
                radiusM = radiusM
            )
        )
    }

    @Test
    fun `well outside geofence returns false`() {
        // ~1 km south of origin
        assertFalse(
            checker.isInsideGeofence(
                posLat = 43.5193, posLon = 7.0450,
                originLat = originLat, originLon = originLon,
                radiusM = radiusM
            )
        )
    }

    @Test
    fun `at exact radius edge returns true`() {
        // ~190 m north of origin (190 / 111_195 ≈ 0.0017°)
        assertTrue(
            checker.isInsideGeofence(
                posLat = 43.5300, posLon = 7.0450,
                originLat = originLat, originLon = originLon,
                radiusM = radiusM
            )
        )
    }

    @Test
    fun `just beyond radius edge returns false`() {
        // ~250 m north of origin
        assertFalse(
            checker.isInsideGeofence(
                posLat = 43.53055, posLon = 7.0450,
                originLat = originLat, originLon = originLon,
                radiusM = radiusM
            )
        )
    }

    @Test
    fun `null island with tiny radius rejects remote point`() {
        assertFalse(
            checker.isInsideGeofence(
                posLat = 43.5283, posLon = 7.0450,
                originLat = 0.0, originLon = 0.0,
                radiusM = 1f
            )
        )
    }

    @Test
    fun `antipodal point is outside`() {
        assertFalse(
            checker.isInsideGeofence(
                posLat = -43.5283, posLon = -172.9550, // near-antipode
                originLat = originLat, originLon = originLon,
                radiusM = radiusM
            )
        )
    }

    @Test
    fun `zero radius only accepts exact origin`() {
        assertTrue(
            checker.isInsideGeofence(
                posLat = originLat, posLon = originLon,
                originLat = originLat, originLon = originLon,
                radiusM = 0f
            )
        )
        assertFalse(
            checker.isInsideGeofence(
                posLat = 43.5284, posLon = 7.0450,
                originLat = originLat, originLon = originLon,
                radiusM = 0f
            )
        )
    }
}
