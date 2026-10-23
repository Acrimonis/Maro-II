package ykws.android.maro.data.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The plan's worked-cases table (W = 4 min) and the rules behind it. */
class PacePlateauTest {

    private val windowMs = 4 * 60_000L
    private val eps = 1e-6

    private fun plateau(startKn: Double = 25.0) = PacePlateau(windowMs, stopKn = 4.0, startKn = startKn)

    /** One reading a second at [kn] over `[fromSec, toSec)`. */
    private fun PacePlateau.feed(fromSec: Int, toSec: Int, kn: Double?, restricted: Boolean = false) {
        for (s in fromSec until toSec) observe(kn, restricted, s * 1_000L)
    }

    private fun PacePlateau.at(sec: Int): Double = plateauKn(sec * 1_000L)

    @Test
    fun aShortSlowdownNeverCounts() {
        val p = plateau()
        p.feed(0, 300, 30.0)
        p.feed(300, 330, 20.0)
        for (sec in 300..330 step 10) assertEquals(30.0, p.at(sec), eps)
        p.feed(330, 600, 30.0)
        for (sec in 330..600 step 30) assertEquals(30.0, p.at(sec), eps)
    }

    @Test
    fun aStopNeverMovesThePace() {
        val p = plateau()
        p.feed(0, 300, 30.0)
        p.feed(300, 380, 0.0)
        for (sec in 300..380 step 20) assertEquals(30.0, p.at(sec), eps)
        p.feed(380, 600, 30.0)
        for (sec in 380..600 step 30) assertEquals(30.0, p.at(sec), eps)
    }

    @Test
    fun aLastingSlowdownIsFollowedInStages() {
        val p = plateau()
        p.feed(0, 300, 30.0)
        p.feed(300, 480, 25.0)
        assertEquals(30.0, p.at(360), eps)
        assertEquals(27.5, p.at(420), eps)
        assertEquals(25.0, p.at(480), eps)
    }

    @Test
    fun aLastingSpeedUpIsFollowedInStages() {
        val p = plateau()
        p.feed(0, 300, 25.0)
        p.feed(300, 480, 30.0)
        assertEquals(25.0, p.at(360), eps)
        assertEquals(27.5, p.at(420), eps)
        assertEquals(30.0, p.at(480), eps)
    }

    @Test
    fun upAndDownAreMirrored() {
        val down = plateau()
        down.feed(0, 300, 30.0)
        down.feed(300, 480, 25.0)
        val up = plateau()
        up.feed(0, 300, 25.0)
        up.feed(300, 480, 30.0)
        for (sec in 330..480 step 30) assertEquals(55.0, down.at(sec) + up.at(sec), eps)
    }

    @Test
    fun aTwoMinuteSlowdownSagsThenRecovers() {
        val p = plateau()
        p.feed(0, 300, 30.0)
        p.feed(300, 420, 20.0)
        assertEquals(25.0, p.at(420), eps)
        p.feed(420, 720, 30.0)
        assertEquals(30.0, p.at(600), eps)
        assertEquals(30.0, p.at(720), eps)
    }

    @Test
    fun aLongStopThenTheSameSpeedStaysPut() {
        val p = plateau()
        p.feed(0, 300, 30.0)
        p.feed(300, 900, 0.0)
        assertEquals(30.0, p.at(900), eps)
        p.feed(900, 1_200, 30.0)
        for (sec in 900..1_200 step 60) assertEquals(30.0, p.at(sec), eps)
    }

    @Test
    fun aLongStopThenANewSpeedIsFollowedAsIfItNeverStopped() {
        val p = plateau()
        p.feed(0, 300, 30.0)
        p.feed(300, 900, 0.0)
        p.feed(900, 1_080, 25.0)
        assertEquals(30.0, p.at(960), eps)
        assertEquals(27.5, p.at(1_020), eps)
        assertEquals(25.0, p.at(1_080), eps)
    }

    @Test
    fun timeInsideZonesAndTheBandIsIgnored() {
        val p = plateau()
        p.feed(0, 300, 30.0)
        p.feed(300, 1_500, 5.0, restricted = true)
        assertEquals(30.0, p.at(1_500), eps)
        assertEquals(0L, p.readingMs(1_500_000L))
    }

    @Test
    fun aHarbourStartEdgesFromTheStartingPaceToOpenWater() {
        val p = plateau(startKn = 20.0)
        assertEquals(20.0, p.at(0), eps)
        p.feed(0, 180, 32.0)
        assertEquals(20.0, p.at(60), eps)
        assertEquals(26.0, p.at(120), eps)
        assertEquals(32.0, p.at(180), eps)
    }

