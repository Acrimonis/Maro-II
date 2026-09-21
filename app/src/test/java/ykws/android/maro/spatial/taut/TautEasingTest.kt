package ykws.android.maro.spatial.taut

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.spatial.RoutePlanTiming
import ykws.android.maro.spatial.RouteTurnGeometry
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units

/**
 * **The corner's own readings: the ladder, the chords, and the speed the tightest point sets.**
 *
 * The four properties pinned here are the ones the design chose rather than derived: the curve is
 * emitted as chords stepping the shorter of ten metres and ten degrees of arc; the fit test shortens the
 * transition **before** it reduces the radius; the speed is `√(a·r)` at the tightest point; and the
 * corner's price is the drawn clock's own reading of the same geometry. Each is stated as a number a
 * human can check, so a regression in any of them is a failing test rather than a line that looks round.
 */
class TautEasingTest {

    private val latitude0 = 43.55
    private val longitude0 = 7.12
    private val accel = 2.94
    private val cruiseKn = 28.0
    private val frame = Frame(latitude0, longitude0)

    private val metresPerDegreeLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    private val metresPerDegreeLon = metresPerDegreeLat * cos(latitude0 * PI / 180.0)

    private fun point(eastM: Double, northM: Double): RoutePoint = RoutePoint(
        latitude0 + northM / metresPerDegreeLat,
        longitude0 + eastM / metresPerDegreeLon
    )

    private fun fit(
        legM: Double,
        fits: (List<RoutePoint>) -> TautEasing.SharpCause? = { null }
    ): TautEasing.Turn = TautEasing.fit(
        frame = frame,
        previous = point(-legM, 0.0),
        vertex = point(0.0, 0.0),
        next = point(0.0, legM),
        cruiseSpeedKn = cruiseKn,
        referenceLimitKn = Double.MAX_VALUE,
        lateralAccelMps2 = accel,
        fits = fits
    )

    @Test
    fun `the curve closes on its own legs and steps the shorter of ten metres and ten degrees`() {
        val turn = fit(legM = 600.0)
        val idealRadius = RouteTurnGeometry.radiusM(
            Units.knotsToMps(cruiseKn), accel
        )
        assertEquals("a 90° corner in open water takes the easing", TautEasing.Kind.SPIRAL, turn.kind)
        assertEquals(
            "the peak radius is the one the ceiling implies at the cruise speed",
            idealRadius,
            turn.radiusM,
            1e-6
        )
        assertEquals(
            "so the tightest point is driven at the cruise speed",
            Units.knotsToMps(cruiseKn),
            turn.speedMps,
            1e-6
        )

        val previous = point(-600.0, 0.0)
        val vertex = point(0.0, 0.0)
        val next = point(0.0, 600.0)
        assertTrue(
            "the entry tangent point stands on the leg arriving",
            offLine(turn.emitted.first(), previous, vertex) < 0.05
        )
        assertTrue(
            "the exit tangent point stands on the leg leaving",
            offLine(turn.emitted.last(), vertex, next) < 0.05
        )

        var longestChord = 0.0
        var widestStep = 0.0
        for (i in 0 until turn.emitted.size - 1) {
            longestChord = maxOf(longestChord, metres(turn.emitted[i], turn.emitted[i + 1]))
        }
        for (i in 1 until turn.emitted.size - 1) {
            widestStep = maxOf(
                widestStep,
                RouteTurnGeometry.turnRadians(
                    turn.emitted[i - 1], turn.emitted[i], turn.emitted[i + 1]
                ) * 180.0 / PI
            )
        }
        assertTrue("no chord is longer than 10 m (read $longestChord)", longestChord <= 10.05)
        assertTrue("no chord turns more than 10° (read $widestStep)", widestStep <= 10.05)
    }

    @Test
    fun `the fit test shortens the transition before it reduces the radius`() {
        // 200 m legs leave a 90 m cutback: the full spiral's own cut does not fit, the plain arc's does.
        val turn = fit(legM = 200.0)
        val idealRadius = RouteTurnGeometry.radiusM(Units.knotsToMps(cruiseKn), accel)
        assertEquals(
            "the transition gives way, not the radius: the arc is what fits at the ideal radius",
            TautEasing.Kind.ARC,
            turn.kind
        )
        assertEquals(idealRadius, turn.radiusM, 1e-6)
        assertTrue("the arc's cut stands inside the legs' own limit", turn.cutM <= 90.0 + 1e-6)
    }

