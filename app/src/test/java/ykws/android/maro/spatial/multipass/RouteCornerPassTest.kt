package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** The corner pass's own contract: convex corners are rounded outward and clear, the rest stays sharp. */
class RouteCornerPassTest {

    @Test
    fun aStraightLineStaysWhole() {
        val a = LatLng(43.5, 7.0)
        val b = LatLng(43.502, 7.0)
        val c = LatLng(43.504, 7.0)
        val rounded = RouteCornerPass.round(
            listOf(a, b, c), 28.0, CoastWorld(emptyList()), null, 50.0
        )
        assertEquals(listOf(a, b, c), rounded.points)
    }

    @Test
    fun aConvexCornerIsRoundedAndStaysClear() {
        val corner = LatLng(43.5, 7.0)
        val prev = LatLng(43.505, 7.0)
        val next = LatLng(43.5, 7.005)
        val coast = listOf(LatLng(43.494, 6.995), LatLng(43.494, 7.005))
        val world = CoastWorld(coast)
        val margin = 50.0
        val rounded = RouteCornerPass.round(
            listOf(prev, corner, next), 28.0, world, null, margin
        )
        assertTrue("the corner gains arc points", rounded.points.size > 3)
        assertTrue("the corner vertex is replaced by the arc", corner !in rounded.points)
        for (p in rounded.points) {
            assertTrue(
                "every drawn point clears the coast",
                world.distanceToCoastM(p.latitude, p.longitude) >= margin - 1e-6
            )
        }
        assertTrue(
            "some arc point carries a corner-speed ceiling",
            rounded.points.any { rounded.ceilingKnAt(it) != null }
        )
    }

    @Test
    fun aCornerNextToAZoneDoesNotBowIntoIt() {
        val corner = LatLng(43.5, 7.0)
        val prev = LatLng(43.505, 7.0)
        val next = LatLng(43.5, 7.005)
        val zone = SpeedZone(
            "z", "Cap", 5.0,
            listOf(
                LatLng(43.501, 7.001),
                LatLng(43.501, 7.020),
                LatLng(43.520, 7.020),
                LatLng(43.520, 7.001),
                LatLng(43.501, 7.001)
            )
        )
        val world = CoastWorld(emptyList(), listOf(zone))
        val rounded = RouteCornerPass.round(
            listOf(prev, corner, next), 28.0, world, null, 50.0
        )
        for (p in rounded.points) {
            assertFalse("no drawn point bows into the zone", zone.contains(p.latitude, p.longitude))
        }
    }

    @Test
    fun aCornerWithShortLegsStaysSharp() {
        val corner = LatLng(43.5, 7.0)
        val prev = LatLng(43.50001, 7.0)
        val next = LatLng(43.5, 7.00001)
        val rounded = RouteCornerPass.round(
            listOf(prev, corner, next), 28.0, CoastWorld(emptyList()), null, 50.0
        )
        assertTrue("the corner stands sharp", corner in rounded.points)
        assertEquals("taken at the turn floor", 5.0, rounded.ceilingKnAt(corner)!!, 0.01)
    }

    /** The tally's own contract: every bent vertex is read, and the answer names what happened there. */
    @Test
    fun theTallyNamesWhatHappenedAtEachCorner() {
        val shallow = RouteCornerPass.round(
            listOf(LatLng(43.5, 7.0), LatLng(43.502, 7.0), LatLng(43.504, 7.0)),
            28.0, CoastWorld(emptyList()), null, 50.0
        )
        assertEquals(
            "a straight run is read as too shallow to round",
            RouteCornerPass.CornerOutcome.SHALLOW, shallow.verdicts.single().outcome
        )

        val rounded = RouteCornerPass.round(
            listOf(LatLng(43.505, 7.0), LatLng(43.5, 7.0), LatLng(43.5, 7.005)),
            28.0, CoastWorld(emptyList()), null, 50.0
        )
        val verdict = rounded.verdicts.single()
        assertEquals("the convex corner is drawn", RouteCornerPass.CornerOutcome.ROUNDED, verdict.outcome)
        assertEquals("the tally reads the turn it saw", 90.0, verdict.turnDeg, 0.5)
        assertTrue("the reach asked is the shorter leg the policy allows", verdict.reachAskM > 0.0)
        assertTrue("the room is the reach, nothing having bowed before it", verdict.roomM > 0.0)

        val starved = RouteCornerPass.round(
            listOf(LatLng(43.50001, 7.0), LatLng(43.5, 7.0), LatLng(43.5, 7.00001)),
            28.0, CoastWorld(emptyList()), null, 50.0
        )
        val starvedVerdict = starved.verdicts.single()
        assertEquals(
            "legs too short to round read as no room",
            RouteCornerPass.CornerOutcome.NO_ROOM, starvedVerdict.outcome
        )
        assertEquals("and the corner is taken at the floor", 5.0, starvedVerdict.speedKn, 0.01)
    }

