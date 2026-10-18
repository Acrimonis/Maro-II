package ykws.android.maro.spatial

import org.junit.Assert.assertEquals
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.MarkerOrigin
import ykws.android.maro.data.model.markers.UserMarker

/**
 * The one rule both kinds' ends share (2026-10-18): the smallest zone wins, an inside match beats a
 * proximity match only on equal size, auto markers are ignored, and a pin — size zero — outranks any
 * zone. Pure over pre-built matches, so no [CoastlineSpatialIndex] and no Android runtime.
 */
class MarkerMatcherNamingMatchTest {

    private fun marker(
        id: String,
        radiusM: Double? = null,
        origin: MarkerOrigin = MarkerOrigin.USER
    ) = UserMarker(
        id = id,
        name = id,
        geometry = if (radiusM == null) MarkerGeometry.Pin(LatLng(43.0, 7.0))
        else MarkerGeometry.Circle(LatLng(43.0, 7.0), radiusM),
        origin = origin
    )

    private fun inside(id: String, radiusM: Double) =
        WhereAmIMatch.ZoneMatch(marker(id, radiusM), radiusM, 0.0, 0.0)

    private fun near(id: String, radiusM: Double, seaDistanceM: Double) =
        WhereAmIMatch.LineOfSightMatch(marker(id, radiusM), seaDistanceM, 0.0)

    private fun top(vararg matches: WhereAmIMatch): String? =
        MarkerMatcher.topNamingMatch(matches.toList())?.let { MarkerMatcher.markerOf(it).id }

    @Test
    fun `smallest containing zone wins among nested zones`() {
        assertEquals("small", top(inside("big", 500.0), inside("small", 100.0)))
    }

    @Test
    fun `a near zone smaller than the containing zone wins`() {
        assertEquals("small", top(inside("big", 500.0), near("small", 100.0, 20.0)))
    }

    @Test
    fun `inside beats near on equal zone size`() {
        assertEquals("inside", top(inside("inside", 300.0), near("near", 300.0, 20.0)))
    }

    @Test
    fun `an auto marker never names the position`() {
        val auto = WhereAmIMatch.ZoneMatch(
            marker("auto", radiusM = 5.0, origin = MarkerOrigin.IDLE_AUTO), 5.0, 0.0, 0.0
        )
        assertEquals("user", top(auto, inside("user", 500.0)))
    }

    @Test
    fun `a near pin outranks a containing zone`() {
        val pin = WhereAmIMatch.LineOfSightMatch(marker("pin"), 10.0, 0.0)
        assertEquals("pin", top(inside("zone", 500.0), pin))
    }
}
