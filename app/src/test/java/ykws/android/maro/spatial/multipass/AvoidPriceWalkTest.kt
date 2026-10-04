package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min

/**
 * **The price walk's own reads** — the fine midpoints it keeps, the groups it prices from one reading
 * behind a declaration the sources make, and the reads it spares.
 *
 * The coarsening is arithmetic, not approximate: a proved group's product is *identically* the fine sum
 * it replaces, so every fixture here pins the **equality** of the returned double and the **saving** in
 * reads. The cases where the proof must fail are pinned too — a band's edge, a hole's edge — because a
 * mis-declared boundary moves the drawn line rather than crashing.
 */
class AvoidPriceWalkTest {

    /** `route.avoid.obstacle.marginM` as it ships — the walk's own fine step is half of it. */
    private val marginM = 25.0

    /** The coarse step these walks are handed — `avoid`'s own cell, eight times the fine step. */
    private val coarseStepM = 100.0

    /** The fine step the `avoid` plan walks at: `clearanceStep(25.0)` = 12.5 m. */
    private val fineStepM = MultipassPull.clearanceStep(marginM)

    private val paceKn = 28.0
    private val lambda = 2.0
    private val collarM = 100.0
    private val bandWidthM = 300.0
    private val bandOutsideMarginM = 50.0
    private val fraction = 0.66
    private val fullSec = 60.0

    /**
     * **The equivalence, exactly.** A constant price whose source declares its boundary far away: the
     * coarsened walk returns today's own double and pays strictly fewer reads. One test, two
     * assertions — the proof rather than a proxy for it.
     */
    @Test
    fun theCoarsenedPriceWalkReturnsTheSameDoubleFromFewerReads() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        var fineReads = 0
        var coarseReads = 0

        val fine = MultipassPull.softPriceSec(
            start, aim, marginM, fineStepM, flatField { fineReads++ }
        )
        val coarse = MultipassPull.softPriceSec(
            start, aim, marginM, coarseStepM, flatField { coarseReads++ }
        )

