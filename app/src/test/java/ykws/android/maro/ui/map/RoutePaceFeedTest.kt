package ykws.android.maro.ui.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.route.PaceSource
import ykws.android.maro.data.route.SpeedHistory.Trip
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.spatial.RouteDeclarations
import ykws.android.maro.spatial.RouteEngine
import ykws.android.maro.spatial.RouteId
import ykws.android.maro.spatial.RouteUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The pace `Now` plans at, fed through the view model: where a trip starts, which source names the number,
 * the day's pace and the settings that move it. The reduction itself is `PacePlateauTest`'s.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoutePaceFeedTest {

    private val today = "2026-10-22"
    private val seaMs = 1_000L

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun releaseMainDispatcher() {
        Dispatchers.resetMain()
    }

    private val noEngine = object : RouteEngine {
        override fun routesToCompute(origin: RoutePoint, destination: RoutePoint): RouteDeclarations =
            throw UnsupportedOperationException("the pace tests arm nothing")

        override fun startLookup(computationId: RouteId): RouteId =
            throw UnsupportedOperationException("the pace tests arm nothing")

        override fun cancelLookup(id: RouteId) = Unit

        override val updates: Flow<RouteUpdate> = emptyFlow()
    }

    private fun viewModel(
        settings: MutableStateFlow<AppSettings>,
        date: () -> String = { today }
    ) = RouteViewModel(MutableStateFlow(noEngine), settings, localDate = date)

    /** One reading a second at [kn] over `[fromSec, toSec)`. */
    private fun RouteViewModel.feed(fromSec: Int, toSec: Int, kn: Double?) {
        for (s in fromSec until toSec) observePace(kn, restricted = false, nowMs = seaMs + s * 1_000L)
    }

    private fun history(vararg kn: Double) =
        kn.mapIndexed { i, v -> Trip(v, 30.0, (i + 1).toLong()) }

    @Test
    fun withNoHistoryAndNoReadingTheDefaultPlansAndSaysSo() = runTest {
        val vm = viewModel(MutableStateFlow(AppSettings()))
        vm.startTrip(seaMs)
        assertEquals(AppConfig.routeFreeWaterPaceKn.toDouble(), vm.nowPace.value.kn, 1e-9)
        assertEquals(PaceSource.DEFAULT, vm.nowPace.value.source)
    }

    @Test
    fun aThinHistoryStillReadsAsHistory() = runTest {
        val settings = AppSettings(routeSpeedHistoryTrips = history(18.0, 22.0), routeSpeedHistoryTrackCount = 5)
        val vm = viewModel(MutableStateFlow(settings))
        vm.startTrip(seaMs)
        assertEquals(PaceSource.HISTORY, vm.nowPace.value.source)
        assertEquals(18.0, vm.nowPace.value.kn, 1e-9)
    }

    @Test
    fun theDaysPaceStartsATripOnItsOwnDateAndTheHistoryOtherwise() = runTest {
        val settings = AppSettings(
            routePaceDayKn = 29f,
            routePaceDayDate = today,
            routeSpeedHistoryTrips = history(18.0)
        )
        val sameDay = viewModel(MutableStateFlow(settings))
        sameDay.startTrip(seaMs)
        assertEquals(PaceSource.TODAY, sameDay.nowPace.value.source)
        assertEquals(29.0, sameDay.nowPace.value.kn, 1e-9)

        val nextDay = viewModel(MutableStateFlow(settings)) { "2026-10-23" }
        nextDay.startTrip(seaMs)
        assertEquals(PaceSource.HISTORY, nextDay.nowPace.value.source)
        assertEquals(18.0, nextDay.nowPace.value.kn, 1e-9)
    }

    @Test
    fun aTripPastMidnightKeepsItsDate() = runTest {
        var date = today
        val settings = AppSettings(routePaceDayKn = 29f, routePaceDayDate = today)
        val vm = viewModel(MutableStateFlow(settings)) { date }
        vm.startTrip(seaMs)
        date = "2026-10-23"
        vm.feed(0, 600, 29.0)
        assertEquals(today, vm.tripDateIso)
        assertEquals(PaceSource.LIVE, vm.nowPace.value.source)
        // The next trip is the one that looks at the date again.
        vm.startTrip(seaMs + 700_000L)
        assertEquals("2026-10-23", vm.tripDateIso)
        assertEquals(PaceSource.DEFAULT, vm.nowPace.value.source)
    }

    @Test
    fun withNoRecordedTripTheDaysDateFollowsTheCalendar() = runTest {
        var date = today
        val vm = viewModel(MutableStateFlow(AppSettings())) { date }
        vm.feed(0, 60, 30.0)
        assertEquals(today, vm.tripDateIso)
        date = "2026-10-23"
        vm.feed(60, 120, 30.0)
        assertEquals("2026-10-23", vm.tripDateIso)
        // A recorded trip pins it, and its end lets the calendar take over again.
        vm.startTrip(seaMs + 200_000L)
        date = "2026-10-24"
        vm.feed(200, 260, 30.0)
        assertEquals("2026-10-23", vm.tripDateIso)
        vm.endTrip()
        vm.feed(260, 270, 30.0)
        assertEquals("2026-10-24", vm.tripDateIso)
    }

    @Test
    fun aRefreshAgesTheWindowWithNoNewReading() = runTest {
        val vm = viewModel(MutableStateFlow(AppSettings(routePaceWindowMin = 4)))
        vm.startTrip(seaMs)
        vm.feed(0, 300, 30.0)
        assertEquals(PaceSource.LIVE, vm.nowPace.value.source)
        // The speed value then holds still, so the feed says nothing more; only the clock moves.
        vm.refreshPace(seaMs + 600_000L)
        assertEquals(PaceSource.DEFAULT, vm.nowPace.value.source)
        assertEquals(30.0, vm.nowPace.value.kn, 1e-6)
    }

    @Test
    fun liveSwitchesOnOnceAQuarterOfTheWindowIsRead() = runTest {
        val vm = viewModel(MutableStateFlow(AppSettings(routePaceWindowMin = 4)))
        vm.startTrip(seaMs)
        vm.feed(0, 59, 30.0)
        assertEquals(PaceSource.DEFAULT, vm.nowPace.value.source)
        vm.feed(59, 61, 30.0)
        assertEquals(PaceSource.LIVE, vm.nowPace.value.source)
    }

    @Test
    fun afterAStopTheSavedDaysPaceNamesTheNumberAgain() = runTest {
        // Live had already been saved as the day's pace (the screen writes it while Live).
        val settings = MutableStateFlow(AppSettings())
        val vm = viewModel(settings)
        vm.startTrip(seaMs)
        vm.feed(0, 300, 30.0)
        assertEquals(PaceSource.LIVE, vm.nowPace.value.source)
        settings.value = settings.value.copy(routePaceDayKn = 30f, routePaceDayDate = today)
        vm.feed(300, 1_400, 0.0)
        assertEquals(PaceSource.TODAY, vm.nowPace.value.source)
        assertEquals(30.0, vm.nowPace.value.kn, 1e-6)
    }

    @Test
    fun aDemoDriveFeedsNoLivePace() = runTest {
        val vm = viewModel(MutableStateFlow(AppSettings(routeSpeedHistoryTrips = history(21.0))))
        vm.startTrip(seaMs)
        vm.feed(0, 600, null)
        assertEquals(PaceSource.HISTORY, vm.nowPace.value.source)
        assertEquals(21.0, vm.nowPace.value.kn, 1e-9)
    }

    @Test
    fun aNumberedStopPlansAsSetWhileNowKeepsReadingTheBoat() = runTest {
        val settings = MutableStateFlow(AppSettings(routeFreeWaterPaceKn = 20f))
        val vm = viewModel(settings)
        vm.startTrip(seaMs)
        vm.feed(0, 300, 30.0)
        assertEquals(20.0, vm.paceKn.value, 1e-9)
        assertEquals(30.0, vm.nowPace.value.kn, 1e-6)
        settings.value = settings.value.copy(routeFreeWaterPaceKn = AppConfig.ROUTE_FREE_WATER_PACE_ANY)
        assertEquals(30.0, vm.paceKn.value, 1e-6)
    }

    @Test
    fun movingNOrTheDemoSwitchMovesTheStartingPaceAtOnce() = runTest {
        val store = history(20.0, 20.0, 20.0) + Trip(32.0, 30.0, 10L, demo = true)
        val settings = MutableStateFlow(AppSettings(routeSpeedHistoryTrips = store, routeSpeedHistoryTrackCount = 5))
        val vm = viewModel(settings)
        vm.startTrip(seaMs)
        assertEquals(20.0, vm.nowPace.value.kn, 1e-9)
        settings.value = settings.value.copy(routeSpeedHistoryIncludeDemo = true, routeSpeedHistoryTrackCount = 1)
        assertEquals(32.0, vm.nowPace.value.kn, 1e-9)
    }

    @Test
    fun theWindowSliderMovesHowFastANewSpeedIsFollowed() = runTest {
        fun paceAfterOneMinuteAt25(windowMin: Int): Double {
            val vm = viewModel(MutableStateFlow(AppSettings(routePaceWindowMin = windowMin)))
            vm.startTrip(seaMs)
            vm.feed(0, 600, 30.0)
            vm.feed(600, 720, 25.0)
            return vm.nowPace.value.kn
        }
        // Two minutes into a lasting slowdown: a 2-minute window is fully there, a 10-minute one barely.
        assertEquals(25.0, paceAfterOneMinuteAt25(2), 1e-6)
        assertNotEquals(25.0, paceAfterOneMinuteAt25(10), 1e-6)
    }
}
