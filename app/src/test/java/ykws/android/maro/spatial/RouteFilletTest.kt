package ykws.android.maro.spatial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.RoutePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * The fillet, over synthetic lines rather than a baked mesh: the pass is pure, so a corner, a
 * half-blocked corner and a corner with no room at all are three arguments, not three baskes.
 *
 * The water test is a lambda here, which is the whole point of the pass taking one: the geometry it
 * carries is the only thing under test, and where the water really is stays the mesh's own answer.
 *
 * **The corners are not all right angles, on purpose.** The fillet built its arc with the tangent
 * point at `R / tan(θ / 2)` and its centre at `R / sin(θ / 2)` — both wrong, and *identical* to the
 * right expressions at 90°, where `sin`, `cos` and `tan` of 45° all read 1. A suite whose every case
 * sat at a right angle therefore could not see that the pass had never drawn a curve, so the 60° and
 * 120° cases below are the ones that hold the geometry in place.
 */
class RouteFilletTest {

    private fun latOf(yM: Double): Double = ORIGIN_LAT + yM / M_PER_DEG_LAT
    private fun lonOf(xM: Double): Double = ORIGIN_LON + xM / M_PER_DEG_LON
    private fun point(xM: Double, yM: Double): RoutePoint = RoutePoint(latOf(yM), lonOf(xM))
    private fun xOf(point: RoutePoint): Double = (point.longitude - ORIGIN_LON) * M_PER_DEG_LON
    private fun yOf(point: RoutePoint): Double = (point.latitude - ORIGIN_LAT) * M_PER_DEG_LAT

    /**
     * A leg's own speed (m/s) at [knots] — what the search hands the fillet.
     *
     * It is the leg's *plain* speed, free of the turn's price: the fillet reads its radius from this,
     * and a plan whose leg times were used instead would shrink the arc by exactly the comfort charge
     * the corners cost.
     */
    private fun legSpeed(knots: Double): Double = knots * Units.MPS_PER_KNOT

    /** The radius `R = v² / a` the cap implies at a speed — the test's own arithmetic, not the pass's. */
    private fun idealRadiusM(knots: Double, accelMps2: Double): Double {
        val speedMps = legSpeed(knots)
        return speedMps * speedMps / accelMps2
    }

    /** A water test that refuses everything inside the given metre-frame box. */
    private fun blocked(x0: Double, x1: Double, y0: Double, y1: Double) =
        { latitude: Double, longitude: Double ->
            val x = (longitude - ORIGIN_LON) * M_PER_DEG_LON
            val y = (latitude - ORIGIN_LAT) * M_PER_DEG_LAT
            !(x in x0..x1 && y in y0..y1)
        }

    /** The worst heading change the smoothed line still asks for, in degrees. */
    private fun worstTurnDeg(points: List<RoutePoint>): Double {
        var worst = 0.0
        for (i in 1 until points.size - 1) {
            val incoming = heading(points[i - 1], points[i])
            val outgoing = heading(points[i], points[i + 1])
            var turn = outgoing - incoming
            while (turn > PI) turn -= 2.0 * PI
            while (turn < -PI) turn += 2.0 * PI
            worst = maxOf(worst, abs(turn))
        }
        return worst * 180.0 / PI
    }

    /**
     * The line's **total** heading change, in degrees.
     *
     * An arc that meets both legs tangentially is sampled at equal steps, so each junction turns by
     * half a step and each interior chord by a whole one; the sum is the sweep the arc subtends. That
     * is what makes "the sweep equals the turn" a reading off the drawn line rather than off the
     * pass's own bookkeeping.
     */
    private fun totalTurnDeg(points: List<RoutePoint>): Double {
        var total = 0.0
        for (i in 1 until points.size - 1) {
            val incoming = heading(points[i - 1], points[i])
            val outgoing = heading(points[i], points[i + 1])
            var turn = outgoing - incoming
            while (turn > PI) turn -= 2.0 * PI
            while (turn < -PI) turn += 2.0 * PI
            total += abs(turn)
        }
        return total * 180.0 / PI
    }

    private fun heading(from: RoutePoint, to: RoutePoint): Double =
        atan2(
            (to.longitude - from.longitude) * cos(from.latitude * PI / 180.0),
            to.latitude - from.latitude
        )