    @Test
    fun `a corner with no room reduces the radius rather than refusing`() {
        val turn = fit(legM = 40.0)
        val idealRadius = RouteTurnGeometry.radiusM(Units.knotsToMps(cruiseKn), accel)
        assertTrue("a radius was still found", turn.kind != TautEasing.Kind.SHARP)
        assertTrue("and it is tighter than the ideal one", turn.radiusM < idealRadius)
        assertTrue("never below the floor", turn.radiusM >= 2.0)
        assertTrue("and it fits the legs' cutback", turn.cutM <= 18.0 + 1e-6)
        assertEquals(
            "the speed is √(a·r) at the tightest point",
            kotlin.math.sqrt(accel * turn.radiusM),
            turn.speedMps,
            1e-6
        )
        assertTrue("which is slower than the cruise", turn.speedMps < Units.knotsToMps(cruiseKn))
    }

    @Test
    fun `a corner the water refuses keeps its sharp vertex`() {
        val turn = fit(legM = 600.0) { TautEasing.SharpCause.WATER }
        assertEquals(TautEasing.Kind.SHARP, turn.kind)
        assertEquals("one point: the vertex the search found", 1, turn.emitted.size)
        assertEquals(0.0, turn.radiusM, 0.0)
        assertEquals(0, turn.chords)
    }

    /**
     * **A sharp corner is not a free corner** (§17 item 2).
     *
     * The corner the water refuses is driven at the ladder's own floor, `√(a · r_floor)` — never above
     * the legs' speed — so the field the price and the assembly both read carries a real speed rather
     * than zero. It is an **upper** bound on the corner speed for the water case (the radius the water
     * refused is somewhere below the floor, so the real slowdown is at least the one charged) and a bare
     * number for the other three causes, named as such.
     *
     * Control: the same corner with the longitudinal ceiling removed from the reading carries no charge at
     * all, so the number below is the term and not a constant.
     */
    @Test
    fun `a sharp corner is charged at the ladder's own floor`() {
        val turn = fit(legM = 600.0) { TautEasing.SharpCause.WATER }
        val floorSpeed = kotlin.math.sqrt(accel * TautEasing.MIN_RADIUS_M)
        assertEquals("the water case keeps the ladder's floor as its speed", floorSpeed, turn.speedMps, 1e-9)
        assertTrue(
            "and it is an upper bound on the corner speed, hence a lower bound on the slowdown: " +
                "${turn.speedMps} against the legs' ${Units.knotsToMps(cruiseKn)}",
            turn.speedMps < Units.knotsToMps(cruiseKn)
        )

        val previous = point(-600.0, 0.0)
        val vertex = point(0.0, 0.0)
        val next = point(0.0, 600.0)
        val charged = turn.priceSec(
            previous, vertex, next, Double.MAX_VALUE, cruiseKn, accel, 1.96
        )
        val free = turn.priceSec(
            previous, vertex, next, Double.MAX_VALUE, cruiseKn, accel, 0.0
        )
        assertTrue(
            "the corner pays §12.3's pair, and the pair is what it pays: $charged against $free",
            charged > free + 1.0
        )
        val handBrake = Units.knotsToMps(cruiseKn) - floorSpeed
        val hand = 2.0 * handBrake * handBrake / (2.0 * 1.96 * floorSpeed)
        assertEquals("and the charge is the pair's own arithmetic, both sides at once", hand, charged - free, 1e-6)
    }

    /**
     * **The four causes are told apart** (§17 item 2), because only the water case is a water answer and
     * only for it is a number read off the failed radius meaningful at all.
     *
     * Four legs, one per cause: a straight-through corner is the angle guard, a 1 m leg cannot host a
     * curve that meets both its legs, a 10 m leg cannot host one that fits the cutback, and a corner the
     * fit test refuses is the water's own answer. Control: the ease in the last case would have returned a
     * curve, so the cause read is the refusal's rather than the ladder's default.
     */
    @Test
    fun `a refusal names its own cause, and the four are not one number`() {
        val guard = fit(legM = 600.0)
        assertTrue(
            "the guard is not reached here — the 90° corner eases",
            guard.kind == TautEasing.Kind.SPIRAL || guard.kind == TautEasing.Kind.ARC
        )
        val straight = TautEasing.fit(
            frame,
            previous = point(-600.0, 0.0),
            vertex = point(0.0, 0.0),
            next = point(600.0, 0.0),
            cruiseSpeedKn = cruiseKn,
            referenceLimitKn = Double.MAX_VALUE,
            lateralAccelMps2 = accel,
            fits = { null }
        )
        assertEquals("a corner that is not a corner is the guard's own refusal", TautEasing.SharpCause.GUARD, straight.sharpCause)

        val tooShort = fit(legM = 1.0)
        assertEquals(TautEasing.Kind.SHARP, tooShort.kind)
        assertEquals(
            "a leg with no room for a curve that meets both legs is the build's",
            TautEasing.SharpCause.BUILD,
            tooShort.sharpCause
        )

        // 3 m legs against a 0.45 cutback fraction: every radius the ladder can still try cuts back
        // further than the leg allows, while the curve itself does meet both legs.
        val noCutback = fit(legM = 3.0)
        assertEquals(TautEasing.Kind.SHARP, noCutback.kind)
        assertEquals(
            "a leg too short for every candidate's cutback is the cutback's",
            TautEasing.SharpCause.CUTBACK,
            noCutback.sharpCause
        )

        val water = fit(legM = 600.0) { TautEasing.SharpCause.WATER }
        assertEquals(
            "and a curve the fit test refuses is the water's own answer",
            TautEasing.SharpCause.WATER,
            water.sharpCause
        )

        // **The rule's own refusal is not pinned here any more** (item 3 of the level above). A lambda
        // handed to the fit test can only assert that the plumbing passes a cause out — and one such pin
        // sat here and in the reference Dijkstra while `sharpByRule` was nought everywhere it could be
        // non-zero. It is now **produced** on a world where a corner's rounded chords really enter a
        // zone's interior, counted and charged in `TautAssertionsTest`: one case, on water, replacing
        // both the lambda-only pin and the duplicated predicate.
        assertEquals(
            "the water's own refusal is the one this file can still make, and it is the water's",
            TautEasing.SharpCause.WATER,
            water.sharpCause
        )
    }

