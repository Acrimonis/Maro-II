package ykws.android.maro.spatial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.MarkerOrigin
import ykws.android.maro.data.model.markers.UserMarker

/**
 * The date-point ruling of 2026-09-28 (Markers walk item 12): a 🕐 idle pin is tested whichever
 * way its range gate answers, so the 300 m rule decides the match and never the drawing.
 *
 * The in-range case is asserted with the quiet debugger on purpose — that path logs through
 * `android.util.Log`, which the unit-test classpath does not provide, so the branch this ruling
 * changed is the one exercised with the recording one.
 */
class MarkerMatcherPinDebugTest {

    private val boat = LatLng(43.0, 7.0)

    /** ≈ 2.2 km north of the boat, so both ranges below are exercised against one position. */
    private val pinPosition = LatLng(43.02, 7.0)

    /** The pin path runs no land test, so an empty coastline is all its resolution needs. */
    private val emptyIndex = CoastlineSpatialIndex(emptyList())

    private fun datePoint(rangeM: Double): UserMarker = UserMarker(
        id = "idle-1",
        name = "2026-07-02",
        geometry = MarkerGeometry.Pin(pinPosition),
        proximityOverrideM = rangeM,
        origin = MarkerOrigin.IDLE_AUTO
    )

    @Test
    fun `a date point beyond its range is tested and drawn, and still does not match`() {
        val debugger = VisualWhereAmIDebugger()

        val match = MarkerMatcher.resolveMatch(boat, datePoint(300.0), emptyIndex, debugger)

        assertNull("the 300 m rule still decides the match", match)
        assertEquals(
            "one segment is recorded whichever way the gate answers, and a pin is never blocked",
            listOf(DebugSegment(boat = boat, target = pinPosition, blocked = false)),
            debugger.getSegments()
        )
    }

    @Test
    fun `a date point inside its range still matches`() {
        val match = MarkerMatcher.resolveMatch(boat, datePoint(5_000.0), emptyIndex, NoOpWhereAmIDebugger)

        assertTrue("inside the range the pin matches", match is WhereAmIMatch.LineOfSightMatch)
    }
}
