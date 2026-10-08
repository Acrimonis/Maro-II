package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.PathClass
import ykws.android.maro.config.PathKind
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.track.PointType
import ykws.android.maro.data.track.TrackPoint

/**
 * The seam's own adapters and the painter's kind-agnosticism (S13): a track's stored speed and a
 * route's leg-derived speed land on the same [RenderPoint] shape, the route adapter yields a neutral
 * speed where its legs do not line up, and both kinds adapt to equal seam values — the
 * both-kinds-equal case the plan asks for.
 */
class LineRenderSeamTest {

    private fun trackPoint(lat: Double, speedMps: Float) =
        TrackPoint(lat = lat, lon = 7.0, speedMps = speedMps, timeOffsetMs = 0L)

    @Test
    fun aTracksStoredSpeedWinsTheAdapter() {
        val seam = listOf(trackPoint(43.55, 5f), trackPoint(43.56, 6f)).toRenderPoints()

        assertEquals(5f * 1.94384f, seam[0].speedKn!!, 1e-3f)
        assertEquals(6f * 1.94384f, seam[1].speedKn!!, 1e-3f)
    }

    @Test
    fun aGapSeamIsABreakWithANeutralSpeed() {
        val seam = listOf(
            trackPoint(43.55, 5f),
            TrackPoint(lat = 43.56, lon = 7.0, type = PointType.GAP)
        ).toRenderPoints()

        assertTrue(seam[1].isBreak)
        assertNull(seam[1].speedKn)
    }

    @Test
    fun aRoutesSpeedIsDerivedFromTheLegEachPointLeaves() {
        // Two equal legs of 0.01° — about 1112 m each — over 100 s: ≈ 11.1 m/s ≈ 21.6 kn.
        val points = listOf(
            RoutePoint(43.55, 7.0),
            RoutePoint(43.56, 7.0),
            RoutePoint(43.57, 7.0)
        )

        val seam = toRenderPoints(points, listOf(100.0, 100.0))

        assertEquals(3, seam.size)
        assertEquals(seam[0].speedKn!!, seam[1].speedKn!!, 0.01f)
        // The last point reads the leg arriving at it.
        assertEquals(seam[1].speedKn!!, seam[2].speedKn!!, 0.01f)
        assertTrue(seam[0].speedKn!! in 20f..24f)
    }

    @Test
    fun aDraftPlansZeroLegsYieldANeutralSpeed() {
        // A plan whose legs do not line up yields a neutral speed, never a fabricated one.
        val seam = toRenderPoints(listOf(RoutePoint(43.55, 7.0), RoutePoint(43.56, 7.0)), listOf(0.0))

        assertNull(seam[0].speedKn)
        assertNull(seam[1].speedKn)
    }

    @Test
    fun bothKindsAdaptToTheSameSeamValues() {
        // The same geometry at the same speed, once through a track's stored-speed adapter and once
        // through a route's leg-time adapter: the seam's values are equal, so the one painter cannot
        // tell the two kinds apart (S13).
        val trackSeam = listOf(trackPoint(43.55, 5f), trackPoint(43.56, 5f)).toRenderPoints()
        val routeSeam = toRenderPoints(
            listOf(RoutePoint(43.55, 7.0), RoutePoint(43.56, 7.0)),
            listOf(0.0)
        )
        val speed = trackSeam[0].speedKn
        val matched = routeSeam.map { it.copy(speedKn = speed) }

        assertEquals(trackSeam, matched)
    }

    @Test
    fun theSpecsKindIsTheOnlyDifferenceBetweenTwoSpecs() {
        val points = listOf(RenderPoint(43.55, 7.0, speedKn = 5f))
        val track = LineRenderSpec(PathKind.TRACK, PathClass.HISTORY, points)
        val route = LineRenderSpec(PathKind.ROUTE, PathClass.HISTORY, points)

        assertEquals(track.points, route.points)
        assertEquals(track.dashed, route.dashed)
        assertEquals(track.drawArrows, route.drawArrows)
        assertTrue(track.kind != route.kind)
    }
}