    // ── The arc the cap implies ───────────────────────────────────────────────

    @Test
    fun aRightAngleInOpenWaterTakesTheRadiusTheCapImplies() {
        // East 400 m, then north 400 m at 28 kn: the cap's own radius at that speed is 138 m, so the
        // arc begins 138 m short of the vertex and the whole corner becomes one gentle sweep.
        val a = point(0.0, 0.0)
        val v = point(400.0, 0.0)
        val b = point(400.0, 400.0)
        val speeds = listOf(legSpeed(28.0), legSpeed(28.0))

        val result = RouteFillet.apply(listOf(a, v, b), speeds, ACCEL_MPS2) { _, _ -> true }

        assertEquals("the corner should be smoothed at the ideal radius", 1, result.smoothed)
        assertEquals(0, result.reduced)
        assertEquals(0, result.sharp)
        assertTrue("the corner must become an arc, not a clipped corner", result.points.size > 5)

        val idealRadius = idealRadiusM(28.0, ACCEL_MPS2)
        val entry = result.points[1]
        assertEquals("the arc starts one radius back along the incoming leg", 400.0 - idealRadius, xOf(entry), 2.0)
        assertEquals("and on the leg itself", 0.0, yOf(entry), 2.0)

        // A 90° turn spread over a 138 m arc is about 4° a sample: the line reads as a curve.
        assertTrue(
            "the smoothed line should ask for no sharp change, got ${worstTurnDeg(result.points)}°",
            worstTurnDeg(result.points) < 8.0
        )
    }

    /**
     * A gentler bend than a right angle — the sweep the old construction drove to 102° at 30°.
     *
     * The four sweeps the old pair of expressions produced — about 102° at a 30° corner, 106° at 60°,
     * 90° at 90° and 48° at 120°, none of them the turn — are why the pass refused an arc at every
     * candidate radius, radius-independently, and why 425 of 444 corners read back "no arc".
     */
    @Test
    fun aThirtyDegreeCornerIsRoundedByTheArcTheCapImplies() {
        assertCornerGeometry(30.0)
    }

    /**
     * A 60° corner: the arc's tangent point stands `R · tan(θ / 2)` back along the leg and the arc
     * sweeps the 60° itself.
     *
     * At 90° the old construction and the right one agree — `R / tan(45°)` is `R · tan(45°)` — so the
     * cases either side of 90° are the only ones that tell them apart.
     */
    @Test
    fun aSixtyDegreeCornerIsRoundedByTheArcTheCapImplies() {
        assertCornerGeometry(60.0)
    }

    /** A 120° corner: the same two readings, and the old pair gave a 48° sweep here. */
    @Test
    fun aHundredAndTwentyDegreeCornerIsRoundedByTheArcTheCapImplies() {
        assertCornerGeometry(120.0)
    }

    /**
     * The reading both non-right-angle cases share: the arc's tangent point stands `R · tan(θ / 2)`
     * back along the incoming leg, and the sweep it draws is the turn itself.
     */
    private fun assertCornerGeometry(turnDeg: Double) {
        val legM = 1_000.0
        val knots = 28.0
        val turnRad = turnDeg * PI / 180.0
        val a = point(0.0, 0.0)
        val v = point(legM, 0.0)
        // The outgoing leg, turned by exactly [turnDeg] off the incoming one.
        val b = point(legM + legM * cos(turnRad), legM * sin(turnRad))
        val speeds = listOf(legSpeed(knots), legSpeed(knots))

        val result = RouteFillet.apply(listOf(a, v, b), speeds, ACCEL_MPS2) { _, _ -> true }

        assertEquals("the ideal arc must fit an open corner", 1, result.smoothed)
        assertEquals(0, result.reduced)
        assertEquals(0, result.sharp)

        val expectedCut = idealRadiusM(knots, ACCEL_MPS2) * tan(turnRad / 2.0)
        val entry = result.points[1]
        assertEquals(
            "the arc must begin R·tan(θ/2) = ${"%.0f".format(expectedCut)} m back along the leg",
            legM - expectedCut,
            xOf(entry),
            2.0
        )
        assertEquals("and on the leg itself", 0.0, yOf(entry), 2.0)
        assertEquals(
            "the arc's sweep must be the turn",
            turnDeg,
            totalTurnDeg(result.points),
            1.0
        )
    }

