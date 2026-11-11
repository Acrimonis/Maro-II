package ykws.android.maro.data.track

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ykws.android.maro.data.model.RouteSlowLimit
import ykws.android.maro.data.model.SlowWater
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * **The slow-water figure a card's `Open water · 300M · Zones` row reads** — the walk over a stored
 * track ([TrackSlowWater.of]), the three cells ([slowWaterCells]) and the repository's projection.
 *
 * Fixtures run north along 7.0° E, a point every 0.001° (~111 m): the band holds latitudes under 43.003,
 * a 5 kn zone holds 43.002–43.005, so 43.002–43.003 stands in both and reads the zone.
 */
class TrackSlowWaterTest {

    @get:Rule val temp = TemporaryFolder()

    private val bandLimitKn = 5.0

    private val classifier: (Double, Double) -> SlowWater? = { lat, _ ->
        SlowWater.of(
            zoneLimitKn = if (lat >= 43.002 && lat < 43.005) 5.0 else null,
            inBand = { lat < 43.003 },
            bandLimitKn = bandLimitKn
        )
    }

    private fun point(lat: Double, ms: Long, type: PointType = PointType.NORMAL) =
        TrackPoint(lat, 7.0, timeOffsetMs = ms, type = type)

    /** Each step ~111 m in 20 s — about 11 kn, well clear of the idle test. */
    private fun moving(vararg lats: Double): List<TrackPoint> =
        lats.mapIndexed { i, lat -> point(lat, i * 20_000L) }

    @Test
    fun eachIntervalChargesTheWaterAtItsStartTheZoneOverTheBand() {
        val entries = TrackSlowWater.of(moving(43.000, 43.001, 43.002, 43.003, 43.005, 43.006), classifier)!!

        assertEquals("band first, then the zone", listOf(true, false), entries.map { it.isBand })
        assertEquals("two band intervals start under 43.002", 40L, entries[0].seconds)
        assertEquals("the overlap at 43.002 and the zone's own 43.003 read the zone", 40L, entries[1].seconds)
        assertEquals(5.0, entries[1].limitKn, 1e-9)
    }

    @Test
    fun aNativeStopIsOneLongIdleIntervalAndChargesNothing() {
        // The recorder stores no point while stopped: 10 min between two points 5 m apart in the band.
        val points = listOf(point(43.0000, 0), point(43.00005, 600_000), point(43.001, 620_000))
        val entries = TrackSlowWater.of(points, classifier)!!

        assertEquals("only the 20 s moving interval counts", 20L, entries.single().seconds)
    }

    @Test
    fun aGapEndsTheRun() {
        val points = listOf(
            point(43.000, 0), point(43.001, 20_000),
            point(43.001, 20_001, PointType.GAP),
            point(43.004, 900_000), point(43.0045, 920_000)
        )
        val entries = TrackSlowWater.of(points, classifier)!!

        assertEquals("nothing is read across the seam", 20L, entries.first { it.isBand }.seconds)
        assertEquals(20L, entries.first { !it.isBand }.seconds)
    }

    @Test
    fun aTimelessGpxHasNoFigure() {
        val points = listOf(point(43.000, 0), point(43.001, 0), point(43.002, 0))
        assertNull(TrackSlowWater.of(points, classifier))
    }

    @Test
    fun aNegativeIntervalCountsZero() {
        val points = listOf(point(43.000, 20_000), point(43.001, 0), point(43.0015, 20_000))
        val entries = TrackSlowWater.of(points, classifier)!!

        assertEquals("only the forward interval counts", 20L, entries.single().seconds)
    }

    @Test
    fun aLongSimplifiedIntervalCountsWholeWithNoCap() {
        // One straight 15 min interval across ~1.1 km, simplification's shape: under way, not idle.
        val points = listOf(point(43.000, 0), point(43.010, 900_000))
        assertEquals(900L, TrackSlowWater.of(points, classifier)!!.single().seconds)
    }

    @Test
    fun openWaterAloneIsAComputedEmptyFigure() {
        val entries = TrackSlowWater.of(moving(43.006, 43.007, 43.008), classifier)
        assertTrue("computed, nothing on limited water", entries!!.isEmpty())
    }

    @Test
    fun theThreeCellsSumToTheTotalAndOpenWaterNeverGoesNegative() {
        val entries = listOf(
            SlowWaterEntry(5.0, 120L, isBand = true),
            SlowWaterEntry(5.0, 60L),
            SlowWaterEntry(10.0, 30L)
        )
        val cells = slowWaterCells(600L, entries)
        assertEquals(SlowWaterCells(openWaterSec = 390L, bandSec = 120L, zonesSec = 90L), cells)
        assertEquals(600L, cells.openWaterSec + cells.bandSec + cells.zonesSec)

        assertEquals(0L, slowWaterCells(100L, entries).openWaterSec)
    }

    @Test
    fun aRouteFigureRoundsEachEntryToAWholeSecond() {
        val entries = TrackSlowWater.fromRoute(
            listOf(RouteSlowLimit(5.0, 59.6, isBand = true), RouteSlowLimit(10.0, 30.4))
        )
        assertEquals(listOf(SlowWaterEntry(5.0, 60L, true), SlowWaterEntry(10.0, 30L, false)), entries)
    }

    @Test
    fun aDraftRouteIsSavedWithNoFigure() {
        val draft = TrackFromCourse.build(start = ykws.android.maro.data.model.RoutePoint(43.0, 7.0), legs = emptyList())
        assertEquals(0L, draft.slowWaterStamp)
        assertTrue(draft.slowWater.isEmpty())
    }