    @Test
    fun withNoHistoryAndNoReadingThePaceIsTheStartingOne() {
        assertEquals(25.0, plateau(25.0).at(0), eps)
        assertEquals(25.0, plateau(25.0).at(10_000), eps)
    }

    @Test
    fun aReadingUnderTheStopSpeedIsIgnored() {
        val p = plateau()
        p.feed(0, 300, 30.0)
        p.feed(300, 600, 3.2)
        assertEquals(30.0, p.at(600), eps)
        p.feed(600, 660, 3.9)
        assertEquals(30.0, p.at(660), eps)
        assertEquals(0L, p.readingMs(660_000L))
        p.feed(660, 700, 4.0)
        assertTrue(p.readingMs(700_000L) > 0L)
    }

    @Test
    fun aReadingSpeaksForAtMostTenSeconds() {
        val p = plateau()
        p.observe(20.0, restricted = false, atMs = 0L)
        assertEquals(10_000L, p.readingMs(100_000L))
        assertEquals(5_000L, p.readingMs(5_000L))
    }

    @Test
    fun readingsAreWeightedByTheTimeTheyCover() {
        // 40 s at 20 kn then 200 s at 30 kn: the slow part is under a quarter, so it never counts.
        val sparse = plateau(startKn = 30.0)
        sparse.observe(20.0, false, 0L)
        sparse.observe(20.0, false, 10_000L)
        sparse.observe(20.0, false, 20_000L)
        sparse.observe(20.0, false, 30_000L)
        sparse.feed(40, 240, 30.0)
        assertEquals(30.0, sparse.at(240), eps)
        // The same number of readings at the faster speed, bunched into 4 s, covers far less.
        val bunched = plateau(startKn = 20.0)
        for (i in 0 until 4) bunched.observe(30.0, false, i * 1_000L)
        assertEquals(20.0, bunched.at(4), eps)
    }

    @Test
    fun liveCountsOnlyOnceAQuarterOfTheWindowIsRead() {
        val p = plateau()
        p.feed(0, 59, 30.0)
        assertFalse(RoutePace.isLive(p.readingMs(59_000L), windowMs))
        p.feed(59, 61, 30.0)
        assertTrue(RoutePace.isLive(p.readingMs(61_000L), windowMs))
    }

    @Test
    fun theStartingPaceIsRevisableUntilTheFirstReading() {
        val p = plateau(25.0)
        assertFalse(p.hasReadings)
        p.startKn = 28.0
        assertEquals(28.0, p.at(0), eps)
        p.observe(30.0, false, 0L)
        assertTrue(p.hasReadings)
    }

    @Test
    fun aStopAfterTheCruiseIsSettledHoldsTheCruiseNotAMidTransitionPace() {
        // A cold start at 25, two minutes at 30 (Live: a quarter of the window), then a long stop.
        val p = plateau(startKn = 25.0)
        p.feed(0, 120, 30.0)
        assertEquals(30.0, p.at(400), eps)
        assertEquals(30.0, p.at(1_400), eps)
        // And a boat resuming at a new speed is still followed as if it never stopped.
        p.feed(1_400, 1_580, 25.0)
        assertEquals(30.0, p.at(1_460), eps)
        assertEquals(27.5, p.at(1_520), eps)
        assertEquals(25.0, p.at(1_580), eps)
    }

    @Test
    fun oneReadAgreesWithItsTwoParts() {
        val p = plateau()
        p.feed(0, 300, 30.0)
        p.feed(300, 400, 0.0)
        for (sec in listOf(300, 350, 400, 500, 700)) {
            val read = p.read(sec * 1_000L)
            assertEquals(p.plateauKn(sec * 1_000L), read.kn, 0.0)
            assertEquals(p.readingMs(sec * 1_000L), read.readingMs)
        }
    }

    @Test
    fun theOpenGapFollowsAWindowOrStartingPaceMove() {
        // Cold start at 25, then only 30 s of 30 kn — under a quarter of the window — and silence: the open gap
        // counts at the pace shown when it began, the starting pace still carrying the trip.
        val early = plateau(startKn = 25.0)
        early.feed(0, 30, 30.0)
        assertEquals(early.at(39), early.at(400), eps)
        assertEquals(25.0, early.at(400), eps)
        // A moved window is not served from the gap pace cached for the old one.
        early.windowMs = 8 * 60_000L
        val fresh = PacePlateau(8 * 60_000L, 4.0, 25.0).also { q -> q.feed(0, 30, 30.0) }
        assertEquals(fresh.at(400), early.at(400), eps)
    }

    @Test
    fun theResultStaysInsideTheSettingsSpan() {
        val p = plateau()
        p.feed(0, 600, 99.0)
        assertEquals(35.0, p.at(600), eps)
    }
}