    /**
     * Two bends the pass will curve on one leg **share** it, so the pair reads as one continuous S rather
     * than the first taking the whole leg and leaving the second a micro-curve. Two 45° bends sit 800 m
     * apart — wider than the pace's own tangent for their summed 90°, so the pass keeps them as two — between
     * 1500 m straights in a coast-free world, read on the code defaults (`a_lat` 0.33, floor 5 kn,
     * transition 2 s, reach 1.0): the first is capped at its **share** of the leg, never at the leg.
     */
    @Test
    fun twoBendsOnOneLegShareItAndMeet() {
        val a = LatLng(43.4904610, 6.9868500)
        val firstBend = LatLng(43.5000000, 7.0000000)
        val secondBend = LatLng(43.5000000, 7.0099180)
        val d = LatLng(43.4904610, 7.0230682)

        val rounded = RouteCornerPass.round(
            listOf(a, firstBend, secondBend, d), 28.0, CoastWorld(emptyList()), null, 50.0
        )

        val first = rounded.verdicts[0]
        val second = rounded.verdicts[1]
        assertEquals("the first bend is curved", RouteCornerPass.CornerOutcome.ROUNDED, first.outcome)
        assertEquals("the second is curved too", RouteCornerPass.CornerOutcome.ROUNDED, second.outcome)
        assertEquals("the policy asked for the whole shorter leg", 800.0, first.reachAskM, 2.0)
        assertEquals("the share caps it at the weighted half", 400.0, first.sharedM ?: 0.0, 2.0)
        assertEquals("and the room is that share", 400.0, first.roomM, 2.0)
        assertTrue(
            "the bend after it keeps what the first bow left (${second.roomM} m)",
            second.roomM > 400.0
        )
        assertEquals("the first is taken at the pace", 28.0, first.speedKn, 1.0)
        assertEquals("and so is the second", 28.0, second.speedKn, 1.0)
    }

    /**
     * A close pair of bends is **one turn**. Two 45° bends 55 m apart between 500 m straights are replaced
     * by the crossing of their outer legs, so the pass rounds the summed 90° once over those legs — where
     * sharing the 55 m leg could only have left a micro-curve beside a wide one.
     */
    @Test
    fun aClosePairOfBendsIsOneTurn() {
        val a = LatLng(43.4968240, 6.9956206)
        val firstBend = LatLng(43.5000000, 7.0000000)
        val secondBend = LatLng(43.5000000, 7.0006838)
        val d = LatLng(43.4968240, 7.0050632)

        val rounded = RouteCornerPass.round(
            listOf(a, firstBend, secondBend, d), 28.0, CoastWorld(emptyList()), null, 50.0
        )

        assertEquals("the pair is one corner, not two", 1, rounded.verdicts.size)
        val corner = rounded.verdicts.single()
        assertEquals("the corner is curved", RouteCornerPass.CornerOutcome.ROUNDED, corner.outcome)
        assertEquals("its angle is the pair's summed turn", 90.0, corner.turnDeg, 2.0)
        assertFalse("the first bend's vertex is gone", firstBend in rounded.points)
        assertFalse("and the second's with it", secondBend in rounded.points)
        assertTrue("the corner is taken fast (${corner.speedKn} kn)", corner.speedKn > 18.0)
    }

    /**
     * **Scope 1** — a bend too flat to round writes no corner ceiling, and its verdict reads the pace.
     *
     * Assumes `AppConfig`'s code defaults, the properties not being loaded in a JVM test:
     * `lateralAccelMps2` 0.33 m/s², `minSpeedKn` 5 kn, `transitionSec` 2 s, `reachFraction` 1.0.
     */
    @Test
    fun aShallowBendKeepsItsLimitAndReadsThePace() {
        val corner = LatLng(43.5, 7.0)
        // 200 m in, then 200 m out turned 5° east — under the pass's own 10° turn threshold.
        val prev = offset(corner, 0.0, -200.0)
        val next = offset(corner, 200.0 * tan(Math.toRadians(5.0)), 200.0)
        val rounded = RouteCornerPass.round(
            listOf(prev, corner, next), 28.0, CoastWorld(emptyList()), null, 50.0
        )
        val verdict = rounded.verdicts.single()
        assertEquals("too flat to round", RouteCornerPass.CornerOutcome.SHALLOW, verdict.outcome)
        assertTrue("the corner stands as its bare vertex", corner in rounded.points)
        assertTrue("no corner ceiling is written", rounded.ceilingKnAt(corner) == null)
        assertEquals("the verdict reports the pace, not the floor", 28.0, verdict.speedKn, 0.01)
        assertTrue(
            "no drawn point is capped at all",
            rounded.points.none { rounded.ceilingKnAt(it) != null }
        )
    }

