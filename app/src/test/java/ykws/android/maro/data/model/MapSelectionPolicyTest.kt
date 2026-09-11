package ykws.android.maro.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.track.Track
import ykws.android.maro.data.track.TrackSummary

/**
 * Pure policy tests — no device, no IO. Covers the map-render visibility contract:
 * eligibility, ranking + cap, focus override, session boost (+ reset invalidation), resume-backup
 * twin ordering, and the deliberate asymmetry between tracks (ranked + capped) and markers
 * (filter-only, no cap).
 */
class MapSelectionPolicyTest {

    private val trackPolicy = TrackSelectionPolicy()

    /** Fixed "today midnight" so the date-axis tests are deterministic. */
    private val today: Long = 1_000_000_000_000L
    private val dayMs = 86_400_000L
    private val last7Days = ListFilter(mapOf("dateRange" to "LAST_7_DAYS"))

    private fun summary(
        id: String,
        startTimeMs: Long,
        lastPointTimeMs: Long = 0L,
        pinned: Boolean = false,
        live: Boolean = false
    ) = TrackSummary(
        id = id,
        name = id,
        startTimeMs = startTimeMs,
        lastPointTimeMs = if (lastPointTimeMs == 0L) startTimeMs else lastPointTimeMs,
        pinned = pinned
    ).also { it.isLive = live }

    private fun marker(id: String, icon: String? = null, pinned: Boolean = false) = UserMarker(
        id = id,
        name = id,
        geometry = MarkerGeometry.Pin(LatLng(latitude = 43.0, longitude = 7.0)),
        icon = icon,
        pinned = pinned
    )

    // ── Eligibility + cap ─────────────────────────────────────────────────

    @Test
    fun emptyFilter_underCap_allEligibleDrawn_newestFirst() {
        val items = listOf(summary("c", today - 2 * dayMs), summary("b", today - dayMs), summary("a", today))
        val selected = trackPolicy.select(items, ListFilter(), cap = 10, focus = MapRenderFocus(), todayMidnightMs = today)
        assertEquals(listOf("a", "b", "c"), selected.map { it.id })
    }

    @Test
    fun capBoundary_keepsNewest_tiesBrokenByLastPointTime() {
        val items = listOf(
            summary("oldest", startTimeMs = today - 3 * dayMs),
            summary("tieLow", startTimeMs = today - dayMs, lastPointTimeMs = today - dayMs + 10),
            summary("tieHigh", startTimeMs = today - dayMs, lastPointTimeMs = today - dayMs + 500),
            summary("newest", startTimeMs = today)
        )
        val selected = trackPolicy.select(items, ListFilter(), cap = 2, focus = MapRenderFocus(), todayMidnightMs = today)
        assertEquals(listOf("newest", "tieHigh"), selected.map { it.id })
    }

    // ── Resume-backup twin ordering (1 ms nudge) ──────────────────────────

    /**
     * `TrackViewModel.duplicateTrack` nudges the copy 1 ms older than its original so the twins never
     * tie on `startTimeMs`. Contract: at an exact cap the copy is always the one dropped, whatever
     * order the summaries arrive in — without the nudge the pair ties and the winner falls back to
     * that order.
     */
    @Test
    fun resumeBackupTwin_copyDroppedFirst_regardlessOfInputOrder() {
        val start = today - 2 * dayMs
        val original = summary("original", startTimeMs = start)
        val copy = summary("original (backup)", startTimeMs = start - 1)   // the nudge
        val filler = summary("filler", startTimeMs = today)

        // Cap exactly fits the filler plus one twin → the copy must lose.
        val forward = trackPolicy.select(
            listOf(original, copy, filler), ListFilter(), cap = 2, focus = MapRenderFocus(), todayMidnightMs = today
        )
        assertEquals(listOf("filler", "original"), forward.map { it.id })

        // Same set, reversed input order → identical outcome (determinism, not list order).
        val reversed = trackPolicy.select(
            listOf(filler, copy, original), ListFilter(), cap = 2, focus = MapRenderFocus(), todayMidnightMs = today
        )
        assertEquals(listOf("filler", "original"), reversed.map { it.id })
    }

    // ── Focus override ────────────────────────────────────────────────────