    @Test
    fun `the corner's price is the drawn clock's own reading, never below free`() {
        val turn = fit(legM = 600.0)
        val previous = point(-600.0, 0.0)
        val vertex = point(0.0, 0.0)
        val next = point(0.0, 600.0)
        val price = turn.priceSec(
            previous, vertex, next, Double.MAX_VALUE, cruiseKn, accel
        )
        assertTrue("a rounded corner is never cheaper than free", price >= 0.0)

        // The same quantity, recomputed by hand off the drawn clock: the sub-polyline's own seconds at
        // the limits the turn names, less the two legs the turn replaced.
        val subPoints = listOf(previous) + turn.emitted + listOf(next)
        val subLimits = listOf(Double.MAX_VALUE) + turn.limitsKn + listOf(Double.MAX_VALUE)
        val drawn = RoutePlanTiming
            .drawnLegSeconds(subPoints, subLimits, cruiseKn, accel)
            .sum()
        val replaced = RoutePlanTiming.legSeconds(
            metres(previous, vertex), Double.MAX_VALUE, cruiseKn
        ) + RoutePlanTiming.legSeconds(metres(vertex, next), Double.MAX_VALUE, cruiseKn)
        assertEquals("the price is that difference, clamped at zero", maxOf(0.0, drawn - replaced), price, 1e-9)

        val sharp = TautEasing.fit(
            frame, previous, vertex, next, cruiseKn,
            Double.MAX_VALUE, accel,
            // Named rather than trailing: the fit takes an optional meter after the test, so a positional
            // lambda would land on the meter instead (§19.2 item 2).
            fits = { TautEasing.SharpCause.WATER }
        )
        val sharpPrice = sharp.priceSec(
            previous, vertex, next, Double.MAX_VALUE, cruiseKn, accel
        )
        assertTrue(
            "and an eased corner costs less than the sharp one it replaces ($price against $sharpPrice)",
            price < sharpPrice
        )

        // **The stubs take the world's own nominal** (item 1). The clock is handed `∞` at both ends —
        // the caller's world resolves what is in force over the metres each stub really covers — and the
        // corner's own limit over its chords alone. A stub charged at the leg's dearest limit is the
        // ≈ 280 s phantom of a 1 000 m leg with a 100 m 5 kn clip, and this is the reading that catches
        // it: with the reference limit at a zone's own 5 kn, the world's nominal at either end must still
        // be `∞` rather than 5.
        val handed = ArrayList<List<Double>>()
        turn.priceSec(
            previous, vertex, next, 5.0, cruiseKn, accel, 0.0,
            inPriceSec = Double.MAX_VALUE,
            outPriceSec = Double.MAX_VALUE,
            clock = { _, limits ->
                handed.add(limits)
                0.0
            }
        )
        assertEquals("the clock is asked once, for one sub-polyline", 1, handed.size)
        val limits = handed.single()
        assertEquals("one limit per emitted piece", turn.emitted.size + 1, limits.size)
        assertEquals("the stub arriving is the world's own nominal", Double.MAX_VALUE, limits.first(), 0.0)
        assertEquals("the stub leaving too", Double.MAX_VALUE, limits.last(), 0.0)
        assertTrue(
            "and the chords alone carry the corner's own limit",
            limits.drop(1).dropLast(1).all { it == turn.limitKn }
        )
    }

    private fun offLine(point: RoutePoint, from: RoutePoint, to: RoutePoint): Double =
        SpatialOperations.pointToSegmentDistance(
            LatLng(point.latitude, point.longitude),
            LatLng(from.latitude, from.longitude),
            LatLng(to.latitude, to.longitude)
        )

    private fun metres(from: RoutePoint, to: RoutePoint): Double = SpatialOperations.haversine(
        LatLng(from.latitude, from.longitude),
        LatLng(to.latitude, to.longitude)
    )
}