    /**
     * **Scope 2** — a bend on a short stub is rounded, not refused. A 48° bend carrying a 34 m stub
     * between a 790 m and a 1541 m straight takes its room from the whole **significant** in-leg — the
     * stub folded into the 790 m feed, the 3.3° stub being a point of the line rather than a corner —
     * so it is drawn at the pace, never collapsed to the stub. Assumes `AppConfig`'s code defaults:
     * `lateralAccelMps2` 0.33 m/s², `minSpeedKn` 5 kn, `transitionSec` 2 s, `reachFraction` 1.0.
     */
    @Test
    fun aBendOnAShortStubIsRoundedNotRefused() {
        val corner = LatLng(43.5, 7.0)
        val stubStart = offset(corner, 0.0, -34.0)
        val feedStart = offset(
            stubStart,
            -790.0 * sin(Math.toRadians(3.3)),
            -790.0 * cos(Math.toRadians(3.3))
        )
        val next = offset(corner, 1541.0 * sin(Math.toRadians(48.0)), 1541.0 * cos(Math.toRadians(48.0)))
        val rounded = RouteCornerPass.round(
            listOf(feedStart, stubStart, corner, next), 28.0, CoastWorld(emptyList()), null, 50.0
        )
        assertTrue("two bends are read", rounded.verdicts.size == 2)
        val feed = rounded.verdicts[0]
        val bend = rounded.verdicts[1]
        assertEquals(
            "the feeding kink is too flat to round",
            RouteCornerPass.CornerOutcome.SHALLOW, feed.outcome
        )
        assertTrue("and so carries no ceiling", rounded.ceilingKnAt(stubStart) == null)
        assertEquals(
            "the 48° bend is rounded, not refused",
            RouteCornerPass.CornerOutcome.ROUNDED, bend.outcome
        )
        assertEquals("the tally reads the turn it saw", 48.0, bend.turnDeg, 1.0)
        assertEquals("the room is the whole significant in-leg, not the 34 m stub", 824.0, bend.roomM, 2.0)
        assertTrue("the corner vertex is replaced by the arc", corner !in rounded.points)
        assertTrue("the bend carries a ceiling", rounded.points.any { rounded.ceilingKnAt(it) != null })
    }

    /**
     * **The guard measures curve to curve.** A 48° bend carrying a 6° kink a few metres before it, on a
     * 400 m feed and a 500 m exit, is rounded with the room of its **significant** in-leg — the feed plus
     * the kink's own few metres — never the few metres to the kink. The kink is too flat to be a corner,
     * so it is a point of the line and nothing for the new bow to overlap: before this change the guard
     * measured to that drawn kink and read `room` as its 12 m distance alone, collapsing the bend beside
     * it. Assumes `AppConfig`'s code defaults, the properties not being loaded in a JVM test:
     * `lateralAccelMps2` 0.33 m/s², `minSpeedKn` 5 kn, `transitionSec` 2 s, `reachFraction` 1.0.
     */
    @Test
    fun aKinkTooFlatToBeACornerDoesNotCapTheBendBesideIt() {
        val corner = LatLng(43.5, 7.0)
        val kink = offset(corner, 0.0, -12.0)
        val feedStart = offset(
            kink,
            -400.0 * sin(Math.toRadians(6.0)),
            -400.0 * cos(Math.toRadians(6.0))
        )
        val next = offset(corner, 500.0 * sin(Math.toRadians(48.0)), 500.0 * cos(Math.toRadians(48.0)))
        val rounded = RouteCornerPass.round(
            listOf(feedStart, kink, corner, next), 28.0, CoastWorld(emptyList()), null, 50.0
        )
        assertTrue("two bends are read", rounded.verdicts.size == 2)
        val flat = rounded.verdicts[0]
        val bend = rounded.verdicts[1]
        assertEquals(
            "the kink is too flat to round",
            RouteCornerPass.CornerOutcome.SHALLOW, flat.outcome
        )
        assertEquals(
            "the 48° bend is rounded, not collapsed to the kink",
            RouteCornerPass.CornerOutcome.ROUNDED, bend.outcome
        )
        assertEquals("the tally reads the turn it saw", 48.0, bend.turnDeg, 1.0)
        assertEquals("the policy asks the whole shorter leg", 412.0, bend.reachAskM, 2.0)
        assertEquals("the room is that whole leg, not the 12 m to the kink", 412.0, bend.roomM, 2.0)
        assertTrue("the corner is taken above the floor (${bend.speedKn} kn)", bend.speedKn > 5.0)
    }