    @Test
    fun theRepositoryComputesARecordingAndProjectsARouteAsSaved() = runBlocking {
        val repo = TrackRepository(temp.root)
        val recording = Track("rec", "rec", startTimeMs = 1_000, endTimeMs = 200_000,
            trackPoints = moving(43.000, 43.001, 43.006, 43.007))
        val route = TrackFromCourse.build(
            start = ykws.android.maro.data.model.RoutePoint(43.0, 7.0),
            legs = emptyList(),
            id = "route-saved",
            slowWater = listOf(SlowWaterEntry(5.0, 42L, isBand = true))
        )
        val oldRoute = route.copy(id = "route-old", slowWater = emptyList(), slowWaterStamp = 0L)
        repo.saveAll(listOf(recording, route, oldRoute))

        val before = repo.listTracks().associateBy { it.id }
        assertFalse("nothing attached yet: the recording stays uncomputed", before.getValue("rec").slowWaterComputed)
        assertTrue("a route's own figure needs no classifier", before.getValue("route-saved").slowWaterComputed)

        repo.attachSlowWaterClassifier(classifier, stamp = 7L)
        val after = repo.listTracks().associateBy { it.id }
        assertEquals("the catch-up fills the recording", 7L, after.getValue("rec").slowWaterStamp)
        assertEquals(40L, after.getValue("rec").slowWater.single().seconds)
        assertEquals(42L, after.getValue("route-saved").slowWater.single().seconds)
        assertFalse("a route saved before the figure stays hidden for good", after.getValue("route-old").slowWaterComputed)

        repo.attachSlowWaterClassifier({ _, _ -> null }, stamp = 8L)
        val rebaked = repo.listTracks().associateBy { it.id }
        assertEquals("a new regulation stamp re-marks the recording", 8L, rebaked.getValue("rec").slowWaterStamp)
        assertTrue(rebaked.getValue("rec").slowWater.isEmpty())
        assertEquals("never the route", TrackSlowWater.ROUTE_STAMP, rebaked.getValue("route-saved").slowWaterStamp)
    }

    private fun recording(id: String, vararg lats: Double) =
        Track(id, id, startTimeMs = 1_000, endTimeMs = 200_000, trackPoints = moving(*lats))

    /** A classifier whose first call parks until [release] — the catch-up's walk caught mid-way. */
    private class Parked(val inner: (Double, Double) -> SlowWater?) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        private val armed = AtomicBoolean(true)

        val classifier: (Double, Double) -> SlowWater? = { lat, lon ->
            if (armed.compareAndSet(true, false)) {
                entered.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
            inner(lat, lon)
        }
    }

    @Test
    fun aCheckpointIsNotHeldUpByTheCatchUpWalk() = runBlocking {
        val repo = TrackRepository(temp.root)
        repo.saveAll(listOf(recording("rec", 43.000, 43.001, 43.006, 43.007)))
        val parked = Parked(classifier)
        repo.attachSlowWaterClassifier(parked.classifier, stamp = 7L)

        val listing = async(Dispatchers.Default) { repo.listTracks() }
        assertTrue("the walk is under way", parked.entered.await(5, TimeUnit.SECONDS))

        withTimeout(5_000) { repo.saveCheckpoint(recording("live", 43.000, 43.001)) }

        parked.release.countDown()
        assertEquals(7L, listing.await().single { it.id == "rec" }.slowWaterStamp)
    }

    @Test
    fun aTrackSavedDuringTheWalkKeepsTheFigureItsSaveComputed() = runBlocking {
        val repo = TrackRepository(temp.root)
        repo.saveAll(listOf(recording("rec", 43.000, 43.001, 43.006, 43.007)))
        val parked = Parked(classifier)
        repo.attachSlowWaterClassifier(parked.classifier, stamp = 7L)

        val listing = async(Dispatchers.Default) { repo.listTracks() }
        assertTrue(parked.entered.await(5, TimeUnit.SECONDS))
        Thread.sleep(5) // the re-save must stamp a later updatedAtEpochMs than the first
        // The new version crosses the zone: a band figure alone is the stale walk's, a zone entry the save's.
        repo.saveAll(listOf(recording("rec", 43.000, 43.001, 43.002, 43.006)))
        parked.release.countDown()

        val figure = listing.await().single { it.id == "rec" }.slowWater
        assertTrue("the save's own figure stands, the stale walk not merged over it", figure.any { !it.isBand })
    }

    @Test
    fun aReattachDuringThePassLeavesTheNextListSweepingUnderTheNewStamp() = runBlocking {
        val repo = TrackRepository(temp.root)
        repo.saveAll(listOf(recording("rec", 43.000, 43.001, 43.006, 43.007)))
        val parked = Parked(classifier)
        repo.attachSlowWaterClassifier(parked.classifier, stamp = 7L)

        val listing = async(Dispatchers.Default) { repo.listTracks() }
        assertTrue(parked.entered.await(5, TimeUnit.SECONDS))
        repo.attachSlowWaterClassifier({ _, _ -> null }, stamp = 8L)
        parked.release.countDown()
        assertEquals("the pass in flight finishes under the stamp it began with", 7L,
            listing.await().single { it.id == "rec" }.slowWaterStamp)

        assertEquals("and the next list is owed and sweeps under the new one", 8L,
            repo.listTracks().single { it.id == "rec" }.slowWaterStamp)
    }
}