    /**
     * **The limit each drawn sub-leg inherits, at the level the arc makes.**
     *
     * The pass hands out one limit per output leg, and the rule is inheritance: a straight run takes
     * the limit of the chain leg it lies on, and an arc's own chords — which belong to neither leg
     * alone — take the **more restrictive** of the two they cut back along. That list is what the
     * plan's clock prices the drawn line with, so a sub-leg carrying the wrong limit is an ETA about a
     * line nobody draws — and nothing else in the suite reads this list at all.
     */
    @Test
    fun anArcsChordsCarryTheMoreRestrictiveOfTheTwoLegsTheySpan() {
        val a = point(0.0, 0.0)
        val v = point(1_000.0, 0.0)
        val b = point(1_000.0, 1_000.0)
        val speeds = listOf(legSpeed(28.0), legSpeed(10.0))

        val result = RouteFillet.apply(
            listOf(a, v, b),
            speeds,
            ACCEL_MPS2,
            legLimitKn = listOf(28.0, 10.0),
            insideWater = { _, _ -> true }
        )

        assertEquals("the corner takes an arc", 1, result.smoothed)
        assertEquals(
            "one limit per leg of the drawn line",
            result.points.size - 1,
            result.legLimitKn.size
        )
        assertEquals(
            "the run along the incoming leg carries that leg's own limit",
            28.0,
            result.legLimitKn.first(),
            0.0
        )
        assertTrue("the arc is drawn as several chords", result.legLimitKn.size > 4)
        for (index in 1 until result.legLimitKn.size) {
            assertEquals(
                "every chord spans both legs, so it carries the slower one, at $index",
                10.0,
                result.legLimitKn[index],
                0.0
            )
        }
    }

    // ── When there is no room ─────────────────────────────────────────────────

    @Test
    fun aCornerWithNoRoomStaysSharpAndIsCounted() {
        val a = point(0.0, 0.0)
        val v = point(400.0, 0.0)
        val b = point(400.0, 400.0)
        val speeds = listOf(legSpeed(28.0), legSpeed(28.0))
        // Everything the arc would occupy is refused, so no radius can fit and the corner stays.
        val noRoom = blocked(250.0, 450.0, -50.0, 200.0)

        val result = RouteFillet.apply(listOf(a, v, b), speeds, ACCEL_MPS2, insideWater = noRoom)

        assertEquals(0, result.smoothed)
        assertEquals(0, result.reduced)
        assertEquals("the refused corner must be counted, never silently softened", 1, result.sharp)
        assertEquals("and the line must be the one the search drew", listOf(a, v, b), result.points)
    }

    @Test
    fun aCornerWithPartialRoomTakesTheLargestRadiusThatFits() {
        val a = point(0.0, 0.0)
        val v = point(400.0, 0.0)
        val b = point(400.0, 400.0)
        val speeds = listOf(legSpeed(28.0), legSpeed(28.0))
        // A thin block right where the ideal arc's own tangent point stands: the ideal radius cannot
        // be drawn, and the halved one can.
        val partlyBlocked = blocked(255.0, 275.0, 0.0, 10.0)

        val result =
            RouteFillet.apply(listOf(a, v, b), speeds, ACCEL_MPS2, insideWater = partlyBlocked)

        assertEquals("the ideal radius must be refused", 0, result.smoothed)
        assertEquals("a smaller arc fits and is drawn", 1, result.reduced)
        assertEquals(0, result.sharp)
        // Halved once: 69 m, so the arc starts about 331 m along the leg.
        assertEquals(331.0, xOf(result.points[1]), 3.0)
    }

    // ── Nothing to do ─────────────────────────────────────────────────────────

    @Test
    fun aStraightLineIsHandedBackUntouched() {
        val a = point(0.0, 0.0)
        val v = point(200.0, 0.0)
        val b = point(400.0, 0.0)
        val speeds = listOf(legSpeed(28.0), legSpeed(20.0))

        val result = RouteFillet.apply(listOf(a, v, b), speeds, ACCEL_MPS2) { _, _ -> true }

        assertEquals(listOf(a, v, b), result.points)
        assertEquals(0, result.smoothed + result.reduced + result.sharp)
    }