    @Test
    fun highlightedId_overridesCapAndFilter() {
        val focus = MapRenderFocus().apply { highlight("viewed") }
        val items = listOf(
            summary("newest", startTimeMs = today),
            summary("viewed", startTimeMs = today - 100 * dayMs)   // outside LAST_7_DAYS
        )
        val selected = trackPolicy.select(items, last7Days, cap = 1, focus = focus, todayMidnightMs = today)
        assertEquals(listOf("viewed"), selected.map { it.id })
    }

    @Test
    fun sessionBoost_includedEvenWhenFilterExcludes() {
        val focus = MapRenderFocus().apply { markTouched("imported") }
        val items = listOf(
            summary("recent", startTimeMs = today),
            // An imported track with an old startTimeMs must still render without selecting or pinning.
            summary("imported", startTimeMs = today - 100 * dayMs)
        )
        val selected = trackPolicy.select(items, last7Days, cap = 10, focus = focus, todayMidnightMs = today)
        assertEquals(listOf("imported", "recent"), selected.map { it.id })
    }

    @Test
    fun trackFilterReset_clearsSessionBoost_highlightUnaffected() {
        val focus = MapRenderFocus().apply {
            markTouched("imported")
            highlight("viewed")
        }
        val items = listOf(
            summary("imported", startTimeMs = today - 100 * dayMs),
            summary("viewed", startTimeMs = today - 100 * dayMs)
        )
        assertEquals(
            listOf("viewed", "imported"),
            trackPolicy.select(items, last7Days, cap = 10, focus = focus, todayMidnightMs = today).map { it.id }
        )
        focus.clearBoost()
        // Boost gone → the filtered-out imported track disappears; the highlighted one stays.
        assertEquals(
            listOf("viewed"),
            trackPolicy.select(items, last7Days, cap = 10, focus = focus, todayMidnightMs = today).map { it.id }
        )
    }

    @Test
    fun boostIsBounded_oldestEvicted() {
        val focus = MapRenderFocus(maxTouched = 2)
        focus.markTouched("a")
        focus.markTouched("b")
        focus.markTouched("c")
        assertEquals(listOf("b", "c"), focus.touchedIds.toList())
        assertFalse(focus.isBoosted("a"))
    }

    // ── Pinned exclusion + live exemption ─────────────────────────────────

    @Test
    fun pinned_excludedFromCappedHistorySet() {
        val items = listOf(
            summary("pinnedNewest", today, pinned = true),
            summary("normal", today - dayMs)
        )
        val selected = trackPolicy.select(items, ListFilter(), cap = 10, focus = MapRenderFocus(), todayMidnightMs = today)
        assertEquals(listOf("normal"), selected.map { it.id })
    }

    @Test
    fun liveTrack_exemptFromDateAxis() {
        val items = listOf(summary("live", startTimeMs = today - 100 * dayMs, live = true))
        val selected = trackPolicy.select(items, last7Days, cap = 10, focus = MapRenderFocus(), todayMidnightMs = today)
        assertEquals(listOf("live"), selected.map { it.id })
    }

    // ── Markers: filter-only, no cap ──────────────────────────────────────

    @Test
    fun markers_filterOnly_noCap() {
        val markers = (1..30).map { marker("m$it") }
        val unfiltered = MarkerSelectionPolicy().select(markers, ListFilter(), cap = 1, focus = MapRenderFocus(), todayMidnightMs = today)
        assertEquals(30, unfiltered.size)

        val withIcon = markers.mapIndexed { i, m -> if (i == 0) m.copy(icon = "⚓") else m }
        val filtered = MarkerSelectionPolicy().select(
            items = withIcon,
            filter = ListFilter(mapOf("icon" to "WITH_ICON")),
            cap = 1,
            focus = MapRenderFocus(),
            todayMidnightMs = today
        )
        assertEquals(listOf("m1"), filtered.map { it.id })
    }

    // ── Guard: the removed flag must not be re-introduced ─────────────────

    @Test
    fun visibleOnMap_isNotDeclaredOnTheEntities() {
        assertTrue(Track::class.java.declaredFields.none { it.name == "visibleOnMap" })
        assertTrue(TrackSummary::class.java.declaredFields.none { it.name == "visibleOnMap" })
    }
}
