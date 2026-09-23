package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.ListFilter
import ykws.android.maro.data.model.MapRenderFocus
import ykws.android.maro.data.track.TrackSummary

/**
 * The route role's decisions (R34, R35, R37, R38): the path a route takes and why, the stroke it earns
 * whatever its pin says, the set split that lets the count bound the unpinned ones alone, and the pair
 * and ladder it interpolates over its own set.
 */
class TrackRouteRoleTest {

    private fun summary(
        id: String,
        route: Boolean = false,
        pinned: Boolean = false,
        startTimeMs: Long = 0L
    ) = TrackSummary(
        id = id,
        name = id,
        startTimeMs = startTimeMs,
        endTimeMs = startTimeMs + 1_000L,
        pinned = pinned,
        route = route
    )

    @Test
    fun aRouteTakesItsOwnPairWhateverTheChipsSay() {
        val bothChipsOn = trackRenderPlan(
            trackArrows = true, trackColours = true, selected = false,
            eyeOverride = null, route = true
        )
        val bothChipsOff = trackRenderPlan(
            trackArrows = false, trackColours = false, selected = false,
            eyeOverride = null, route = true
        )

        assertEquals(TrackRenderPath.ROUTE, bothChipsOn.path)
        assertEquals(TrackRenderPath.ROUTE, bothChipsOff.path)
    }

    @Test
    fun aPinnedRouteDrawsByTheSamePlanAnUnpinnedOneDoes() {
        // Read through the two functions the loops really call, each with its own argument set — the
        // counted pass plans a route role outright, the pinned pass reads the summary's own flag — so the
        // pinned loop's `route = summary.route` is what this holds. Reverting it to a plain stored plan
        // is the change that fails here (R34).
        val route = summary("route-pinned", route = true, pinned = true)
        val unpinned = routeTrackRenderPlan(
            trackArrows = false, trackColours = false, selected = false, eyeOverride = null,
            routeSpeedColour = false, routeSpeedArrows = true
        )
        val pinned = pinnedTrackRenderPlan(
            summary = route,
            trackArrows = false, trackColours = false, selected = false, eyeOverride = null,
            routeSpeedColour = false, routeSpeedArrows = true
        )

        assertEquals(unpinned, pinned)
        assertEquals(TrackRenderPath.ROUTE, pinned.path)
        // And the route's own flag does not leak to a recorded track whose pin is set: the pinned
        // recorded row keeps the stored plan it has always had.
        assertNotEquals(
            TrackRenderPath.ROUTE,
            pinnedTrackRenderPlan(
                summary = summary("recording-pinned", pinned = true),
                trackArrows = false, trackColours = false, selected = false, eyeOverride = null,
                routeSpeedColour = true, routeSpeedArrows = true
            ).path
        )
        assertEquals(
            "the route stroke wins over the pinned one",
            AppConfig.trackWidthRouteDp,
            storedTrackWidth(selected = false, route = true, pinned = true, newest = false),
            1e-6f
        )
        assertEquals(
            "and the pinned stroke still governs a recorded track",
            AppConfig.trackWidthPinnedDp,
            storedTrackWidth(selected = false, route = false, pinned = true, newest = false),
            1e-6f
        )
    }

    @Test
    fun theSpeedColourGateReplacesThePairWithTheRamp() {
        val off = trackRenderPlan(
            trackArrows = true, trackColours = false, selected = false,
            eyeOverride = null, route = true, routeSpeedColour = false
        )
        val on = trackRenderPlan(
            trackArrows = true, trackColours = false, selected = false,
            eyeOverride = null, route = true, routeSpeedColour = true
        )

        assertEquals(TrackRenderPath.ROUTE, off.path)
        assertEquals(TrackRenderPath.BANDED, on.path)
    }

    @Test
    fun theArrowGateCanOnlyVetoTheChips() {
        assertTrue(
            trackRenderPlan(
                trackArrows = true, trackColours = false, selected = false,
                eyeOverride = null, route = true, routeSpeedArrows = true
            ).drawArrows
        )
        assertFalse(
            trackRenderPlan(
                trackArrows = true, trackColours = false, selected = false,
                eyeOverride = null, route = true, routeSpeedArrows = false
            ).drawArrows
        )
        assertFalse(
            "the gate vetoes, it never draws what the chip withholds",
            trackRenderPlan(
                trackArrows = false, trackColours = false, selected = false,
                eyeOverride = null, route = true, routeSpeedArrows = true
            ).drawArrows
        )
    }

