package ykws.android.maro.spatial.multipass

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
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
     * The walk's own context for the price fixtures: the fine step unless the fixture names another,
     * and the two raw ends stand for the chord's own origin — the price walk reads neither them nor
     * the approaches, so those values are the fixture's own bookkeeping, never the walk's.
     */
    private fun walkCtx(
        priceStepM: Double = fineStepM,
        field: RouteCostField,
        timing: PullTiming? = null,
        memo: MarkMemo? = null
    ): PullContext = PullContext(
        marginM, coarseStepM, priceStepM, field,
        LatLng(CHORD_LAT, east(0.0)), LatLng(CHORD_LAT, east(0.0)),
        timing = timing, memo = memo
    )

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
            start, aim, walkCtx(field = flatField { fineReads++ })
        )
        val coarse = MultipassPull.softPriceSec(
            start, aim, walkCtx(priceStepM = coarseStepM, field = flatField { coarseReads++ })
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

        val fine = MultipassPull.softPricePrefix(path, walkCtx(field = flatField {}))
        val coarse = MultipassPull.softPricePrefix(path, walkCtx(priceStepM = coarseStepM, field = flatField {}))

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

        val fineSum = MultipassPull.softPriceSec(start, aim, walkCtx(field = bandEdgeField { fineReads++ }))
        val coarseSum =
            MultipassPull.softPriceSec(start, aim, walkCtx(priceStepM = coarseStepM, field = bandEdgeField { coarseReads++ }))

        assertEquals("the band's edge keeps today's sum", fineSum, coarseSum, 0.0)
        assertEquals("and every fine midpoint is still read", fineReads, coarseReads)
        assertTrue("the walk is not vacuous: the collar is priced", coarseSum > 0.0)

        val fineLine = MultipassPull.pull(
            PullSetup(marginM, fineStepM, fineStepM, bandEdgeField {}, start, aim), path
        )
        val coarseLine = MultipassPull.pull(
            PullSetup(marginM, coarseStepM, coarseStepM, bandEdgeField {}, start, aim), path
        )
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

        val pulled = MultipassPull.pull(
            PullSetup(marginM, coarseStepM, coarseStepM, field, start, aim), path
        )

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

        val fine = MultipassPull.softPriceSec(start, aim, walkCtx(field = holeField { fineReads++ }))
        val coarse = MultipassPull.softPriceSec(start, aim, walkCtx(priceStepM = coarseStepM, field = holeField { coarseReads++ }))

        assertEquals("the hole's edge keeps today's sum exactly", fine, coarse, 0.0)
        val steps = ceil(SpatialOperations.haversine(start, aim) / fineStepM).toInt()
        val perGroup = (coarseStepM / fineStepM).toInt()
        val groups = (steps + perGroup - 1) / perGroup
        assertTrue("groups away from the edges are proved", coarseReads < fineReads)
        assertTrue("while the groups across the hole keep their own reads", coarseReads > groups)

        val blind = MultipassPull.softPriceSec(
            start, aim, walkCtx(priceStepM = coarseStepM, field = holeField(blindToHoles = true) {})
        )
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

        MultipassPull.softPriceSec(start, aim, walkCtx(priceStepM = coarseStepM, field = field))

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

    /**
     * **The span proof's own shape: every read but one.** A constant price whose boundary is declared
     * far away lets the recursion prove the **whole chord** from one clearance read at its midpoint, so
     * the coarsened walk returns today's own double and pays a single price read where the fine walk
     * reads every midpoint — the group walk's own saving, taken at the whole chord's scale.
     */
    @Test
    fun aProvedSpanSavesEveryReadButOne() {
        val start = LatLng(CHORD_LAT, 7.00)
        val aim = LatLng(CHORD_LAT + 1595.0 / M_PER_DEG_LAT, 7.00)
        var fineReads = 0
        var coarseReads = 0

        val fine = MultipassPull.softPriceSec(start, aim, walkCtx(field = flatField { fineReads++ }))
        val coarse = MultipassPull.softPriceSec(start, aim, walkCtx(priceStepM = coarseStepM, field = flatField { coarseReads++ }))

        assertEquals("the span proof returns today's own double, not a near miss", fine, coarse, 0.0)
        assertTrue("the fine walk is not vacuous", fineReads > 0)
        assertEquals("a proved span pays one price read whatever its length", 1, coarseReads)
        assertEquals("so the saving is every read but one", fineReads - 1, fineReads - coarseReads)
    }

    /**
     * **A proved span reads exactly twice and prices zero.** Open water whose arm is 0 but whose source
     * still declares a boundary far away: the recursion proves the whole chord, so the walk pays one
     * clearance read and one price read at the midpoint, and its sum is exactly 0.
     */
    @Test
    fun aProvedSpanReadsExactlyTwiceAndPricesZero() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        var reads = 0
        val field = RouteCostField(
            listOf(
                RouteCostSource.Soft(
                    priceSec = { reads++; 0.0 },
                    tag = MultipassCellState.ZONE,
                    clearanceAt = { reads++; CLEAR_OF_ANY_BOUNDARY_M }
                )
            )
        )

        val sum = MultipassPull.softPriceSec(start, aim, walkCtx(priceStepM = coarseStepM, field = field))

        assertEquals("the proved span's sum is exactly 0", 0.0, sum, 0.0)
        assertEquals("and it reads exactly twice: one proof, one price", 2, reads)
    }

    /**
     * **The floor holds.** A chord at or under twice the price step never reaches the span proof: it
     * takes the group path, so a short chord's counts are today's — one prove and one price per proved
     * group, and never a single whole-chord read.
     */
    @Test
    fun aChordAtTheFloorTakesTheGroupPathAndPaysNoExtraRead() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val aim = LatLng(CHORD_LAT, east(150.0))
        var fineReads = 0
        var coarseReads = 0

        val fine = MultipassPull.softPriceSec(start, aim, walkCtx(field = countingField { fineReads++ }))
        val coarse = MultipassPull.softPriceSec(start, aim, walkCtx(priceStepM = coarseStepM, field = countingField { coarseReads++ }))

        val steps = ceil(SpatialOperations.haversine(start, aim) / fineStepM).toInt()
        val k = floor(coarseStepM / fineStepM).toInt()
        val groups = (steps + k - 1) / k
        assertTrue(
            "the fixture is at or under the floor",
            steps <= floor(2.0 * coarseStepM / fineStepM).toInt()
        )
        assertEquals("the group path returns today's own double", fine, coarse, 0.0)
        assertEquals("and pays the group walk's own reads, never one whole-chord read", groups * 2, coarseReads)
    }

    /**
     * **The boundary is a boundary.** A chord whose midpoint declaration stands one metre under half
     * the chord's length is **split** rather than proved — so the walk reads each half's midpoint, not
     * the whole chord's alone, and still returns today's own double.
     */
    @Test
    fun aSpanJustUnderHalfItsLengthIsSplitNotProved() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        var reads = 0
        // The chord's midpoint declares a boundary 700 m away — under its 800 m half-length — while a
        // half's midpoint, 400 m along the chord, stands ~806 m from that same boundary, well clear of
        // its 400 m half-length. So the top span is refused and its two halves prove.
        val boundary = LatLng(north(700.0), east(800.0))
        val field = RouteCostField(
            listOf(
                RouteCostSource.Soft(
                    priceSec = { reads++; fullSec },
                    tag = MultipassCellState.ZONE,
                    clearanceAt = { p -> reads++; SpatialOperations.haversine(p, boundary) }
                )
            )
        )

        val sum = MultipassPull.softPriceSec(start, aim, walkCtx(priceStepM = coarseStepM, field = field))
        val fine = MultipassPull.softPriceSec(start, aim, walkCtx(field = flatField {}))

        assertEquals("the split still returns today's own double", fine, sum, 0.0)
        assertEquals(
            "the whole chord was not priced from one read: its two halves were, each proof and price",
            5, reads
        )
    }

    /**
     * **The seam the Phase 4 collapse hid.** The phase's whole suite was green while the engine handed
     * the pull the *fine* cell, because every fixture threaded its own step and the runner's own choice
     * was never read. The step is now a named pure function of the walk, so this pins what the runner
     * hands the pull on the `evolutive` pair: the walk's **interior** cell, never the band's fine one.
     */
    @Test
    fun theRunnerHandsThePriceWalkTheInteriorCellNeverTheFineOne() {
        val walk = twoLayerWalk()
        val runner = RoutePassRunner()

        assertEquals(
            "the price step is the walk's interior cell — 100 m, the interior's own",
            100.0, runner.priceStepFor(walk), 0.0
        )
        val fine = walk.windows!!.cellSizeM(1)
        assertEquals("while the band's local cell is the fine 20 m", 20.0, fine, 0.0)
        assertTrue("so a collapse to the fine cell would be visible", runner.priceStepFor(walk) != fine)

        val single = GridWalk(walk.grid, CellIndex(0, 0), CellIndex(1, 1), 100.0)
        assertEquals("a single-grid walk answers its own cell", 100.0, runner.priceStepFor(single), 0.0)
    }

    /**
     * **The two-layer fixture the collapse cannot return through.** On the same `evolutive` pair the
     * interior step groups and spares reads, while the band's local step collapses the quotient to one
     * interval a group and reads every midpoint — the defect, and its fix, on one fixture.
     */
    @Test
    fun aTwoLayerWalkGroupsItsPriceWhereTheFineStepCouldNot() {
        val walk = twoLayerWalk()
        val interiorStep = RoutePassRunner().priceStepFor(walk)
        val localStep = walk.windows!!.cellSizeM(1)

        val start = LatLng(CHORD_LAT, 7.00)
        val aim = LatLng(CHORD_LAT + 1595.0 / M_PER_DEG_LAT, 7.00)
        var interiorReads = 0
        var localReads = 0

        val interiorSum = MultipassPull.softPriceSec(start, aim, walkCtx(priceStepM = interiorStep, field = flatField { interiorReads++ }))
        val localSum = MultipassPull.softPriceSec(start, aim, walkCtx(priceStepM = localStep, field = flatField { localReads++ }))

        assertEquals("the two steps price the same water identically", interiorSum, localSum, 0.0)
        assertTrue("the interior step groups and spares reads", interiorReads < localReads)
        assertTrue("while the fine local step reads every midpoint, exactly as before", localReads > 0)
    }

    /**
     * **The ring collar's own walk-level chord** — the test Phase 4's review left owed: the collar had a
     * declaration test but no chord. A chord running through the collar keeps today's fine reads (its
     * declaration never reaches a group's half-length), while a chord deep inside the ring proves its
     * groups from the ring's own distance.
     */
    @Test
    fun aRingCollarChordKeepsItsFineReadsAndADeepRingChordGroups() {
        // The outer ring spans east(150)..east(1450); its south edge is at south(445), so this chord sits
        // 20 m inside the 100 m collar.
        val collarStart = LatLng(south(465.0), east(200.0))
        val collarAim = LatLng(south(465.0), east(1400.0))
        var collarFine = 0
        var collarCoarse = 0
        val collarFineSum =
            MultipassPull.softPriceSec(collarStart, collarAim, walkCtx(field = holeField { collarFine++ }))
        val collarCoarseSum =
            MultipassPull.softPriceSec(collarStart, collarAim, walkCtx(priceStepM = coarseStepM, field = holeField { collarCoarse++ }))
        assertEquals("the collar keeps today's sum exactly", collarFineSum, collarCoarseSum, 0.0)
        assertEquals("and every fine midpoint is still read", collarFine, collarCoarse)
        assertTrue("the collar's water is priced, so the walk is not vacuous", collarFineSum > 0.0)

        // 200 m north of the chord: inside the ring, 89 m from the hole's own edge and 245 m from the
        // outer ring, so the declaration clears a group's 50 m half-length and the groups are proved.
        val deepStart = LatLng(north(200.0), east(400.0))
        val deepAim = LatLng(north(200.0), east(1200.0))
        var deepFine = 0
        var deepCoarse = 0
        MultipassPull.softPriceSec(deepStart, deepAim, walkCtx(field = holeField { deepFine++ }))
        MultipassPull.softPriceSec(deepStart, deepAim, walkCtx(priceStepM = coarseStepM, field = holeField { deepCoarse++ }))
        assertTrue("deep inside the ring the declaration proves groups", deepCoarse < deepFine)
        assertTrue("and the ring water is priced", deepFine > 0)
    }

    /**
     * **The shipped `costField` declaration closure** — the second test Phase 4's review left owed: every
     * fixture hand-rolls its own declaration, so the one that ships was the one the suite never asked.
     * This builds the field through `costField` and reads its declaration, exercising both arms — the
     * band's two circles and the ring's own edge.
     */
    @Test
    fun theShippedCostFieldClosureDeclaresTheBandsCirclesAndTheRings() {
        setSpeedZoneSwitch(true)
        val world = BandWorld(bandM = bandWidthM, coastLat = CHORD_LAT, zones = listOf(holedZone))
        val field = costField(
            world, cellM = 50.0, pace = paceKn, withZones = true, withBand = true,
            zones = listOf(holedZone), lambda = lambda
        )

        // 310 m off the coast: inside the band's 300 m circle by 10 m, and far from the ring.
        val inBand = LatLng(CHORD_LAT + 310.0 / M_PER_DEG_LAT, east(0.0))
        assertEquals(
            "the band's own circle at 300 m is named",
            min(abs(310.0 - bandWidthM), abs(310.0 - bandReachM(bandWidthM, bandOutsideMarginM))),
            field.priceClearanceM(inBand),
            1e-6
        )
        assertTrue("and the closure's band arm prices that point", field.softPriceSecAt(inBand) > 0.0)

        // On the ring's own south edge: the closure's zone arm names it, at distance 0.
        val onRingEdge = LatLng(south(445.0), east(800.0))
        assertEquals(
            "the ring's own edge is named too",
            0.0,
            field.priceClearanceM(onRingEdge),
            1e-6
        )
    }

    /**
     * **The moved sample the phase is for.** The walk's marks stand on a fixed lattice from the chord's
     * own anchor — `anchor + (j + 0.5) × step`, the last interval sized to the chord's remainder — so the
     * mark set is fixed by the step alone and no longer carries the chord's length into every point. The
     * fixture collects the points the walk reads and asserts each mark stands at its own whole-step
     * offset from the anchor, and that the interval widths sum to the chord exactly.
     */
    @Test
    fun theWalkPlacesItsMarksOnTheFixedLattice() {
        val anchor = LatLng(CHORD_LAT, east(0.0))
        val aim = LatLng(CHORD_LAT, east(1595.0))
        val marks = ArrayList<LatLng>()

        MultipassPull.softPriceSec(anchor, aim, walkCtx(field = boundaryEverywhereField { p -> marks.add(p) }))

        val dist = SpatialOperations.haversine(anchor, aim)
        val step = MultipassPull.clearanceStep(marginM)
        assertTrue("the fixture reads a substantial run of marks", marks.size > 100)
        assertEquals("one mark per whole step, plus the remainder's own", ceil(dist / step).toInt(), marks.size)
        for (j in 0 until marks.size - 1) {
            assertEquals(
                "mark $j stands at (j + 0.5) steps from the anchor",
                (j + 0.5) * step,
                SpatialOperations.haversine(anchor, marks[j]),
                1e-3
            )
        }
        assertTrue(
            "and the last mark sits inside the chord, on the remainder, never a step past it",
            SpatialOperations.haversine(anchor, marks.last()) < dist
        )
        val lattice = MarkLattice(dist, step)
        assertEquals(
            "the intervals' lengths sum to the chord's own haversine", dist,
            (0 until lattice.count).sumOf { lattice.widthM(it) }, 1e-9
        )
    }

    /**
     * **The lattice's error, named.** A price arm that changes across a moved sample — here a step from
     * full price to zero at one point — is the plan's worst case, and the number the user's word rests
     * on. The lattice reads each interval at its midpoint, so the sum errs by the length the boundary
     * stands from the nearest interval edge times the price it switches across: **at most one interval's
     * price**, `price × stepM`, and it is zero exactly where the boundary lands on an edge.
     */
    @Test
    fun aPriceBoundaryAcrossAMovedSampleErrOrsByAtMostOneInterval() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        val boundaryM = 1000.7
        val step = MultipassPull.clearanceStep(marginM)
        val field = RouteCostField(
            listOf(
                RouteCostSource.Soft(
                    priceSec = { p -> if (eastMetres(p) < boundaryM) fullSec else 0.0 },
                    tag = MultipassCellState.ZONE,
                    clearanceAt = { 0.0 }
                )
            )
        )

        val exact = fullSec * boundaryM
        val sum = MultipassPull.softPriceSec(start, aim, walkCtx(field = field))

        assertTrue("the lattice's error never exceeds one interval's price", abs(sum - exact) <= fullSec * step)
        assertTrue("and the boundary moved a sample, so the error is not trivially zero", sum != exact)
    }

    /**
     * **The memo reads a mark once and answers the same double.** The fixture the plan names: the same
     * point is read twice through one [MarkMemo], the two doubles are asserted **identical**, and the
     * field's own counter rises **once** — the second lookup is answered by the memo, never by the field.
     */
    @Test
    fun aMemoReadsOneMarkOnceAndAnswersTheSameDouble() {
        val p = LatLng(CHORD_LAT, east(400.0))
        var reads = 0
        val field = flatField { reads++ }
        val memo = MarkMemo()

        val first = memo.price(field, p)
        val second = memo.price(field, p)

        assertEquals("the memo answers the identical double on the repeat", first, second, 0.0)
        assertEquals("and the one mark was read once for the two lookups", 1, reads)
    }

    /**
     * **The reads fall across the attempts of one anchor, and the sum does not move.** The value half
     * is pinned exactly: a walk repeated over the same chord answers the identical double on the hit.
     * The read half is the pull's own two attempts from one anchor — the second stretch answered by the
     * memo the first already paid — so the same line is drawn from strictly fewer price reads.
     */
    @Test
    fun theMemoLowersTheReadsAcrossTheAttemptsOfOneAnchor() {
        val start = LatLng(CHORD_LAT, 7.00)
        val detour = LatLng(CHORD_LAT + 0.003, 7.01)
        val aim = LatLng(CHORD_LAT, 7.02)
        val path = listOf(start, detour, aim)

        // The value half: one chord walked twice through one memo, against the memo-less walk's double.
        val memo = MarkMemo()
        val field = pricedCornerField {}
        val first = MultipassPull.softPriceSec(start, aim, walkCtx(field = field, memo = memo))
        val second = MultipassPull.softPriceSec(start, aim, walkCtx(field = field, memo = memo))
        val plain = MultipassPull.softPriceSec(start, aim, walkCtx(field = pricedCornerField {}))
        assertEquals("a memo hit answers the identical double", first, second, 0.0)
        assertEquals("and the memo changes no sum", plain, second, 0.0)

        // The read half: the pull's two attempts from the anchor, the re-walk answered by the first's memo.
        var memoReads = 0
        var plainReads = 0
        val pulled = MultipassPull.pull(
            PullSetup(marginM, fineStepM, fineStepM, pricedCornerField { memoReads++ }, start, aim), path
        )
        val plainPulled = MultipassPull.pull(
            PullSetup(marginM, fineStepM, fineStepM, pricedCornerField { plainReads++ }, start, aim),
            path, memo = null
        )
        assertEquals("the memo returns the line today's walk draws", plainPulled, pulled)
        assertTrue("the memo walk reads the price strictly less", memoReads < plainReads)
        assertTrue("and the walk is not vacuous: the price was read", plainReads > 0)
        // The plan's own 410 → 270, asserted on the fixture's numbers rather than left in prose: a change
        // that moves either count must answer to these two lines.
        assertEquals("the memo walk's price reads, exactly as the plan records", 270, memoReads)
        assertEquals("against the memo-less walk's, exactly as the plan records", 410, plainReads)
    }

    /**
     * **The overlap the triangle never produced: two attempts sharing a stretch.** The path is straight,
     * so the fixed lattice makes the attempts' marks sit on shared points — the plain walk pays a repeat
     * for every mark a later attempt retraces, while the memo answers each from the first read. The field
     * is therefore read **once per distinct mark**, and the memo run visits exactly the point set the
     * memo-less run does, with the repeats removed.
     */
    @Test
    fun twoAttemptsThatShareAStretchReadEachDistinctMarkOnce() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val mid = LatLng(CHORD_LAT, east(800.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        val path = listOf(start, mid, aim)

        val memoPoints = ArrayList<LatLng>()
        MultipassPull.pull(
            PullSetup(marginM, fineStepM, fineStepM, recordingField { memoPoints.add(it) }, start, aim),
            path, memo = MarkMemo()
        )
        val plainPoints = ArrayList<LatLng>()
        MultipassPull.pull(
            PullSetup(marginM, fineStepM, fineStepM, recordingField { plainPoints.add(it) }, start, aim),
            path, memo = null
        )

        assertTrue("the fixture reads a substantial run of marks", memoPoints.isNotEmpty())
        assertEquals(
            "the memo reads each distinct shared mark exactly once, never asking twice",
            memoPoints.size, memoPoints.distinct().size
        )
        assertEquals(
            "and it visits exactly the marks the memo-less walk reads, each once",
            plainPoints.distinct(), memoPoints
        )
        assertTrue(
            "the two attempts genuinely share their stretch, so the plain walk pays the repeat",
            plainPoints.size > memoPoints.size
        )
    }

    /**
     * **An unproved stretch reads at the lattice's own positions, memo or not.** The chord runs along the
     * band's collar, so no span and no group is ever proved and every fine midpoint is read. The points
     * recorded with a memo and without it are the **same**, and they are the landed lattice's own
     * midpoints in order — pinning the clause's "today's positions, in today's order" rather than letting
     * the count and the verdict stand in for it.
     */
    @Test
    fun anUnprovedStretchReadsTodaysPositionsWithAndWithoutTheMemo() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        val lattice = MarkLattice(SpatialOperations.haversine(start, aim), MultipassPull.clearanceStep(marginM))

        val withMemo = ArrayList<LatLng>()
        MultipassPull.softPriceSec(
            start, aim, walkCtx(priceStepM = coarseStepM, field = recordingBandEdgeField { withMemo.add(it) }, memo = MarkMemo())
        )
        val withoutMemo = ArrayList<LatLng>()
        MultipassPull.softPriceSec(
            start, aim, walkCtx(priceStepM = coarseStepM, field = recordingBandEdgeField { withoutMemo.add(it) })
        )

        assertTrue("the unproved stretch reads every fine midpoint", withMemo.size > 100)
        assertEquals("the memo answers the same marks at the same positions", withoutMemo, withMemo)
        assertEquals(
            "and they are the lattice's own midpoints, in order",
            (0 until lattice.count).map { chordAt(start, aim, lattice.markT(it)) }, withMemo
        )
    }

    /**
     * **A changed field wipes the memo rather than answering stale.** The memo is bound to the field it
     * fills under: the same point read under a second field re-reads under that field's own law, and a
     * return to the first re-reads it too — so no number can cross a rebuild even if a memo outlived its
     * walk.
     */
    @Test
    fun theMemoWipesItsCacheWhenTheFieldChanges() {
        val p = LatLng(CHORD_LAT, east(400.0))
        var firstReads = 0
        var secondReads = 0
        val first = flatField { firstReads++ }
        val second = RouteCostField(
            listOf(
                RouteCostSource.Soft(
                    priceSec = { secondReads++; 2.0 * fullSec },
                    tag = MultipassCellState.ZONE,
                    clearanceAt = { CLEAR_OF_ANY_BOUNDARY_M }
                )
            )
        )
        val memo = MarkMemo()

        assertEquals("the first field's own price", fullSec, memo.price(first, p), 0.0)
        assertEquals(
            "a second field is read under its own law, never answered from the first's cache",
            2.0 * fullSec, memo.price(second, p), 0.0
        )
        assertEquals("the first field was read once", 1, firstReads)
        assertEquals("and the second field once, not served from the first's entry", 1, secondReads)
        assertEquals("a return to the first re-reads it rather than answering stale", fullSec, memo.price(first, p), 0.0)
        assertEquals("with the first field's own counter risen again", 2, firstReads)
    }

    /** The metres east of the chord's origin that [p] stands — the step-arm fixture's own abscissa. */
    private fun eastMetres(p: LatLng): Double = (p.longitude - 7.00) * M_PER_DEG_LON

    @After
    fun restoreSpeedZoneSwitch() {
        setSpeedZoneSwitch(false)
    }

    private fun setSpeedZoneSwitch(value: Boolean) {
        val field = AppConfig::class.java.getDeclaredField("routeAvoidSpeedZoneEnabled")
        field.isAccessible = true
        field.setBoolean(AppConfig, value)
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
     * The [`thePullRefusesAChordDearerThanThePathItReplaces`] corner, counting its price reads: a band
     * priced [CORNER_PRICE_SEC] seconds on the low side, its declaration naming no boundary, so every
     * fine mark is read and a memo has marks to answer.
     */
    private fun pricedCornerField(counting: () -> Unit): RouteCostField = RouteCostField(
        listOf(
            RouteCostSource.Soft(
                priceSec = { p -> counting(); if (p.latitude < CHORD_LAT + 0.0015) CORNER_PRICE_SEC else 0.0 },
                tag = MultipassCellState.BAND
            )
        )
    )

    /**
     * A constant price counting **both** the reads a proved group pays: its declaration
     * ([RouteCostSource.Soft.priceClearanceM]) and its price ([RouteCostField.softPriceSecAt]), so a
     * proved group's `k − 2` saving is read net of what proving it costs.
     */
    private fun countingField(counting: () -> Unit): RouteCostField = RouteCostField(
        listOf(
            RouteCostSource.Soft(
                priceSec = { counting(); fullSec },
                tag = MultipassCellState.ZONE,
                clearanceAt = { counting(); CLEAR_OF_ANY_BOUNDARY_M }
            )
        )
    )

    /**
     * A constant price whose source declares a boundary **everywhere**, so no group and no span is ever
     * proved and the walk reads every fine midpoint — the shape the moved-sample fixture collects.
     */
    private fun boundaryEverywhereField(onRead: (LatLng) -> Unit): RouteCostField = RouteCostField(
        listOf(
            RouteCostSource.Soft(
                priceSec = { p -> onRead(p); fullSec },
                tag = MultipassCellState.ZONE,
                clearanceAt = { 0.0 }
            )
        )
    )

    /** The `evolutive` pair: an interior grid at 100 m and a band window at 20 m, one two-layer walk. */
    private fun twoLayerWalk(): GridWalk {
        val family = LatticeFamily.of(BBox(43.45, 43.55, 6.95, 7.05), coarseCellM = 100.0, fineCellM = 20.0)
        val coarseGrid = MultipassGrid(
            family.coarse.latSouth, family.coarse.lonWest,
            family.coarse.cellSizeDegLat, family.coarse.cellSizeDegLon,
            2, 2, family.coarse.cellM, baseCostSec(family.coarse.cellM, paceKn)
        )
        val fineGrid = MultipassGrid(
            family.fine.latSouth, family.fine.lonWest,
            family.fine.cellSizeDegLat, family.fine.cellSizeDegLon,
            10, 10, family.fine.cellM, baseCostSec(family.fine.cellM, paceKn)
        )
        val windows = WalkWindows.onLattice(
            family.layers,
            listOf(WalkWindow(coarseGrid, 0, 0, layer = 0), WalkWindow(fineGrid, 0, 0, layer = 1))
        )
        return GridWalk(coarseGrid, CellIndex(0, 0), CellIndex(1, 1), family.coarse.cellM, windows)
    }

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

    /** The band-edge field of [bandEdgeField], recording the point each price read lands on. */
    private fun recordingBandEdgeField(onRead: (LatLng) -> Unit): RouteCostField = RouteCostField(
        listOf(
            RouteCostSource.Soft(
                priceSec = { p ->
                    onRead(p)
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

    /**
     * A flat priced field that records the point each price read lands on — the sharing fixture's own,
     * carrying [flatField]'s far-away declaration so every mark is read.
     */
    private fun recordingField(onRead: (LatLng) -> Unit): RouteCostField = RouteCostField(
        listOf(
            RouteCostSource.Soft(
                priceSec = { p -> onRead(p); fullSec },
                tag = MultipassCellState.ZONE,
                clearanceAt = { CLEAR_OF_ANY_BOUNDARY_M }
            )
        )
    )

    /** The point at fraction [t] of the chord [a]→[b] — the walk's own interpolation, mirrored for the pin. */
    private fun chordAt(a: LatLng, b: LatLng, t: Double): LatLng = LatLng(
        a.latitude + (b.latitude - a.latitude) * t,
        a.longitude + (b.longitude - a.longitude) * t
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

    /** A world with one straight coast parallel to the chord — the shipped closure's own input. */
    private class BandWorld(
        private val bandM: Double,
        private val coastLat: Double,
        private val zones: List<SpeedZone> = emptyList()
    ) : MultipassWorld {
        private val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0

        override val coastlineReady = true
        override val depthReady = false
        override val bandWidthM = bandM
        override val regionBounds: BBox? = null

        override fun segmentsIn(box: BBox): List<MultipassEdge> = emptyList()
        override fun openCoastIn(box: BBox): List<List<LatLng>> = emptyList()
        override fun isWater(latitude: Double, longitude: Double) = true
        override fun distanceToCoastM(latitude: Double, longitude: Double): Double =
            abs(latitude - coastLat) * mPerDegLat
        override fun depthAt(latitude: Double, longitude: Double) = DepthSample.NONE
        override fun speedZonesIn(box: BBox): List<SpeedZone> = zones
        override fun zoneLimitKnAt(latitude: Double, longitude: Double): Double? =
            strictestLimitKnAt(zones, emptySet(), latitude, longitude)
    }

    private companion object {

        /** The chord's own parallel, so every fixture offsets in metres without a projection. */
        private const val CHORD_LAT = 43.50

        /** A declared boundary this far away is outside every group the fixture walks. */
        private const val CLEAR_OF_ANY_BOUNDARY_M = 10_000.0

        /** The priced corner's own price, in seconds — the band the dearer chord crosses. */
        private const val CORNER_PRICE_SEC = 120.0

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