    @Test
    fun anArcWhoseWaterIsRefusedLeavesTheCornerSharpAndSaysTheWaterDidIt() {
        // The water the test refuses is the corner's own inside — a zone interior the chain went
        // around reads exactly like this, and the search composes that rule into the very lambda it
        // hands this pass. No radius fits, so the corner stays sharp, and the count names the water as
        // what refused it rather than the legs: a refusal count that never mentions the constraint the
        // refusal claims is a bug report, not a measurement.
        val a = point(0.0, 0.0)
        val v = point(1_000.0, 0.0)
        val b = point(1_000.0, 1_000.0)
        val speeds = listOf(legSpeed(28.0), legSpeed(28.0))
        val insideTheCorner = { latitude: Double, longitude: Double ->
            val x = (longitude - ORIGIN_LON) * M_PER_DEG_LON
            val y = (latitude - ORIGIN_LAT) * M_PER_DEG_LAT
            !(x > 700.0 && y < 300.0)
        }

        val result = RouteFillet.apply(
            listOf(a, v, b),
            speeds,
            ACCEL_MPS2,
            insideWater = insideTheCorner
        )

        assertEquals("no arc was drawn", 0, result.smoothed)
        assertEquals(0, result.reduced)
        assertEquals("the corner stays sharp", 1, result.sharp)
        assertEquals("and the water is what refused it", 1, result.sharpNoWater)
    }

    @Test
    fun anArcThePriceRefusesIsCountedAsThePricesAndLeavesTheCornerSharp() {
        // Open water and a price that refuses every radius — the shape a corner between a fast leg and
        // a slow one has, where the whole arc pays the slow limit and the chain paid only that side's
        // cutback. The arc **is** built and **is** sampled before the price is asked, which is what
        // makes "sound and on water" a fact this count rests on rather than a claim about it; and the
        // corner is counted as the price's own refusal rather than as the water's or the legs'.
        val a = point(0.0, 0.0)
        val v = point(1_000.0, 0.0)
        val b = point(1_000.0, 1_000.0)
        val speeds = listOf(legSpeed(28.0), legSpeed(28.0))

        val result = RouteFillet.apply(
            listOf(a, v, b),
            speeds,
            ACCEL_MPS2,
            arcAdmissible = { _, _, _, _ -> false },
            insideWater = { _, _ -> true }
        )

        assertEquals("no arc was drawn", 0, result.smoothed)
        assertEquals("the corner stays sharp", 1, result.sharp)
        assertEquals("and the price is what refused it", 1, result.sharpNoPrice)
    }

    @Test
    fun aCapAtOrBelowZeroIsANoOp() {
        val a = point(0.0, 0.0)
        val v = point(400.0, 0.0)
        val b = point(400.0, 400.0)
        val speeds = listOf(legSpeed(28.0), legSpeed(28.0))

        val result = RouteFillet.apply(listOf(a, v, b), speeds, 0.0) { _, _ -> true }

        assertEquals(listOf(a, v, b), result.points)
        assertEquals(0, result.smoothed + result.reduced + result.sharp)
    }

    private companion object {
        const val ORIGIN_LAT = 43.5
        const val ORIGIN_LON = 7.0

        /**
         * The cap these cases draw at (m/s²) — **1.5, and not the 0.5 the app ships**.
         *
         * The number is the sweep's first choice rather than the shipped one, and the difference is
         * geometric: the shipped 0.5 asks a 415 m radius at 28 kn, which these hand-written 400 m legs
         * cannot carry (`MAX_CUTBACK_FRACTION` of 400 m is 180 m of tangent), so every case would read
         * its geometry off a halved radius instead of the ideal one the case is about. Each case
         * states the radius its own legs allow; the cap's own sweep is the trajectory probe's.
         */
        const val ACCEL_MPS2 = 1.5

        val M_PER_DEG_LAT: Double = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val M_PER_DEG_LON: Double = M_PER_DEG_LAT * cos(ORIGIN_LAT * PI / 180.0)
    }
}
