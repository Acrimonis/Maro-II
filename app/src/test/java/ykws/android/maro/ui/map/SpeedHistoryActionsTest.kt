package ykws.android.maro.ui.map

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ykws.android.maro.data.route.PaceReading
import ykws.android.maro.data.route.PaceSource
import ykws.android.maro.data.route.SpeedHistory.Trip
import ykws.android.maro.data.route.speedHistoryProfileKn
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.data.track.Track
import ykws.android.maro.data.track.TrackPoint
import ykws.android.maro.data.track.TrackSummary
import ykws.android.maro.spatial.Units

@OptIn(ExperimentalCoroutinesApi::class)
class SpeedHistoryActionsTest {

    private fun summary(
        id: String,
        start: Long,
        updated: Long = 0L,
        route: Boolean = false,
        demo: Boolean = false,
        water: Int? = null,
        land: Int? = null
    ) = TrackSummary(
        id = id,
        name = id,
        startTimeMs = start,
        updatedAtEpochMs = updated,
        route = route,
        demo = demo,
        // Stored biased by one; both left at 0 is a track never classified.
        waterPointCount = water?.plus(1) ?: 0,
        landPointCount = land?.plus(1) ?: 0
    )

    private fun trip(kn: Double, start: Long, min: Double = 30.0, demo: Boolean = false) = Trip(kn, min, start, demo)

    // ── E4: which tracks a seed reads ────────────────────────────────────

    @Test
    fun theSeedReadsTheNewestByStartTimeNotByEditTime() {
        val summaries = listOf(
            summary("oldest-but-renamed", start = 1, updated = 999),
            summary("newest", start = 30, updated = 2),
            summary("middle", start = 20, updated = 1)
        )
        assertEquals(
            listOf("newest", "middle", "oldest-but-renamed"),
            seedCandidates(summaries).map { it.id }
        )
    }

    @Test
    fun aRouteSaveAndATrackNotMostlyOnWaterNeverEnter() {
        val summaries = listOf(
            summary("sea", start = 5, water = 40, land = 2),
            summary("planned", start = 6, route = true),
            summary("drive", start = 7, water = 1, land = 50),
            summary("unclassified", start = 8)
        )
        // A track nothing could classify counts as water, as the track filter reads it.
        assertEquals(listOf("unclassified", "sea"), seedCandidates(summaries).map { it.id })
    }

    @Test
    fun theSeedKeepsFifteenOfEachKind() {
        val summaries = (1..20).map { summary("g$it", start = it.toLong()) } +
            (1..20).map { summary("d$it", start = 100L + it, demo = true) }
        val chosen = seedCandidates(summaries)
        assertEquals(15, chosen.count { !it.demo })
        assertEquals(15, chosen.count { it.demo })
        assertEquals("g20", chosen.first { !it.demo }.id)
    }

    // ── E3: the snapshot and the live profile ───────────────────────────

    @Test
    fun aFoldMovesTheLiveProfileAndLeavesTheCalculateSnapshotAlone() {
        val seeded = AppSettings(routeSpeedHistoryTrackCount = 5)
            .withSeededHistory(listOf(trip(20.0, 1), trip(20.0, 2), trip(20.0, 3)), nowMs = 1_000L)
        assertEquals(20f, seeded.routeSpeedHistoryComputedKn, 0f)
        assertEquals(1_000L, seeded.routeSpeedHistoryComputedAtEpochMs)

        val folded = (4..8).fold(seeded) { s, i -> s.withFoldedTrip(trip(30.0, i.toLong())) }
        assertEquals(20f, folded.routeSpeedHistoryComputedKn, 0f)
        assertEquals(1_000L, folded.routeSpeedHistoryComputedAtEpochMs)
        assertEquals(30.0, folded.speedHistoryProfileKn()!!, 1e-9)
        assertNotEquals(folded.routeSpeedHistoryComputedKn.toDouble(), folded.speedHistoryProfileKn()!!, 1e-9)
    }

    @Test
    fun theProfileMovesAtOnceWithNAndWithTheDemoSwitch() {
        val store = listOf(
            trip(20.0, 1), trip(20.0, 2), trip(20.0, 3),
            trip(40.0, 4, demo = true), trip(40.0, 5, demo = true)
        )
        val base = AppSettings(routeSpeedHistoryTrips = store, routeSpeedHistoryTrackCount = 5)
        assertEquals(20.0, base.speedHistoryProfileKn()!!, 1e-9)
        val withDemo = base.copy(routeSpeedHistoryIncludeDemo = true, routeSpeedHistoryTrackCount = 2)
        assertEquals(40.0, withDemo.speedHistoryProfileKn()!!, 1e-9)
        // Nothing was thrown away: N back up brings the older trips back.
        assertEquals(20.0, withDemo.copy(routeSpeedHistoryTrackCount = 5).speedHistoryProfileKn()!!, 1e-9)
    }