    /**
     * **Scope 3** — the floor is a target. A 90° bend whose room (10 m) cannot fit the floor's own
     * ~23 m tangent is still curved — at the largest radius whose **drawn** curve reaches no further
     * than the 10 m room, taken below the 5 kn floor. Assumes `AppConfig`'s code defaults, the
     * properties not being loaded in a JVM test: `lateralAccelMps2` 0.33 m/s², `minSpeedKn` 5 kn,
     * `transitionSec` 2 s, `reachFraction` 1.0; the fitted ceiling is ≈ 3.16 kn.
     */
    @Test
    fun aBendTooRoomTightForTheFloorIsStillCurved() {
        val corner = LatLng(43.5, 7.0)
        val prev = offset(corner, 0.0, -10.0)
        val next = offset(corner, 100.0, 0.0)
        val rounded = RouteCornerPass.round(
            listOf(prev, corner, next), 28.0, CoastWorld(emptyList()), null, 50.0
        )
        val verdict = rounded.verdicts.single()
        assertEquals(
            "the bend is drawn, not refused",
            RouteCornerPass.CornerOutcome.ROUNDED, verdict.outcome
        )
        assertEquals("the room is the whole shorter leg", 10.0, verdict.reachAskM, 0.5)
        assertTrue("the corner vertex is replaced by the arc", corner !in rounded.points)
        // The fit is the drawn curve's own tangent, spirals included — not the shorter circular arc's —
        // so the ceiling is the ~1.625 m/s that fitted radius allows, not the arc-only ≈ 1.817 m/s.
        val expectedKn = Units.mpsToKnots(1.6245)
        val ceilings = rounded.points.mapNotNull { rounded.ceilingKnAt(it) }
        assertTrue("a ceiling is written across the arc", ceilings.isNotEmpty())
        for (c in ceilings) {
            assertTrue("the ceiling is below the 5 kn floor", c < 5.0)
            assertEquals("at the widest radius whose drawn curve fits", expectedKn, c, 0.05)
        }
        // The drawn curve's own tangent, read off the line as the corner-to-endpoint distances: both
        // must stay inside the 10 m room the curve was fitted to. The old arc-only formula reached
        // ~12.2 m past the corner, which is the defect this assertion now guards.
        val curve = rounded.points.subList(1, rounded.points.size - 1)
        val startReach = SpatialOperations.haversine(corner, curve.first())
        val endReach = SpatialOperations.haversine(corner, curve.last())
        assertTrue("the drawn curve begins within the room", startReach <= 10.0 + 0.05)
        assertTrue("the drawn curve ends within the room", endReach <= 10.0 + 0.05)
        assertTrue("and the room is used, not wasted", maxOf(startReach, endReach) >= 10.0 - 0.5)
    }

    /**
     * **Scope 3's guard** — a near-reversal whose room (3 m) fits no radius above the pass's own 1 kn
     * bottom is still left standing, ceiling at the floor. Assumes `AppConfig`'s code defaults: at 170°
     * the drawn fit's tangent reaches the 3 m room at only ≈ 0.44 kn, under the 1 kn bottom — so this
     * parked shape reaches the guard but cannot pin the bottom's value, which
     * [aBendAtTheHardBottomCurvesAndAHairLessStands] does with a plain bend.
     */
    @Test
    fun aBendNoRoomCanCurveAboveTheHardBottomIsLeftStanding() {
        val corner = LatLng(43.5, 7.0)
        val prev = offset(corner, 0.0, -3.0)
        val next = offset(corner, 50.0 * sin(Math.toRadians(170.0)), 50.0 * cos(Math.toRadians(170.0)))
        val rounded = RouteCornerPass.round(
            listOf(prev, corner, next), 28.0, CoastWorld(emptyList()), null, 50.0
        )
        val verdict = rounded.verdicts.single()
        assertEquals(
            "the room fits no radius above the hard bottom",
            RouteCornerPass.CornerOutcome.FLOOR_ROOM, verdict.outcome
        )
        assertTrue("the bend is left standing as its bare vertex", corner in rounded.points)
        assertEquals("taken at the turn floor", 5.0, rounded.ceilingKnAt(corner)!!, 0.01)
        assertEquals("the verdict reports the floor", 5.0, verdict.speedKn, 0.01)
    }

