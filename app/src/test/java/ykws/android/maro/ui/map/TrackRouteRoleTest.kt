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
 * on its own unpinned path (a pinned route takes the pinned stroke through the shared pinned path, D5),
 * the set split that lets the count bound the unpinned ones alone, and the pair and ladder it
 * interpolates over its own set.
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
    fun aRouteReadsItsOwnTwoAxesWithNoChipAboveIt() {
        // 2026-10-07: the route role takes its own colours axis for the band and its own arrows axis for
        // the chevrons — the tracks kind's pair no longer reaches a route at all.
        val plain = routeLineRenderPlan(routeArrows = false, routeColours = false, selected = false)
        val banded = routeLineRenderPlan(routeArrows = true, routeColours = true, selected = false)

        assertEquals(LineRenderPath.ROUTE, plain.path)
        assertFalse(plain.drawArrows)
        assertEquals(LineRenderPath.BANDED, banded.path)
        assertTrue(banded.drawArrows)
        assertTrue("a route is dashed whatever its path", plain.dashed && banded.dashed)
    }

    @Test
    fun aPinnedRouteKeepsItsDashOnTheOnePinnedPath() {
        // D5: a pinned route runs the one pinned path, exactly as a pinned recorded track does — the
        // `isRoute` special case that pushed it back to the route role is gone. Only the values that
        // path paints (the pinned-route pair and ladder) are selected per kind, and that lives in the
        // effect, not here. D12: the dash follows the summary's own identity, so a pinned route stays
        // dashed while a pinned recorded track is solid, on that same shared pinned path.
        val route = summary("route-pinned", route = true, pinned = true)
        val pinnedRoute = pinnedLineRenderPlan(
            summary = route,
            trackArrows = false, trackColours = false,
            routeArrows = true, routeColours = false,
            selected = false, eyeOverride = null
        )
        val pinnedRecorded = pinnedLineRenderPlan(
            summary = summary("recording-pinned", pinned = true),
            trackArrows = false, trackColours = false,
            routeArrows = true, routeColours = false,
            selected = false, eyeOverride = null
        )
        val routeRole = routeLineRenderPlan(routeArrows = true, routeColours = false, selected = false)

        // The two pinned kinds share one path and one role, and the dash is the only thing the route's
        // own identity adds (D12): a pinned route is dashed, a pinned recorded track is not.
        assertEquals(
            "a pinned route and a pinned track share one pinned path",
            pinnedRecorded.path,
            pinnedRoute.path
        )
        assertTrue("a pinned route keeps its dash (D12)", pinnedRoute.dashed)
        assertFalse("a pinned recorded track stays solid", pinnedRecorded.dashed)
        assertNotEquals("the route role is what a pinned route no longer takes", routeRole, pinnedRoute)
        // The pinned stroke governs every pinned item: the loop passes route = false for both kinds.
        assertEquals(
            AppConfig.trackWidthPinnedDp,
            storedTrackWidth(selected = false, route = false, pinned = true, newest = false),
            1e-6f
        )
        // The helper still ranks the route stroke over the pin where it is asked to — the route role an
        // unpinned route takes — so the table itself is unchanged (R34, D11).
        assertEquals(
            AppConfig.trackWidthRouteDp,
            storedTrackWidth(selected = false, route = true, pinned = true, newest = false),
            1e-6f
        )
    }

    @Test
    fun eachKindsAxesGovernTheirOwnLinesAlone() {
        // The kinds' independence at the one entry point that reads both: a track id is asked of the
        // track pair and a route id of the route pair, so neither kind's axis can band the other's lines.
        fun banded(routeIds: Set<String>, trackColours: Boolean, routeColours: Boolean): Boolean =
            bandedStrokeOnMap(
                paintedIds = setOf("t", "r"),
                arrowAxis = false,
                coloursAxis = trackColours,
                highlightedTrackId = null,
                eyeOverride = null,
                tracksVisible = true,
                routeIds = routeIds,
                routeSpeedColour = routeColours
            )

        assertTrue(
            "the track axis bands the recorded id",
            banded(routeIds = setOf("r"), trackColours = true, routeColours = false)
        )
        assertTrue(
            "the route axis bands the route id",
            banded(routeIds = setOf("r"), trackColours = false, routeColours = true)
        )
        assertFalse(
            "neither axis on bands nothing",
            banded(routeIds = setOf("r"), trackColours = false, routeColours = false)
        )
        // With no id marked a route, every painted id is read as a recorded track: the track axis alone.
        assertTrue(banded(routeIds = emptySet(), trackColours = true, routeColours = false))
        assertFalse(banded(routeIds = emptySet(), trackColours = false, routeColours = true))
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
            trackFilter = ListFilter(),
            routeFilter = ListFilter(),
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
            trackFilter = ListFilter(),
            routeFilter = ListFilter(),
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
    fun aPinnedTrackTheMapFilterExcludesIsNotDrawn() {
        // The pin escapes the route count alone; it is no escape from the map filter — the map draws
        // its filter's set and nothing else (2026-09-28, family plan §3).
        val today = 1_000_000_000_000L
        val summaries = listOf(
            summary("pinned-excluded", route = true, pinned = true, startTimeMs = 0L),
            summary("in-range", startTimeMs = today)
        )
        val selection = storedTrackSelection(
            summaries = summaries,
            trackFilter = ListFilter(mapOf("dateRange" to "LAST_7_DAYS")),
            routeFilter = ListFilter(mapOf("dateRange" to "LAST_7_DAYS")),
            focus = MapRenderFocus(),
            tracksVisible = true,
            todayMidnightMs = today,
            recordingNb = 10,
            routeNb = 10
        )
        assertTrue("the pin no longer escapes the map filter", selection.pinned.isEmpty())
        assertTrue("nor does it ride the counted route set", selection.routes.isEmpty())
        assertEquals(listOf("in-range"), selection.recorded.map { it.id })
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

    @Test
    fun aBandedRouteAndABandedTrackDifferOnlyByTheDashedFlag() {
        // The dashed stroke is keyed on the summary's route identity, never on the path: a route and a
        // recorded track can both take BANDED, so the flag is what tells the dispatcher which to dash.
        val route = routeLineRenderPlan(routeArrows = true, routeColours = true, selected = false)
        val recorded = lineRenderPlan(
            arrowAxis = true, coloursAxis = true, selected = false,
            eyeOverride = null, route = false
        )

        assertTrue(route.dashed)
        assertFalse(recorded.dashed)
        assertEquals(LineRenderPath.BANDED, route.path)
        assertEquals(LineRenderPath.BANDED, recorded.path)
    }

    @Test
    fun thePinnedSetIsFilteredByItsOwnKind() {
        // S6: the pinned escape reads each kind's own map filter. A pinned route is excluded by the route
        // filter, a pinned track by the track filter, and neither escapes the other's.
        val today = 1_000_000_000_000L
        val summaries = listOf(
            summary("pinned-route", route = true, pinned = true, startTimeMs = 0L),
            summary("pinned-track", pinned = true, startTimeMs = 0L)
        )
        val dateOnly = ListFilter(mapOf("dateRange" to "LAST_7_DAYS"))

        val none = storedTrackSelection(
            summaries = summaries,
            trackFilter = dateOnly, routeFilter = dateOnly,
            focus = MapRenderFocus(), tracksVisible = true, todayMidnightMs = today,
            recordingNb = 10, routeNb = 10
        )
        assertTrue("both kinds excluded leaves the pinned set empty", none.pinned.isEmpty())

        val trackOnly = storedTrackSelection(
            summaries = summaries,
            trackFilter = ListFilter(), routeFilter = dateOnly,
            focus = MapRenderFocus(), tracksVisible = true, todayMidnightMs = today,
            recordingNb = 10, routeNb = 10
        )
        assertEquals(listOf("pinned-track"), trackOnly.pinned.map { it.id })

        val routeOnly = storedTrackSelection(
            summaries = summaries,
            trackFilter = dateOnly, routeFilter = ListFilter(),
            focus = MapRenderFocus(), tracksVisible = true, todayMidnightMs = today,
            recordingNb = 10, routeNb = 10
        )
        assertEquals(listOf("pinned-route"), routeOnly.pinned.map { it.id })
    }

    @Test
    fun theCountedHalvesAreFilteredPerKindToo() {
        // S6: the recorded half reads the track filter, the route half the route filter.
        val today = 1_000_000_000_000L
        val summaries = listOf(
            summary("track-in", startTimeMs = today),
            summary("track-out", startTimeMs = 0L),
            summary("route-in", route = true, startTimeMs = today),
            summary("route-out", route = true, startTimeMs = 0L)
        )
        val dateOnly = ListFilter(mapOf("dateRange" to "LAST_7_DAYS"))

        val selection = storedTrackSelection(
            summaries = summaries,
            trackFilter = ListFilter(), routeFilter = dateOnly,
            focus = MapRenderFocus(), tracksVisible = true, todayMidnightMs = today,
            recordingNb = 10, routeNb = 10
        )
        assertEquals(listOf("track-in", "track-out"), selection.recorded.map { it.id }.sorted())
        assertEquals(listOf("route-in"), selection.routes.map { it.id })
    }

    @Test
    fun theTwoKindsHaveTheirOwnVisibilitySwitch() {
        // The drawer's eyes (2026-10-05): each kind's layer is gated on its own flag, and the pinned
        // items follow their kind — a route hidden by routesVisible takes its pinned route with it, a
        // track hidden by tracksVisible takes its pinned track, and neither flag touches the other kind.
        val summaries = listOf(
            summary("track", startTimeMs = 1_000L),
            summary("track-pinned", pinned = true, startTimeMs = 1_000L),
            summary("route", route = true, startTimeMs = 1_000L),
            summary("route-pinned", route = true, pinned = true, startTimeMs = 1_000L)
        )

        // Tracks hidden, routes drawn.
        val tracksOff = storedTrackSelection(
            summaries = summaries,
            trackFilter = ListFilter(),
            routeFilter = ListFilter(),
            focus = MapRenderFocus(),
            tracksVisible = false,
            routesVisible = true,
            todayMidnightMs = 0L,
            recordingNb = 10,
            routeNb = 10
        )
        assertTrue("the recorded half is empty when tracksVisible is off", tracksOff.recorded.isEmpty())
        assertEquals(listOf("route"), tracksOff.routes.map { it.id })
        assertEquals(
            "a pinned track follows tracksVisible",
            listOf("route-pinned"),
            tracksOff.pinned.map { it.id }
        )

        // Routes hidden, tracks drawn.
        val routesOff = storedTrackSelection(
            summaries = summaries,
            trackFilter = ListFilter(),
            routeFilter = ListFilter(),
            focus = MapRenderFocus(),
            tracksVisible = true,
            routesVisible = false,
            todayMidnightMs = 0L,
            recordingNb = 10,
            routeNb = 10
        )
        assertEquals(listOf("track"), routesOff.recorded.map { it.id })
        assertTrue("the route half is empty when routesVisible is off", routesOff.routes.isEmpty())
        assertEquals(
            "a pinned route follows routesVisible",
            listOf("track-pinned"),
            routesOff.pinned.map { it.id }
        )
    }

    @Test
    fun theFanMasterRulesBothKindsAndTheEyesOnlyRefineIt() {
        // 2026-10-10: the layer fan's master gate is final — off, it hides both kinds whatever the eyes
        // say; on, each kind is left to its own eye.
        assertFalse("the master off hides a kind whose eye is on", kindLayerOn(masterVisible = false, kindVisible = true))
        assertFalse("the master off hides a kind whose eye is off too", kindLayerOn(masterVisible = false, kindVisible = false))
        assertTrue("the master on leaves an eye on drawn", kindLayerOn(masterVisible = true, kindVisible = true))
        assertFalse("the master on leaves an eye off hidden", kindLayerOn(masterVisible = true, kindVisible = false))
    }

    @Test
    fun theOpenDashboardsOwnItemIsDrawnWhateverTheGatesSay() {
        // 2026-10-10: while the info an item describes is on screen the item must be on the map, so a
        // switched-off kind still draws it — and it alone. The other kind's half is untouched.
        val summaries = listOf(
            summary("track", startTimeMs = 1_000L),
            summary("route", route = true, startTimeMs = 1_000L)
        )

        val hiddenBoth = storedTrackSelection(
            summaries = summaries,
            trackFilter = ListFilter(), routeFilter = ListFilter(),
            focus = MapRenderFocus(),
            tracksVisible = false, routesVisible = false,
            todayMidnightMs = 0L, recordingNb = 10, routeNb = 10,
            selectedId = "route"
        )
        assertTrue("the recorded half stays empty", hiddenBoth.recorded.isEmpty())
        assertEquals("the open route alone is drawn", listOf("route"), hiddenBoth.routes.map { it.id })

        val tracksHiddenOpenTrack = storedTrackSelection(
            summaries = summaries,
            trackFilter = ListFilter(), routeFilter = ListFilter(),
            focus = MapRenderFocus(),
            tracksVisible = false, routesVisible = true,
            todayMidnightMs = 0L, recordingNb = 10, routeNb = 10,
            selectedId = "track"
        )
        assertEquals(
            "the open recorded track alone is drawn",
            listOf("track"),
            tracksHiddenOpenTrack.recorded.map { it.id }
        )
        assertEquals("the other kind is unaffected", listOf("route"), tracksHiddenOpenTrack.routes.map { it.id })
    }

    @Test
    fun theOpenItemNeverEscapesTheFilter() {
        // The escape reaches the visibility gates alone: with the gate on the filter still decides, so an
        // opened item the filter excludes stays out (the 2026-09-28 decision, kept rather than re-opened).
        val summaries = listOf(summary("route", route = true, startTimeMs = 1_000L))
        val selection = storedTrackSelection(
            summaries = summaries,
            trackFilter = ListFilter(), routeFilter = ListFilter(mapOf("pinned" to "PINNED")),
            focus = MapRenderFocus(),
            tracksVisible = true, routesVisible = true,
            todayMidnightMs = 0L, recordingNb = 10, routeNb = 10,
            selectedId = "route"
        )
        assertTrue("an unpinned route stays out while the filter asks for pinned ones", selection.routes.isEmpty())
    }
}