    @Test
    fun aHistoryWithNoTripsHasNoProfile() {
        assertNull(AppSettings().speedHistoryProfileKn())
    }

    @Test
    fun theDaysPaceIsSavedUnderItsTripsDate() {
        val saved = AppSettings().withDayPace(29.4, "2026-10-22")
        assertEquals(29.4f, saved.routePaceDayKn, 1e-4f)
        assertEquals("2026-10-22", saved.routePaceDayDate)
    }

    // ── The day's pace save: throttled, trailing, Live only ─────────────

    @Test
    fun theLastLiveValueIsSavedAtTheWindowsEndAndNeverDropped() = runTest {
        val pace = MutableStateFlow(PaceReading(25.0, PaceSource.DEFAULT))
        val saved = mutableListOf<Pair<Long, Double>>()
        val job = launch {
            saveDayPace(pace, { "2026-10-22" }, { testScheduler.currentTime }) { kn, _ ->
                saved += testScheduler.currentTime to kn
            }
        }
        runCurrent()
        pace.value = PaceReading(30.0, PaceSource.LIVE)
        runCurrent()
        advanceTimeBy(2_000)
        pace.value = PaceReading(30.5, PaceSource.LIVE)
        runCurrent()
        advanceTimeBy(3_000)
        pace.value = PaceReading(31.0, PaceSource.LIVE)
        runCurrent()
        advanceTimeBy(1_000)
        // Leaving Live does not cancel the pending save: the last Live value is the one kept.
        pace.value = PaceReading(31.0, PaceSource.TODAY)
        runCurrent()
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(listOf(0L to 30.0, 10_000L to 31.0), saved)
        job.cancel()
    }

    @Test
    fun aMoveUnderATenthOfAKnotIsNotSaved() = runTest {
        val pace = MutableStateFlow(PaceReading(30.0, PaceSource.LIVE))
        val saved = mutableListOf<Double>()
        val job = launch {
            saveDayPace(pace, { "2026-10-22" }, { testScheduler.currentTime }) { kn, _ -> saved += kn }
        }
        runCurrent()
        advanceTimeBy(15_000)
        pace.value = PaceReading(30.05, PaceSource.LIVE)
        runCurrent()
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(listOf(30.0), saved)
        job.cancel()
    }

    @Test
    fun aDemoOrUnreadPaceIsNeverSaved() = runTest {
        val pace = MutableStateFlow(PaceReading(21.0, PaceSource.HISTORY))
        val saved = mutableListOf<Double>()
        val job = launch {
            saveDayPace(pace, { "2026-10-22" }, { testScheduler.currentTime }) { kn, _ -> saved += kn }
        }
        runCurrent()
        pace.value = PaceReading(22.0, PaceSource.TODAY)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(emptyList<Double>(), saved)
        job.cancel()
    }

    // ── The fold-in's own decision ───────────────────────────────────────

    private fun cruisingTrack(id: String, route: Boolean = false) = Track(
        id = id,
        name = id,
        startTimeMs = 7L,
        route = route,
        trackPoints = (0 until 10).map {
            TrackPoint(lat = 43.5, lon = 7.0, speedMps = (30.0 / Units.KNOTS_PER_MPS).toFloat(), timeOffsetMs = it * 1_000L)
        }
    )

    private val nowhereRestricted: (Double, Double) -> Boolean = { _, _ -> false }

    @Test
    fun aFinishedTripOnWaterFoldsIn() {
        val track = cruisingTrack("t")
        val trip = finishedTripForHistory(track, listOf(summary("t", start = 7, water = 30, land = 1)), nowhereRestricted)
        assertEquals(30.0, trip!!.speedKn, 1e-3)
        assertEquals(7L, trip.startTimeMs)
    }

    @Test
    fun aRouteSaveOrALandTripNeverFoldsIn() {
        assertNull(finishedTripForHistory(cruisingTrack("r", route = true), emptyList(), nowhereRestricted))
        val land = listOf(summary("t", start = 7, water = 1, land = 40))
        assertNull(finishedTripForHistory(cruisingTrack("t"), land, nowhereRestricted))
    }

    @Test
    fun aTripWhoseSummaryHasNotArrivedReadsAsWater() {
        assertEquals(30.0, finishedTripForHistory(cruisingTrack("t"), emptyList(), nowhereRestricted)!!.speedKn, 1e-3)
    }
}