    /**
     * **Scope 3's guard, pinned by value** — the bottom is the pass's own 1 kn and nothing looser. A
     * 140° bend whose room exactly holds the drawn curve at 1.02 kn is still curved, its ceiling just
     * above the bottom; the same bend given a hair less room — one that holds only a 0.98 kn curve — is
     * left standing. The pair brackets the guard at 1 kn, which the parked near-reversal alone cannot.
     * Assumes `AppConfig`'s code defaults: `lateralAccelMps2` 0.33 m/s², `transitionSec` 2 s.
     */
    @Test
    fun aBendAtTheHardBottomCurvesAndAHairLessStands() {
        val corner = LatLng(43.5, 7.0)
        val next = offset(corner, 50.0 * sin(Math.toRadians(140.0)), 50.0 * cos(Math.toRadians(140.0)))
        // The drawn tangent a 140° curve needs at 1.02 kn (≈3.288 m) and at 0.98 kn (≈3.086 m).
        val curvedRoom = 3.2878
        val standingRoom = 3.0862

        val curved = RouteCornerPass.round(
            listOf(offset(corner, 0.0, -curvedRoom), corner, next),
            28.0, CoastWorld(emptyList()), null, 50.0
        )
        assertEquals(
            "a curve just above the hard bottom is drawn",
            RouteCornerPass.CornerOutcome.ROUNDED, curved.verdicts.single().outcome
        )
        val ceiling = curved.points.mapNotNull { curved.ceilingKnAt(it) }.first()
        assertEquals("its ceiling is the bottom, not the floor", 1.02, ceiling, 0.05)

        val standing = RouteCornerPass.round(
            listOf(offset(corner, 0.0, -standingRoom), corner, next),
            28.0, CoastWorld(emptyList()), null, 50.0
        )
        assertEquals(
            "a hair less room leaves it standing",
            RouteCornerPass.CornerOutcome.FLOOR_ROOM, standing.verdicts.single().outcome
        )
        assertTrue("the bend stands as its bare vertex", corner in standing.points)
        assertEquals("and is taken at the turn floor", 5.0, standing.ceilingKnAt(corner)!!, 0.01)
    }

    /** A point [eastM] metres east and [northM] metres north of [base], on the pass's own sphere. */
    private fun offset(base: LatLng, eastM: Double, northM: Double): LatLng {
        val mLat = SpatialOperations.EARTH_RADIUS_M * Math.PI / 180.0
        val mLon = mLat * cos(Math.toRadians(base.latitude))
        return LatLng(base.latitude + northM / mLat, base.longitude + eastM / mLon)
    }

    /** A world whose coast is one polyline; optional zones; depth always clear. */
    private class CoastWorld(
        private val coast: List<LatLng>,
        private val zones: List<SpeedZone> = emptyList()
    ) : MultipassWorld {
        override val coastlineReady = true
        override val depthReady = true
        override val bandWidthM = 0.0
        override val regionBounds: BBox? = null

        override fun segmentsIn(box: BBox): List<MultipassEdge> = emptyList()
        override fun openCoastIn(box: BBox): List<List<LatLng>> = emptyList()
        override fun isWater(latitude: Double, longitude: Double) = true
        override fun speedZonesIn(box: BBox): List<SpeedZone> = speedZonesInBox(zones, box, emptySet())
        override fun zoneLimitKnAt(latitude: Double, longitude: Double): Double? =
            strictestLimitKnAt(zones, emptySet(), latitude, longitude)
        override fun zoneDisplayLimitKnAt(latitude: Double, longitude: Double): Double? =
            strictestDisplayLimitKnAt(zones, emptySet(), latitude, longitude)
        override fun depthAt(latitude: Double, longitude: Double): DepthSample =
            DepthSample(20f, DepthSource.LITTO3D, 100, true)

        override fun distanceToCoastM(latitude: Double, longitude: Double): Double {
            val p = LatLng(latitude, longitude)
            var best = Double.MAX_VALUE
            for (i in 0 until coast.size - 1) {
                val d = SpatialOperations.pointToSegmentDistance(p, coast[i], coast[i + 1])
                if (d < best) best = d
            }
            return best
        }
    }
}