        assertEquals("the coarsened walk returns today's own double, not a near miss", fine, coarse, 0.0)
        assertTrue("and it pays strictly fewer price reads", coarseReads < fineReads)
        assertTrue("the fine walk is not vacuous", fineReads > 0)
    }

    /**
     * **The prefix is asserted, not inferred.** Its identity follows from the chord's by construction,
     * but the walk subtracts two prefix entries to get a span, so the arrays themselves are pinned.
     */
    @Test
    fun theCoarsenedPrefixEqualsTodaysPrefix() {
        val path = listOf(
            LatLng(CHORD_LAT, east(0.0)),
            LatLng(CHORD_LAT, east(700.0)),
            LatLng(CHORD_LAT + 0.002, east(1400.0))
        )

        val fine = MultipassPull.softPricePrefix(path, marginM, fineStepM, flatField {})
        val coarse = MultipassPull.softPricePrefix(path, marginM, coarseStepM, flatField {})

        assertArrayEquals("the coarsened prefix is today's prefix", fine, coarse, 0.0)
    }

    /**
     * **An unproved group keeps today's reads and today's verdict.** The chord runs along the band's
     * collar, 340 m off the coast, so its declaration `min(|d − 300|, |d − 350|)` is 10 m — far short of
     * a group's 50 m half-length. Every group is refused, every fine midpoint is read, and the pulled
     * line is the one today's walk draws.
     */
    @Test
    fun anUnprovedGroupAlongTheBandsEdgeKeepsTodaysReadsAndVerdict() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val mid = LatLng(CHORD_LAT, east(800.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        val path = listOf(start, mid, aim)
        var fineReads = 0
        var coarseReads = 0

        val fineSum = MultipassPull.softPriceSec(start, aim, marginM, fineStepM, bandEdgeField { fineReads++ })
        val coarseSum = MultipassPull.softPriceSec(start, aim, marginM, coarseStepM, bandEdgeField { coarseReads++ })

        assertEquals("the band's edge keeps today's sum", fineSum, coarseSum, 0.0)
        assertEquals("and every fine midpoint is still read", fineReads, coarseReads)
        assertTrue("the walk is not vacuous: the collar is priced", coarseSum > 0.0)

        val fineLine = MultipassPull.pull(path, start, aim, marginM, fineStepM, bandEdgeField {})
        val coarseLine = MultipassPull.pull(path, start, aim, marginM, coarseStepM, bandEdgeField {})
        assertEquals("and the chord's verdict is the one today's walk gives", fineLine, coarseLine)
    }

    /**
     * **A shallow patch inside a group is still refused.** The price half is coarsened (its source
     * proves every group) while the depth gate is a **step**, tested at every mark by
     * [`legClearCause`]. A walk that let the price's proof skip the gate's test would sail the patch.
     */
    @Test
    fun aShallowPatchInsideAGroupIsStillRefused() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val mid = LatLng(CHORD_LAT, east(800.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        val path = listOf(start, mid, aim)
        val field = RouteCostField(
            listOf(
                RouteCostSource.Hard(distanceAt = { _ -> Double.MAX_VALUE }),
                depthGateSource(3.0) { p -> if (shallowPatch(p)) 2.0 else Double.NaN },
                RouteCostSource.Soft(
                    priceSec = { 1.0 },
                    tag = MultipassCellState.ZONE,
                    clearanceAt = { CLEAR_OF_ANY_BOUNDARY_M }
                )
            )
        )

        val pulled = MultipassPull.pull(path, start, aim, marginM, coarseStepM, field)

        assertEquals("a shallow patch the coarse marks step over is still refused", path, pulled)
    }

    /**
     * **A hole's edge is a boundary like any other.** The chord runs through a holed ring's own hole, so
     * its arm changes twice across the hole's vertical edges. The declaration names every ring, so the
     * groups straddling those edges keep their fine reads — and with them the sum is exactly today's,
     * while the walks away from the edges are still proved.
     *
     * The control pins the review's defect: a declaration built from the shipped collar read alone walks
     * the outer ring and is blind to the hole, so it proves a group straddling the hole and misprices it.
     */
    @Test
    fun aHoleEdgeKeepsTheFineReadsAndIsNeverProved() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        var fineReads = 0
        var coarseReads = 0

        val fine = MultipassPull.softPriceSec(start, aim, marginM, fineStepM, holeField { fineReads++ })
        val coarse = MultipassPull.softPriceSec(start, aim, marginM, coarseStepM, holeField { coarseReads++ })

        assertEquals("the hole's edge keeps today's sum exactly", fine, coarse, 0.0)
        val steps = ceil(SpatialOperations.haversine(start, aim) / fineStepM).toInt()
        val perGroup = (coarseStepM / fineStepM).toInt()
        val groups = (steps + perGroup - 1) / perGroup
        assertTrue("groups away from the edges are proved", coarseReads < fineReads)
        assertTrue("while the groups across the hole keep their own reads", coarseReads > groups)

        val blind = MultipassPull.softPriceSec(start, aim, marginM, coarseStepM, holeField(blindToHoles = true) {})
        assertTrue(
            "a declaration blind to the hole would prove a group straddling it and move the sum",
            fine != blind
        )
    }

    /** **A proved group pays no hard read.** The flag the price walk discards is proven discarded. */
    @Test
    fun aProvedGroupPaysNoHardRead() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        var blockedTests = 0
        val field = RouteCostField(
            listOf(
                RouteCostSource.Hard(blockedAt = { blockedTests++; false }, distanceAt = { Double.MAX_VALUE }),
                RouteCostSource.Soft(
                    priceSec = { fullSec },
                    tag = MultipassCellState.ZONE,
                    clearanceAt = { CLEAR_OF_ANY_BOUNDARY_M }
                )
            )
        )

        MultipassPull.softPriceSec(start, aim, marginM, coarseStepM, field)

        assertEquals("the price walk never asks the walls' blocked test", 0, blockedTests)
        field.evaluate(start)
        assertEquals("while evaluate is the price body plus the hard loop, so the counter works", 1, blockedTests)
    }

    /**
     * **The zone declaration itself.** The outer ring, the holes and the collar edge are all named: a
     * point on the outer ring's collar edge declares 0, and the hole's own edge does too — the boundary
     * the shipped collar read, which walks the outer ring alone, cannot see.
     */
    @Test
    fun theZoneDeclarationNamesTheCollarEdgeAndTheHole() {
        val zones = listOf(holedZone)

        val onCollarEdge = LatLng(CHORD_LAT, east(800.0 - OUTER_HALF_X_M - collarM))
        assertEquals(
            "a point on the outer ring's collar edge declares no boundary",
            0.0,
            speedZonePriceClearanceM(zones, emptySet(), onCollarEdge.latitude, onCollarEdge.longitude, collarM),
            1.0
        )

        val onHoleEdge = LatLng(CHORD_LAT, east(800.0 - HOLE_HALF_X_M))
        assertEquals(
            "and the hole's own edge is a boundary like any other",
            0.0,
            speedZonePriceClearanceM(zones, emptySet(), onHoleEdge.latitude, onHoleEdge.longitude, collarM),
            1.0
        )

        val holeCentre = LatLng(CHORD_LAT, east(800.0))
        val declared = speedZonePriceClearanceM(zones, emptySet(), holeCentre.latitude, holeCentre.longitude, collarM)
        val outerOnly = ringDistanceM(holedZone.outerRing, holeCentre)
        assertTrue("the hole is seen, so its edge is nearer than the outer ring's", declared < outerOnly)
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /** A constant price whose source declares its nearest boundary far past any group's half-length. */
    private fun flatField(counting: () -> Unit): RouteCostField = RouteCostField(
        listOf(
            RouteCostSource.Soft(
                priceSec = { counting(); fullSec },
                tag = MultipassCellState.ZONE,
                clearanceAt = { CLEAR_OF_ANY_BOUNDARY_M }
            )
        )
    )

    /**
     * The band's own law off a synthetic straight coast 340 m from the chord: the collar arm where the
     * coast distance sits between the width and the reach, and the declaration those two circles imply.
     */
    private fun bandEdgeField(counting: () -> Unit): RouteCostField = RouteCostField(
        listOf(
            RouteCostSource.Soft(
                priceSec = { p ->
                    counting()
                    bandPriceAt(bandWidthM, bandOutsideMarginM, fullSec, fraction, bandDistanceM(p))
                },
                tag = MultipassCellState.BAND,
                clearanceAt = { p ->
                    val d = bandDistanceM(p)
                    min(abs(d - bandWidthM), abs(d - bandReachM(bandWidthM, bandOutsideMarginM)))
                }
            )
        )
    )

    /** The holed ring's own price and declaration — the declaration optionally blind to the holes. */
    private fun holeField(
        blindToHoles: Boolean = false,
        counting: () -> Unit = {}
    ): RouteCostField {
        val zones = listOf(holedZone)
        return RouteCostField(
            listOf(
                RouteCostSource.Soft(
                    priceSec = { p ->
                        counting()
                        val interior = strictestLimitKnAt(zones, emptySet(), p.latitude, p.longitude)
                        val collar = speedZoneCollarLimitKnAt(zones, emptySet(), p.latitude, p.longitude, collarM)
                        zonePriceAtLimits(50.0, paceKn, interior ?: 0.0, collar ?: 0.0, lambda, fraction)
                    },
                    tag = MultipassCellState.ZONE,
                    clearanceAt = { p ->
                        if (blindToHoles) {
                            // The shipped collar read's own blindness: the outer ring alone.
                            val d = ringDistanceM(holedZone.outerRing, p)
                            min(d, abs(d - collarM))
                        } else {
                            speedZonePriceClearanceM(zones, emptySet(), p.latitude, p.longitude, collarM)
                        }
                    }
                )
            )
        )
    }

    /** The coast is a straight line 340 m south of the chord, long enough to be a perpendicular foot. */
    private fun bandDistanceM(p: LatLng): Double =
        SpatialOperations.pointToSegmentDistance(p, COAST_A, COAST_B)

    /** A patch of 2 m water, 30 m across, centred on the path's middle — one coarse interval's width. */
    private fun shallowPatch(p: LatLng): Boolean =
        abs(p.latitude - CHORD_LAT) <= 15.0 / M_PER_DEG_LAT &&
            abs(p.longitude - east(800.0)) <= 15.0 / M_PER_DEG_LON

    private fun ringDistanceM(ring: List<LatLng>, p: LatLng): Double {
        if (ring.size < 2) return Double.MAX_VALUE
        var nearest = Double.MAX_VALUE
        for (i in 0 until ring.size - 1) {
            nearest = min(nearest, SpatialOperations.pointToSegmentDistance(p, ring[i], ring[i + 1]))
        }
        return nearest
    }

    private companion object {

        /** The chord's own parallel, so every fixture offsets in metres without a projection. */
        private const val CHORD_LAT = 43.50

        /** A declared boundary this far away is outside every group the fixture walks. */
        private const val CLEAR_OF_ANY_BOUNDARY_M = 10_000.0

        /** The outer ring's own half-width, and the hole's: 324 m wide and 222 m tall. */
        private const val OUTER_HALF_X_M = 650.0
        private const val HOLE_HALF_X_M = 162.0
        private const val HOLE_HALF_Y_M = 111.0

        private val M_PER_DEG_LAT = SpatialOperations.EARTH_RADIUS_M * PI / 180.0

        private val M_PER_DEG_LON = M_PER_DEG_LAT * cos(Math.toRadians(CHORD_LAT))

        /** A longitude [m] metres east of the chord's origin. */
        private fun east(m: Double): Double = 7.00 + m / M_PER_DEG_LON

        /** A latitude [m] metres south of the chord's parallel. */
        private fun south(m: Double): Double = CHORD_LAT - m / M_PER_DEG_LAT

        /** A latitude [m] metres north of the chord's parallel. */
        private fun north(m: Double): Double = CHORD_LAT + m / M_PER_DEG_LAT

        private val COAST_A = LatLng(south(340.0), east(-500.0))
        private val COAST_B = LatLng(south(340.0), east(2500.0))

        /**
         * The holed speed zone: an outer ring 1300 m wide and 890 m tall centred on the chord's own
         * midpoint, with a hole 324 m wide and 222 m tall in the middle of it.
         */
        private val holedZone = SpeedZone(
            "z", "Cap", 5.0,
            ring(800.0, OUTER_HALF_X_M, 445.0),
            listOf(ring(800.0, HOLE_HALF_X_M, HOLE_HALF_Y_M))
        )

        /** A closed rectangle in the chord's own metric frame, centred [centreXM] metres east. */
        private fun ring(centreXM: Double, halfXM: Double, halfYM: Double): List<LatLng> = listOf(
            LatLng(south(halfYM), east(centreXM - halfXM)),
            LatLng(south(halfYM), east(centreXM + halfXM)),
            LatLng(north(halfYM), east(centreXM + halfXM)),
            LatLng(north(halfYM), east(centreXM - halfXM)),
            LatLng(south(halfYM), east(centreXM - halfXM))
        )
    }
}
