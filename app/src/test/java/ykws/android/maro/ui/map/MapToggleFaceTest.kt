package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.track.TrackRecorderState
import ykws.android.maro.data.track.TrackRecorderUiState

/**
 * Unit tests for the row's colour normalization — one resolved [TopToggleFace] per square per state.
 *
 * Each square's two channels are pinned here: the tile's fill and the data mark's dot. The resolvers are
 * pure functions of a state and the palette (no Compose call), so this test reaches them directly, and
 * the composables that paint them — `MapControls`, `TrackStatusIcon`, `InspectMode`, `RouteOverlay` —
 * carry no state table of their own to fall out of step.
 */
class MapToggleFaceTest {

    // ── GPS ──────────────────────────────────────────────────────────────────

    @Test
    fun gpsDemoIsOff() {
        assertEquals(TopToggleFace.OFF, gpsFace(GpsIconState.DEMO))
    }

    @Test
    fun gpsAcquiringAndWeakWearTheAcquiringAmberWithARedDot() {
        val expected = TopToggleFace(AppConfig.statusGpsAcquiring, AppConfig.semanticDanger)
        assertEquals(expected, gpsFace(GpsIconState.ACQUIRING))
        assertEquals(expected, gpsFace(GpsIconState.WEAK))
    }

    @Test
    fun gpsEstimatingWearsTheAcquiringAmberWithARedDot() {
        assertEquals(
            TopToggleFace(AppConfig.statusGpsEstimating, AppConfig.semanticDanger),
            gpsFace(GpsIconState.ESTIMATING)
        )
    }

    @Test
    fun gpsHealthyAndIdleBothWearTheNominalBlueWithAGreenDot() {
        assertEquals(
            TopToggleFace(AppConfig.statusGpsHealthy, AppConfig.semanticCompliant),
            gpsFace(GpsIconState.HEALTHY)
        )
        assertEquals(
            TopToggleFace(AppConfig.statusGpsIdle, AppConfig.semanticCompliant),
            gpsFace(GpsIconState.IDLE)
        )
    }

    @Test
    fun gpsStaleWearsTheHazardRedWithARedDot() {
        assertEquals(
            TopToggleFace(AppConfig.statusGpsStale, AppConfig.semanticDanger),
            gpsFace(GpsIconState.STALE)
        )
    }

    // ── Tracking ─────────────────────────────────────────────────────────────

    @Test
    fun trackingOffIsFlat() {
        assertEquals(
            TopToggleFace.OFF,
            trackingFace(TrackRecorderUiState(state = TrackRecorderState.OFF))
        )
    }

    @Test
    fun trackingRecordingIsBlueWithAGreenDot() {
        assertEquals(
            TopToggleFace(AppConfig.statusTrackingRecording, AppConfig.semanticCompliant),
            trackingFace(TrackRecorderUiState(state = TrackRecorderState.ON, isMoving = true))
        )
    }

    @Test
    fun trackingStandingByIsGreenWithAGreenDot() {
        assertEquals(
            TopToggleFace(AppConfig.statusTrackingIdle, AppConfig.semanticCompliant),
            trackingFace(TrackRecorderUiState(state = TrackRecorderState.ON, isMoving = false))
        )
    }

    // ── Land / water ─────────────────────────────────────────────────────────

    @Test
    fun waterIsBlueAndLandIsRedAndNeitherIsDotted() {
        assertEquals(
            TopToggleFace(AppConfig.statusEarthWaterWater, null),
            earthWaterFace(isWater = true)
        )
        assertEquals(
            TopToggleFace(AppConfig.statusEarthWaterLand, null),
            earthWaterFace(isWater = false)
        )
    }

    // ── Inspect ──────────────────────────────────────────────────────────────

    @Test
    fun inspectOffIsFlatAndArmedIsBlueWithAGreenDot() {
        assertEquals(TopToggleFace.OFF, inspectFace(armed = false))
        assertEquals(
            TopToggleFace(AppConfig.uiAccent, AppConfig.semanticCompliant),
            inspectFace(armed = true)
        )
    }

    // ── Route ────────────────────────────────────────────────────────────────

    @Test
    fun routeOffIsFlat() {
        assertEquals(
            TopToggleFace.OFF,
            routeFace(armed = false, following = false, searching = false)
        )
    }

    @Test
    fun routeFollowingIsBlueWithAGreenDot() {
        assertEquals(
            TopToggleFace(AppConfig.routeNavigateColor, AppConfig.semanticCompliant),
            routeFace(armed = true, following = true, searching = false)
        )
    }

    @Test
    fun routeSearchingIsAmberWithAnAmberDot() {
        assertEquals(
            TopToggleFace(AppConfig.semanticCaution, AppConfig.semanticCaution),
            routeFace(armed = true, following = false, searching = true)
        )
    }

    @Test
    fun routeArmedAndSettledIsAmberWithACompleteGreenDot() {
        assertEquals(
            TopToggleFace(AppConfig.semanticCaution, AppConfig.semanticCompliant),
            routeFace(armed = true, following = false, searching = false)
        )
    }

    // ── Lock ─────────────────────────────────────────────────────────────────

    @Test
    fun lockOffIsFlatAndLockedIsBlueWithAGreenDot() {
        assertEquals(TopToggleFace.OFF, lockFace(locked = false))
        assertEquals(
            TopToggleFace(AppConfig.statusLockOn, AppConfig.semanticCompliant),
            lockFace(locked = true)
        )
    }

    // ── Recenter ─────────────────────────────────────────────────────────────

    @Test
    fun recenterIsBlueWithNoDot() {
        assertEquals(TopToggleFace(AppConfig.uiAccent, null), recenterFace())
    }

    // ── The off square ───────────────────────────────────────────────────────

    @Test
    fun theOffFaceCarriesNoFillAndNoDot() {
        assertNull(TopToggleFace.OFF.fill)
        assertNull(TopToggleFace.OFF.dot)
    }
}