    @Test
    fun theSetSplitsByTheFlagAndAPinnedRouteStaysARoute() {
        val summaries = listOf(
            summary("recorded-new", startTimeMs = 3_000L),
            summary("route-pinned", route = true, pinned = true, startTimeMs = 2_000L),
            summary("route-open", route = true, startTimeMs = 1_000L),
            summary("recorded-old", startTimeMs = 0L)
        )

        val sets = storedTrackSets(summaries)

        assertEquals(listOf("route-pinned", "route-open"), sets.routes.map { it.id })
        assertEquals(listOf("recorded-new", "recorded-old"), sets.recorded.map { it.id })
        // A pinned route is in the route set, which is what lets the pinned pass draw it whatever the
        // route count says: the count bounds the unpinned ones the counted pass ranks.
        assertTrue(sets.routes.first { it.id == "route-pinned" }.pinned)
        assertFalse(sets.recorded.any { it.route })
    }

    @Test
    fun theRouteCountBoundsTheRouteSetAloneAndAPinnedRouteEscapesIt() {
        // Five open routes, one pinned route and two recordings: the two counts are the two siblings'
        // own, and the pin takes the route out of the counted set entirely (R35).
        val summaries = listOf(
            summary("recording-new", startTimeMs = 7_000L),
            summary("recording-old", startTimeMs = 6_000L),
            summary("route-pinned", route = true, pinned = true, startTimeMs = 5_000L),
            summary("route-5", route = true, startTimeMs = 4_000L),
            summary("route-4", route = true, startTimeMs = 3_000L),
            summary("route-3", route = true, startTimeMs = 2_000L),
            summary("route-2", route = true, startTimeMs = 1_000L),
            summary("route-1", route = true, startTimeMs = 0L)
        )

        val selection = storedTrackSelection(
            summaries = summaries,
            highlightedTrackId = null,
            filter = ListFilter(),
            focus = MapRenderFocus(),
            tracksVisible = true,
            todayMidnightMs = 0L,
            recordingNb = 1,
            routeNb = 2
        )

        // The route set is bounded by the route count and never by the recorded one: a cap taken from
        // `recordingNb` would answer `route-5` alone here, not two routes.
        assertEquals(listOf("route-5", "route-4"), selection.routes.map { it.id })
        // The recorded half is bounded by its own count, untouched by the route count.
        assertEquals(listOf("recording-new"), selection.recorded.map { it.id })
        // And the pinned route is drawn outside the route set, which is the one thing the pin buys.
        assertEquals(listOf("route-pinned"), selection.pinned.map { it.id })
        assertTrue(selection.pinned.single().route)

        // The count taken to zero empties the counted set while the pinned route still escapes it: a
        // pinned pass capped by the route count would answer nothing here (R35).
        val noRoutes = storedTrackSelection(
            summaries = summaries,
            highlightedTrackId = null,
            filter = ListFilter(),
            focus = MapRenderFocus(),
            tracksVisible = true,
            todayMidnightMs = 0L,
            recordingNb = 5,
            routeNb = 0
        )
        assertTrue("a route count of 0 leaves the counted route set empty", noRoutes.routes.isEmpty())
        assertEquals(
            "and the pinned route is still drawn, the pin being the escape",
            listOf("route-pinned"),
            noRoutes.pinned.map { it.id }
        )
    }

    @Test
    fun thePairAndTheLadderInterpolateOverTheRouteSetAlone() {
        val routeFrom = 0xFF1565C0.toInt()
        val routeTo = 0xFF0000FF.toInt()

        val newest = computeTrackPolylineAppearance(
            index = 0, total = 3,
            transparencyNewest = 20, transparencyOldest = 80,
            colorFrom = routeFrom, colorTo = routeTo,
            strokeWidth = AppConfig.trackWidthRouteDp
        )
        val oldest = computeTrackPolylineAppearance(
            index = 2, total = 3,
            transparencyNewest = 20, transparencyOldest = 80,
            colorFrom = routeFrom, colorTo = routeTo,
            strokeWidth = AppConfig.trackWidthRouteDp
        )

        // The newest route wears the pair's own `from` at the 20 % transparency the ladder opens on,
        // the oldest its `to` at the 80 % it closes on: the ladder is the route pair's, interpolated
        // over the route set and nothing else. The alphas are compared with a tolerance because the
        // fade multiplies a float fraction by 255 and truncates it.
        assertEquals("the head wears the pair's `from`", 0x1565C0, newest.argb and 0x00FFFFFF)
        assertEquals("the foot wears its `to`", 0x0000FF, oldest.argb and 0x00FFFFFF)
        assertEquals(204f, (newest.argb ushr 24).toFloat(), 1f)
        assertEquals(0.2f * 255f, (oldest.argb ushr 24).toFloat(), 1f)

        val historyNewest = computeTrackPolylineAppearance(
            index = 0, total = 3,
            transparencyNewest = 0, transparencyOldest = 100,
            colorFrom = 0xFFFF6F00.toInt(), colorTo = 0xFFFF8F00.toInt(),
            strokeWidth = AppConfig.trackWidthHistoryDp
        )
        assertNotEquals(historyNewest.argb, newest.argb)
    }
}
